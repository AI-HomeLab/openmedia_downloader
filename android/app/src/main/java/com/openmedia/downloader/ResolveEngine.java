package com.openmedia.downloader;

import android.os.Looper;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;

/**
 * 結構化 resolve：直連 AAR 內建的 yt_dlp Python 模組做 extract_info，
 * 不經 YtDlp.execute（它只回 exit code 且吞掉 dump 參數）。
 *
 * 為什麼不用 Chaquopy plugin 自帶 .py：plugin 全 app 只能用在一個模組，
 * AAR 建置時已用掉；在 app 模組再套一次會打架。直接拿同一個直譯器 +
 * 同一份 yt_dlp，既無重複打包也無版本 skew。
 *
 * 同步阻塞呼叫，只能在 background thread（有 Looper guard）。
 */
public final class ResolveEngine {
    private ResolveEngine() {
    }

    /**
     * 底層錯誤收斂：code 照 mapper，訊息保留 yt-dlp 原文首行（截斷＋去換行，
     * 避免 token／長 traceback 進 UI）。純函式，可單測。
     */
    static ResolveException wrapFailure(Exception e) {
        String raw = e.getMessage() == null ? "" : e.getMessage();
        String firstLine = raw.split("[\\r\\n]+")[0].trim();
        if (firstLine.length() > 160) {
            firstLine = firstLine.substring(0, 160) + "…";
        }
        String message = firstLine.isEmpty() ? "解析失敗" : "解析失敗：" + firstLine;
        return new ResolveException(ErrorMapper.fromMessage(e.getMessage()), message, e);
    }

    public static ResolveResult resolve(android.content.Context context, String url)
            throws ResolveException {
        return resolve(context, url, true);
    }

    /**
     * @param requireVideo true＝一定要有可播影像（影片模式）；
     *                     false＝純聲音也可（音檔模式，不檢查、不列畫質）。
     */
    public static ResolveResult resolve(android.content.Context context, String url,
                                        boolean requireVideo)
            throws ResolveException {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new ResolveException(DownloadError.UNKNOWN, "禁止在 main thread resolve");
        }
        try {
            YtDlpEngine.initOnce(context);
        } catch (DownloadException e) {
            throw new ResolveException(e.getCode(), e.getMessage(), e);
        }
        try {
            Python py = Python.getInstance();
            PyObject ytDlp = py.getModule("yt_dlp");
            // Java Map 傳進 Python 仍是 HashMap（yt-dlp 調 .get(k, default) 會炸），
            // 必須先在 Python 側建成真正的 dict。
            PyObject opts = py.getBuiltins().callAttr("dict");
            opts.callAttr("__setitem__", "quiet", true);
            opts.callAttr("__setitem__", "noplaylist", true);
            // 行動網路＋CI 模擬器常超過 15 秒，30 秒才算超時（ticket 03 CI 實測）。
            opts.callAttr("__setitem__", "socket_timeout", 30);
            PyObject ydl = ytDlp.callAttr("YoutubeDL", opts);
            PyObject info = ydl.callAttr("extract_info", url, false);
            if (info == null) {
                throw new ResolveException(DownloadError.EXTRACT, "解析無回傳");
            }
            PyObject json = py.getModule("json");
            String dumped = json.callAttr("dumps", info).toJava(String.class);
            ResolveResult result = ResolveResult.parse(dumped);
            if (result.title.isEmpty()
                    || (requireVideo && !result.hasPlayableVideo())) {
                throw new ResolveException(DownloadError.EXTRACT,
                        requireVideo ? "找不到可下載的影片格式" : "找不到可下載的音檔");
            }
            return result;
        } catch (ResolveException e) {
            throw e;
        } catch (Exception e) {
            // 原因記 logcat（CI 上抓 resolve 失敗根因用）。
            android.util.Log.w("ResolveEngine", "resolve 失敗: " + e);
            throw wrapFailure(e);
        }
    }

    /**
     * 播放清單掃描（ticket 06）：flat extract，只拿 id/標題/連結/時長，不拿格式。
     * 單片 URL 誤傳進來（_type=video）拋 EXTRACT，呼叫方退回單片流程。
     */
    public static PlaylistResult resolvePlaylist(android.content.Context context, String url)
            throws ResolveException {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new ResolveException(DownloadError.UNKNOWN, "禁止在 main thread resolve");
        }
        try {
            YtDlpEngine.initOnce(context);
        } catch (DownloadException e) {
            throw new ResolveException(e.getCode(), e.getMessage(), e);
        }
        try {
            Python py = Python.getInstance();
            PyObject ytDlp = py.getModule("yt_dlp");
            PyObject opts = py.getBuiltins().callAttr("dict");
            opts.callAttr("__setitem__", "quiet", true);
            opts.callAttr("__setitem__", "noplaylist", false);
            opts.callAttr("__setitem__", "extract_flat", true);
            opts.callAttr("__setitem__", "socket_timeout", 30);
            PyObject ydl = ytDlp.callAttr("YoutubeDL", opts);
            PyObject info = ydl.callAttr("extract_info", url, false);
            if (info == null) {
                throw new ResolveException(DownloadError.EXTRACT, "解析無回傳");
            }
            PyObject json = py.getModule("json");
            String dumped = json.callAttr("dumps", info).toJava(String.class);
            if (!PlaylistResult.isPlaylistJson(dumped)) {
                throw new ResolveException(DownloadError.EXTRACT, "這不是播放清單");
            }
            return PlaylistResult.parse(dumped);
        } catch (ResolveException e) {
            throw e;
        } catch (Exception e) {
            throw wrapFailure(e);
        }
    }
}
