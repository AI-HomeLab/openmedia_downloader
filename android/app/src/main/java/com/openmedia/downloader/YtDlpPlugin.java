package com.openmedia.downloader;

import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * 自訂 YtDlp Plugin 的 Android 原生側。TS 介面（apps/web/src/lib/ytdlp.ts）是
 * source of truth；方法名/事件名/錯誤碼三邊一致（見 rules §5）。
 * 長任務交給 DownloadService（foreground），這裡只做 bridge：存 call、轉事件。
 */
@CapacitorPlugin(name = "YtDlp")
public class YtDlpPlugin extends Plugin {
    private final Handler main = new Handler(Looper.getMainLooper());
    private String currentCallbackId;
    private String currentState = "idle";

    @Override
    public void load() {
        DownloadService.setListener(new DownloadService.Listener() {
            @Override
            public void onProgress(float percent, long etaSeconds) {
                JSObject data = new JSObject();
                data.put("percent", percent);
                data.put("etaSeconds", etaSeconds);
                data.put("line", "");
                main.post(() -> notifyListeners("progress", data));
            }

            @Override
            public void onDone(Uri fileUri, String fileName) {
                currentState = "done";
                finishWith((call) -> {
                    JSObject data = new JSObject();
                    data.put("fileUri", fileUri.toString());
                    data.put("fileName", fileName);
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
        // 02 只有 video/bestaudio 兩檔；畫質清單是 03 的事。
        String kind = call.getString("kind", "video");
        String format = "audio".equals(kind) ? "bestaudio" : call.getString("format", "best");
        if (!DownloadService.startDownload(getContext(), url, format)) {
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

    /** 結構化 resolve 移至 03；現在呼叫一律明確拒絕。 */
    @PluginMethod
    public void resolve(PluginCall call) {
        call.reject("resolve 移至 03 實作", DownloadError.UNKNOWN.name());
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
