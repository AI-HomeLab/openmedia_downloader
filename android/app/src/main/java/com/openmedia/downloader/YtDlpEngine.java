package com.openmedia.downloader;

import android.content.Context;

import com.chaquo.python.Python;

import dev.ffmpegkit_maintained.ytdlp.YtDlp;
import dev.ffmpegkit_maintained.ytdlp.YtDlpException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * library 初始化單例：YtDlp.init（內含 Chaquopy/Python 啟動）全進程只跑一次。
 * 重複 init 視為 bug，上層不應依賴多次呼叫。
 *
 * yt-dlp 疊加（overlay）：AAR 內建 yt-dlp 太舊（2026.06.09，YouTube 直連全 403），
 * 上游尚無新版 AAR。assets 自帶新版 wheel（pure Python zip，可直接 import），
 * init 時拷到內部儲存並插到 sys.path 最前蓋掉內建版。檔名含版本號，升級即換檔。
 */
public final class YtDlpEngine {
    private static volatile boolean initialized = false;

    /** 與 assets/ytdlp/ 下檔名同步；升級 yt-dlp 只換這裡＋wheel 檔。 */
    static final String OVERLAY_WHEEL = "yt_dlp-2026.8.19-py3-none-any.whl";
    private static final String OVERLAY_ASSET = "ytdlp/" + OVERLAY_WHEEL;

    private YtDlpEngine() {
    }

    public static synchronized void initOnce(Context context) throws DownloadException {
        if (initialized) {
            return;
        }
        try {
            YtDlp.init(context.getApplicationContext());
            installOverlay(context.getApplicationContext());
            initialized = true;
        } catch (YtDlpException e) {
            throw new DownloadException(
                    ErrorMapper.fromMessage(e.getMessage()), "init 失敗", e);
        } catch (DownloadException e) {
            throw e;
        } catch (Exception e) {
            throw new DownloadException(
                    ErrorMapper.fromMessage(e.getMessage()), "overlay 失敗", e);
        }
    }

    /** 拷 wheel → filesDir/ytdlp_overlay/，sys.path.insert(0) 蓋掉 AAR 內建版。 */
    static void installOverlay(Context context) throws Exception {
        File dir = new File(context.getFilesDir(), "ytdlp_overlay");
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new DownloadException(DownloadError.STORAGE, "建 overlay 目錄失敗");
        }
        // 清掉舊版殘留（檔名含版本，不同版不共存）。
        File[] stale = dir.listFiles((d, name) ->
                name.endsWith(".whl") && !name.equals(OVERLAY_WHEEL));
        if (stale != null) {
            for (File f : stale) {
                f.delete();
            }
        }
        File wheel = new File(dir, OVERLAY_WHEEL);
        if (!wheel.isFile()) {
            try (InputStream in = context.getAssets().open(OVERLAY_ASSET);
                 OutputStream out = new FileOutputStream(wheel)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
            }
        }
        Python py = Python.getInstance();
        py.getModule("sys").get("path")
                .callAttr("insert", 0, wheel.getAbsolutePath());
    }

    public static boolean isInitialized() {
        return initialized;
    }
}
