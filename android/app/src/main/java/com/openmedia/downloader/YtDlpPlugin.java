package com.openmedia.downloader;

import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 自訂 YtDlp Plugin 的 Android 原生側。TS 介面（apps/web/src/lib/ytdlp.ts）是
 * source of truth；方法名/事件名/錯誤碼三邊一致（見 rules §5）。
 * 長任務交給 DownloadService（foreground），這裡只做 bridge：存 call、轉事件。
 */
@CapacitorPlugin(name = "YtDlp")
public class YtDlpPlugin extends Plugin {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private String currentCallbackId;
    private volatile String currentState = "idle";
    /** 上次 resolve 的完整結果；download(formatIndex) 吃它的 index，URL 對不上就拒收。 */
    private volatile ResolveResult lastResolve;
    private volatile String lastResolveUrl;

    @Override
    public void load() {
        DownloadService.setListener(new DownloadService.Listener() {
            @Override
            public void onProgress(float percent, long etaSeconds, long speedBps) {
                JSObject data = new JSObject();
                data.put("percent", percent);
                data.put("etaSeconds", etaSeconds);
                data.put("speedBps", speedBps);
                data.put("line", "");
                main.post(() -> notifyListeners("progress", data));
            }

            @Override
            public void onDone(Uri fileUri, String fileName, boolean merged) {
                currentState = "done";
                finishWith((call) -> {
                    JSObject data = new JSObject();
                    data.put("fileUri", fileUri.toString());
                    data.put("fileName", fileName);
                    data.put("merged", merged);
                    if (!merged) {
                        data.put("code", DownloadError.POSTPROCESS.name());
                    }
                    call.resolve(data);
                });
            }

            @Override
            public void onError(DownloadError code, String message) {
                if (code == DownloadError.CANCELLED) {
                    currentState = "cancelled";
                } else {
                    currentState = "error";
                }
                final String msg = message == null ? code.name() : message;
                finishWith((call) -> call.reject(msg, code.name()));
            }
        });
    }

    @PluginMethod
    public void download(PluginCall call) {
        String url = call.getString("url");
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            call.reject("INVALID_URL", DownloadError.UNKNOWN.name());
            return;
        }
        // 02 只有 video/bestaudio 兩檔；03 起吃 formatIndex（resolve 清單的 index）。
        // 音檔走 bestaudio/best（無純音軌時退回單檔，04 再轉 mp3）。
        String kind = call.getString("kind", "video");
        boolean isAudio = "audio".equals(kind);
        String format = isAudio ? "bestaudio/best" : call.getString("format", "best");
        int formatIndex = isAudio ? -1 : call.getInt("formatIndex", -1);
        if (formatIndex >= 0) {
            String picked = pickFormat(url, formatIndex);
            if (picked == null) {
                call.reject("請重新解析後再選畫質", DownloadError.UNKNOWN.name());
                return;
            }
            format = picked;
        }
        if (!DownloadService.startDownload(getContext(), url, format, kind)) {
            call.reject("BUSY", DownloadError.UNKNOWN.name());
            return;
        }
        currentState = "downloading";
        currentCallbackId = call.getCallbackId();
        getBridge().saveCall(call);
    }

    /** 冪等：閒置時呼叫是 no-op。 */
    @PluginMethod
    public void cancel(PluginCall call) {
        DownloadService.cancel();
        call.resolve();
    }

    @PluginMethod
    public void getStatus(PluginCall call) {
        JSObject data = new JSObject();
        data.put("state", DownloadService.isRunning() ? "downloading" : currentState);
        call.resolve(data);
    }

    /** 結構化 resolve：回傳實際可用畫質清單（index/label/size），結果快取給 download 用。 */
    @PluginMethod
    public void resolve(PluginCall call) {
        String url = call.getString("url");
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            call.reject("INVALID_URL", DownloadError.UNKNOWN.name());
            return;
        }
        currentState = "resolving";
        final String callbackId = call.getCallbackId();
        final boolean requireVideo = !"audio".equals(call.getString("kind", "video"));
        getBridge().saveCall(call);
        executor.submit(() -> {
            try {
                ResolveResult result = ResolveEngine.resolve(getContext(), url, requireVideo);
                lastResolve = result;
                lastResolveUrl = url;
                List<VideoFormat> options = result.videoOptions();
                JSArray arr = new JSArray();
                for (int i = 0; i < options.size(); i++) {
                    VideoFormat f = options.get(i);
                    JSObject o = new JSObject();
                    o.put("index", i);
                    o.put("label", f.displayLabel());
                    o.put("sizeBytes", f.filesize);
                    o.put("hasAudio", f.hasAudio());
                    arr.put(o);
                }
                JSObject data = new JSObject();
                data.put("title", result.title);
                data.put("durationSec", result.durationSec);
                data.put("options", arr);
                finishSaved(callbackId, (saved) -> saved.resolve(data));
            } catch (ResolveException e) {
                currentState = "error";
                finishSaved(callbackId,
                        (saved) -> saved.reject(e.getMessage(), e.getCode().name()));
            } catch (Throwable t) {
                // 絕不讓 saved call 懸空：任何非預期錯誤都收斂為 UNKNOWN。
                android.util.Log.e("YtDlpPlugin", "resolve 意外失敗", t);
                currentState = "error";
                finishSaved(callbackId, (saved) -> saved.reject(
                        "解析失敗", DownloadError.UNKNOWN.name()));
            }
        });
    }

    /** 從快取的 resolve 結果拿 formatId；URL 對不上或越界就拒收（防錯片）。 */
    private String pickFormat(String url, int index) {
        ResolveResult cached = lastResolve;
        if (cached == null || !url.equals(lastResolveUrl)) {
            return null;
        }
        List<VideoFormat> options = cached.videoOptions();
        if (index < 0 || index >= options.size()) {
            return null;
        }
        return options.get(index).formatId;
    }

    @PluginMethod
    public void openFile(PluginCall call) {
        String uriString = call.getString("uri");
        if (uriString == null) {
            call.reject("missing uri", DownloadError.UNKNOWN.name());
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(Uri.parse(uriString), MimeGuesser.guess(uriString))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
            call.resolve();
        } catch (Exception e) {
            call.reject("開啟失敗", DownloadError.UNKNOWN.name());
        }
    }

    private void finishWith(Finisher finisher) {
        final String id = currentCallbackId;
        currentCallbackId = null;
        finishSaved(id, finisher);
    }

    private void finishSaved(final String id, Finisher finisher) {
        main.post(() -> {
            if (id == null) {
                return;
            }
            PluginCall saved = getBridge().getSavedCall(id);
            if (saved == null) {
                return;
            }
            finisher.finish(saved);
            getBridge().releaseCall(saved);
        });
    }

    private interface Finisher {
        void finish(PluginCall call);
    }

    /** MediaStore content Uri 沒有副檔名可用 extension 猜，video/audio 先分流。 */
    static class MimeGuesser {
        static String guess(String uri) {
            // 先切掉 query/fragment，免得 ?download=1.mp3 之類誤判
            String path = uri.split("[?#]", 2)[0].toLowerCase();
            if (path.endsWith(".mp3")) {
                return "audio/mpeg";
            }
            if (path.endsWith(".m4a")) {
                return "audio/mp4";
            }
            if (path.endsWith(".webm")) {
                return "video/webm";
            }
            return "video/mp4";
        }
    }
}
