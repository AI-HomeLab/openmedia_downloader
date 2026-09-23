package com.openmedia.downloader;

import android.os.Looper;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;

import java.util.HashMap;
import java.util.Map;

/**
 * 直連下載：不經 YtDlp.execute/runner，直接調 yt_dlp.YoutubeDL().download()，
 * 因此可用完整 opts（含 runner 沒轉的 extractor_args）。
 * 同步阻塞，只能在 background thread。
 */
public final class DirectDownloader {
    private DirectDownloader() {
    }

    /**
     * @param playerClient null＝不指定；否則如 "web_embedded_player"。
     * @return yt-dlp exit code（0＝成功）
     */
    public static int download(android.content.Context context, String url,
                               String format, String outTemplate, String playerClient)
            throws DownloadException {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new DownloadException(DownloadError.UNKNOWN, "禁止在 main thread 下載");
        }
        try {
            YtDlpEngine.initOnce(context);
        } catch (DownloadException e) {
            throw e;
        }
        try {
            Python py = Python.getInstance();
            PyObject ytDlp = py.getModule("yt_dlp");
            Map<String, Object> opts = new HashMap<>();
            opts.put("quiet", true);
            opts.put("noplaylist", true);
            opts.put("socket_timeout", 10);
            if (format != null) {
                opts.put("format", format);
            }
            if (outTemplate != null) {
                opts.put("outtmpl", outTemplate);
            }
            if (playerClient != null) {
                Map<String, Object> youtube = new HashMap<>();
                youtube.put("player_client", playerClient);
                Map<String, Object> extractorArgs = new HashMap<>();
                extractorArgs.put("youtube", youtube);
                opts.put("extractor_args", extractorArgs);
            }
            PyObject ydl = ytDlp.callAttr("YoutubeDL", pyDict(py, opts));
            return ydl.callAttr("download", (Object) new String[]{url})
                    .toJava(Integer.class);
        } catch (Exception e) {
            throw new DownloadException(
                    ErrorMapper.fromMessage(e.getMessage()), "下載失敗", e);
        }
    }

    /** Java Map（含巢狀）→ 真正的 Python dict（HashMap 傳進去會被當 Java 物件）。 */
    static PyObject pyDict(Python py, Map<String, Object> map) {
        PyObject dict = py.getBuiltins().callAttr("dict");
        for (Map.Entry<String, Object> e : map.entrySet()) {
            Object v = e.getValue();
            if (v instanceof Map) {
                @SuppressWarnings("unchecked")
                PyObject nested = pyDict(py, (Map<String, Object>) v);
                dict.callAttr("__setitem__", e.getKey(), nested);
            } else {
                dict.callAttr("__setitem__", e.getKey(), v);
            }
        }
        return dict;
    }
}
