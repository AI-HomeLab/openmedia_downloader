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

    public static ResolveResult resolve(android.content.Context context, String url)
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
            opts.callAttr("__setitem__", "socket_timeout", 15);
            PyObject ydl = ytDlp.callAttr("YoutubeDL", opts);
            PyObject info = ydl.callAttr("extract_info", url, false);
            if (info == null) {
                throw new ResolveException(DownloadError.EXTRACT, "解析無回傳");
            }
            PyObject json = py.getModule("json");
            String dumped = json.callAttr("dumps", info).toJava(String.class);
            ResolveResult result = ResolveResult.parse(dumped);
            if (result.title.isEmpty() || !result.hasPlayableVideo()) {
                throw new ResolveException(DownloadError.EXTRACT, "找不到可下載的影片格式");
            }
            return result;
        } catch (ResolveException e) {
            throw e;
        } catch (Exception e) {
            throw new ResolveException(
                    ErrorMapper.fromMessage(e.getMessage()), "解析失敗", e);
        }
    }
}
