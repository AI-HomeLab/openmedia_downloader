package com.openmedia.downloader;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/**
 * 繞道實驗２：yt-dlp 只做解析拿直連，位元組走 Android 原生 HttpURLConnection
 *（Conscrypt 指紋＋Chrome UA），看 googlevideo 買不買帳。
 */
@RunWith(AndroidJUnit4.class)
public class NativeFetchProbeTest {
    private static final String VIDEO =
            "https://www.youtube.com/watch?v=aqz-KE-bpKQ";
    private static final String CHROME_UA = "Mozilla/5.0 (Linux; Android 10; Pixel 7) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    @Test
    public void probeNativeFetch() throws Exception {
        Context ctx = ApplicationProvider.getApplicationContext();
        ResolveResult r = ResolveEngine.resolve(ctx, VIDEO);
        List<VideoFormat> options = r.videoOptions();
        assertTrue(!options.isEmpty());

        StringBuilder report = new StringBuilder();
        // 只試前三個（由大到小），省時間
        for (int i = 0; i < Math.min(3, options.size()); i++) {
            String fid = options.get(i).formatId;
            String mediaUrl = findMediaUrl(ctx, VIDEO, fid);
            if (mediaUrl == null) {
                report.append(fid).append("=nourl;");
                continue;
            }
            int code = fetchHead(mediaUrl);
            report.append(fid).append('=').append(code).append(';');
            android.util.Log.w("Probe2", fid + " -> HTTP " + code);
            if (code == 200 || code == 206) {
                File out = new File(ctx.getCacheDir(), "native-" + fid + ".bin");
                long bytes = fetchFull(mediaUrl, out);
                android.util.Log.w("Probe2", fid + " downloaded bytes=" + bytes);
                if (bytes > 100 * 1024) {
                    return; // 有解
                }
            }
        }
        fail("原生抓也全滅：" + report);
    }

    /** 用直連模組重拿一次該 format 的 URL（extract 全量 info 太重，這裡簡化：略）。 */
    private static String findMediaUrl(Context ctx, String video, String formatId)
            throws Exception {
        // 直接問 yt-dlp 拿該 format 的 url：g 參數等價，重用 extract_info 全量結果太肥，
        // 此探針只取 videoOptions 快取——呼叫端已 resolve，這裡為簡化重解一次飲食。
        ResolveResult r = ResolveEngine.resolve(ctx, video);
        for (VideoFormat f : r.formats) {
            if (f.formatId.equals(formatId)) {
                return rawUrl(ctx, f);
            }
        }
        return null;
    }

    private static String rawUrl(Context ctx, VideoFormat f) {
        // format 的 url 不在 VideoFormat 模型裡（03 刻意只留 UI 欄位）；
        // 探針直接再解一次 JSON 取 url。
        try {
            com.chaquo.python.Python py = com.chaquo.python.Python.getInstance();
            com.chaquo.python.PyObject ytDlp = py.getModule("yt_dlp");
            com.chaquo.python.PyObject opts = py.getBuiltins().callAttr("dict");
            opts.callAttr("__setitem__", "quiet", true);
            opts.callAttr("__setitem__", "noplaylist", true);
            com.chaquo.python.PyObject ydl =
                    ytDlp.callAttr("YoutubeDL", opts);
            com.chaquo.python.PyObject info =
                    ydl.callAttr("extract_info", VIDEO, false);
            String dumped = py.getModule("json").callAttr("dumps", info)
                    .toJava(String.class);
            org.json.JSONObject o = new org.json.JSONObject(dumped);
            org.json.JSONArray arr = o.optJSONArray("formats");
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject x = arr.optJSONObject(i);
                if (x != null && f.formatId.equals(x.optString("format_id"))) {
                    return x.optString("url", null);
                }
            }
        } catch (Exception e) {
            android.util.Log.w("Probe2", "rawUrl fail: " + e);
        }
        return null;
    }

    private static int fetchHead(String mediaUrl) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(mediaUrl).openConnection();
            c.setRequestProperty("User-Agent", CHROME_UA);
            c.setRequestProperty("Range", "bytes=0-1023");
            c.setConnectTimeout(10000);
            c.setReadTimeout(10000);
            c.connect();
            int code = c.getResponseCode();
            android.util.Log.w("Probe2", "head code=" + code);
            return code;
        } catch (Exception e) {
            android.util.Log.w("Probe2", "head fail: " + e);
            return -1;
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }

    private static long fetchFull(String mediaUrl, File out) {
        // 分段抓：googlevideo 對小 Range 回 206、整包回 403，逐段要活得久。
        final long chunk = 1024 * 1024;
        long total = 0;
        try (FileOutputStream fos = new FileOutputStream(out)) {
            for (int i = 0; i < 8; i++) {
                long start = i * chunk;
                HttpURLConnection c = (HttpURLConnection) new URL(mediaUrl).openConnection();
                try {
                    c.setRequestProperty("User-Agent", CHROME_UA);
                    c.setRequestProperty("Range", "bytes=" + start + "-" + (start + chunk - 1));
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(15000);
                    int code = c.getResponseCode();
                    if (code != 206) {
                        android.util.Log.w("Probe2", "chunk " + i + " code=" + code);
                        return total > 0 ? total : -1;
                    }
                    try (InputStream in = c.getInputStream()) {
                        byte[] buf = new byte[64 * 1024];
                        int n;
                        long got = 0;
                        while ((n = in.read(buf)) > 0) {
                            fos.write(buf, 0, n);
                            total += n;
                            got += n;
                        }
                        if (got < chunk) {
                            return total; // 尾段，不足一塊＝下完
                        }
                    }
                } finally {
                    c.disconnect();
                }
            }
            return total;
        } catch (Exception e) {
            android.util.Log.w("Probe2", "chunked fail total=" + total + " err=" + e);
            return total > 0 ? total : -1;
        }
    }
}
