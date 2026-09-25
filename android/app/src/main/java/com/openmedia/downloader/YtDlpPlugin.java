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
    /** 上次掃描的清單；downloadBatch 吃它做 URL 一致性檢查（防錯單）。 */
    private volatile PlaylistResult lastPlaylist;
    private volatile String lastPlaylistUrl;

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

            @Override
            public void onBatchProgress(int index, int total, String itemTitle,
                                        float percent, long etaSeconds, long speedBps) {
                JSObject data = new JSObject();
                data.put("index", index);
                data.put("total", total);
                data.put("itemTitle", itemTitle);
                data.put("percent", percent);
                data.put("etaSeconds", etaSeconds);
                data.put("speedBps", speedBps);
                data.put("line", "");
                main.post(() -> notifyListeners("batchProgress", data));
            }

            @Override
            public void onBatchDone(int succeeded, int total,
                                    java.util.List<BatchFailure> failed) {
                currentState = "done";
                finishWith((call) -> {
                    JSObject data = new JSObject();
                    data.put("succeeded", succeeded);
                    data.put("total", total);
                    JSArray arr = new JSArray();
                    for (BatchFailure f : failed) {
                        JSObject o = new JSObject();
                        o.put("index", f.index);
                        o.put("videoId", f.videoId);
                        o.put("title", f.title);
                        o.put("code", f.code.name());
                        o.put("message", f.message);
                        arr.put(o);
                    }
                    data.put("failed", arr);
                    call.resolve(data);
                });
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

    /** 清單掃描（ticket 06）：flat entries，不拿格式；結果快取給 downloadBatch。 */
    @PluginMethod
    public void resolvePlaylist(PluginCall call) {
        String url = call.getString("url");
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            call.reject("INVALID_URL", DownloadError.UNKNOWN.name());
            return;
        }
        currentState = "resolving";
        final String callbackId = call.getCallbackId();
        getBridge().saveCall(call);
        executor.submit(() -> {
            try {
                PlaylistResult result = ResolveEngine.resolvePlaylist(getContext(), url);
                lastPlaylist = result;
                lastPlaylistUrl = url;
                JSArray arr = new JSArray();
                long totalSec = 0;
                for (PlaylistItem item : result.items) {
                    JSObject o = new JSObject();
                    o.put("videoId", item.videoId);
                    o.put("title", item.title);
                    o.put("url", item.url);
                    o.put("durationSec", item.durationSec);
                    arr.put(o);
                    if (item.durationSec > 0) {
                        totalSec += item.durationSec;
                    }
                }
                JSObject data = new JSObject();
                data.put("playlistId", result.playlistId);
                data.put("title", result.title);
                data.put("itemCount", result.items.size());
                data.put("totalDurationSec", totalSec);
                data.put("items", arr);
                finishSaved(callbackId, (saved) -> saved.resolve(data));
            } catch (ResolveException e) {
                currentState = "error";
                finishSaved(callbackId,
                        (saved) -> saved.reject(e.getMessage(), e.getCode().name()));
            } catch (Throwable t) {
                android.util.Log.e("YtDlpPlugin", "resolvePlaylist 意外失敗", t);
                currentState = "error";
                finishSaved(callbackId, (saved) -> saved.reject(
                        "解析失敗", DownloadError.UNKNOWN.name()));
            }
        });
    }

    /** Cookie 設定（ticket cookie-login/01）：三站 session 的存取開關。內容絕不 Log。 */
    private volatile CookieStore cookieStore;

    private CookieStore cookies() throws DownloadException {
        if (cookieStore == null) {
            synchronized (this) {
                if (cookieStore == null) {
                    try {
                        cookieStore = new CookieStore(getContext());
                    } catch (Exception e) {
                        throw new DownloadException(
                                DownloadError.STORAGE, "安全儲存不可用", e);
                    }
                }
            }
        }
        return cookieStore;
    }

    private static String requireExtractor(PluginCall call) throws DownloadException {
        String extractor = call.getString("extractor", "");
        if (!CookieStore.isKnownExtractor(extractor)) {
            throw new DownloadException(DownloadError.UNKNOWN, "不支援的站點");
        }
        return extractor;
    }

    @PluginMethod
    public void saveCookie(PluginCall call) {
        // KeyStore 初始化數百 ms，走 background（與 resolve 同模式）。
        final String callbackId = call.getCallbackId();
        getBridge().saveCall(call);
        executor.submit(() -> {
            try {
                String extractor = requireExtractor(call);
                String text = call.getString("text", "");
                CookieStore store = cookies();
                store.save(extractor, text);
                JSObject data = new JSObject();
                data.put("domains", CookieStore.domainCount(text));
                finishSaved(callbackId, (saved) -> saved.resolve(data));
            } catch (DownloadException e) {
                finishSaved(callbackId, (saved) -> saved.reject(e.getMessage(), e.getCode().name()));
            } catch (Throwable t) {
                android.util.Log.e("YtDlpPlugin", "saveCookie 意外失敗", t);
                finishSaved(callbackId, (saved) -> saved.reject(
                        "儲存失敗", DownloadError.UNKNOWN.name()));
            }
        });
    }

    @PluginMethod
    public void clearCookie(PluginCall call) {
        final String callbackId = call.getCallbackId();
        getBridge().saveCall(call);
        executor.submit(() -> {
            try {
                String extractor = requireExtractor(call);
                cookies().clear(extractor);
                finishSaved(callbackId, PluginCall::resolve);
            } catch (DownloadException e) {
                finishSaved(callbackId, (saved) -> saved.reject(e.getMessage(), e.getCode().name()));
            } catch (Throwable t) {
                android.util.Log.e("YtDlpPlugin", "clearCookie 意外失敗", t);
                finishSaved(callbackId, (saved) -> saved.reject(
                        "清除失敗", DownloadError.UNKNOWN.name()));
            }
        });
    }

    @PluginMethod
    public void setCookieEnabled(PluginCall call) {
        final String callbackId = call.getCallbackId();
        getBridge().saveCall(call);
        executor.submit(() -> {
            try {
                String extractor = requireExtractor(call);
                boolean enabled = Boolean.TRUE.equals(call.getBoolean("enabled", false));
                cookies().setEnabled(extractor, enabled);
                finishSaved(callbackId, PluginCall::resolve);
            } catch (DownloadException e) {
                finishSaved(callbackId, (saved) -> saved.reject(e.getMessage(), e.getCode().name()));
            } catch (Throwable t) {
                android.util.Log.e("YtDlpPlugin", "setCookieEnabled 意外失敗", t);
                finishSaved(callbackId, (saved) -> saved.reject(
                        "切換失敗", DownloadError.UNKNOWN.name()));
            }
        });
    }

    @PluginMethod
    public void getCookieStatus(PluginCall call) {
        final String callbackId = call.getCallbackId();
        getBridge().saveCall(call);
        executor.submit(() -> {
            try {
                CookieStore store = cookies();
                JSArray arr = new JSArray();
                for (String extractor : CookieStore.EXTRACTORS) {
                    JSObject o = new JSObject();
                    o.put("extractor", extractor);
                    o.put("displayName", CookieStore.displayName(extractor));
                    o.put("has", store.has(extractor));
                    o.put("enabled", store.isEnabled(extractor));
                    String saved = store.load(extractor);
                    o.put("domains", saved == null ? 0 : CookieStore.domainCount(saved));
                    arr.put(o);
                }
                JSObject data = new JSObject();
                data.put("sites", arr);
                finishSaved(callbackId, (saved) -> saved.resolve(data));
            } catch (DownloadException e) {
                finishSaved(callbackId, (saved) -> saved.reject(e.getMessage(), e.getCode().name()));
            } catch (Throwable t) {
                android.util.Log.e("YtDlpPlugin", "getCookieStatus 意外失敗", t);
                finishSaved(callbackId, (saved) -> saved.reject(
                        "讀取失敗", DownloadError.UNKNOWN.name()));
            }
        });
    }

    /**
     * 整批下載（ticket 06）：URL 必須跟上次掃描一致；kind=video|audio；
     * maxHeight=整批預設上限；overrides={videoId:maxHeight} 逐項覆寫。
     * 進度走 batchProgress 事件，結束 resolve {succeeded,total,failed[]}。
     */
    @PluginMethod
    public void downloadBatch(PluginCall call) {
        String url = call.getString("url");
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            call.reject("INVALID_URL", DownloadError.UNKNOWN.name());
            return;
        }
        PlaylistResult cached = lastPlaylist;
        if (cached == null || !url.equals(lastPlaylistUrl)) {
            call.reject("請重新掃描後再整批下載", DownloadError.UNKNOWN.name());
            return;
        }
        String kind = call.getString("kind", "video");
        int maxHeight = call.getInt("maxHeight", 1080);
        String overrides = call.getString("overrides", "{}");
        if (!DownloadService.startBatch(getContext(), url, kind, maxHeight, overrides)) {
            call.reject("BUSY", DownloadError.UNKNOWN.name());
            return;
        }
        currentState = "downloading";
        currentCallbackId = call.getCallbackId();
        getBridge().saveCall(call);
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
