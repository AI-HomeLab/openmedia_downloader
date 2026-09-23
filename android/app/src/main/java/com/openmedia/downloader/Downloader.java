package com.openmedia.downloader;

import android.content.Context;
import android.os.Looper;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import dev.ffmpegkit_maintained.ytdlp.DownloadProgressCallback;
import dev.ffmpegkit_maintained.ytdlp.YtDlp;
import dev.ffmpegkit_maintained.ytdlp.YtDlpException;
import dev.ffmpegkit_maintained.ytdlp.YtDlpRequest;
import dev.ffmpegkit_maintained.ytdlp.YtDlpResponse;

/**
 * 同步下載器：包 library 的 execute，下載單一公開影片。
 * 呼叫者必須在 background thread（library 同步 API 的硬性要求），main thread 呼叫視為 bug。
 */
public final class Downloader {
    /** 進度回傳（library 從 background thread 呼叫）。 */
    public interface ProgressListener {
        void onProgress(float percent, long etaSeconds, String line);
    }

    private Downloader() {
    }

    /**
     * @param format yt-dlp -f 語意（例如 "best"、"worst"）；null/空字串用 yt-dlp 預設。
     *               正式流程的 format 字串一律來自 03 的 resolve 清單，不手寫編號。
     * @return 落地檔案（本次呼叫新增的檔案中最新者）
     */
    public static File download(Context context, String url, File outputDir,
                                String format, ProgressListener listener)
            throws DownloadException {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new DownloadException(DownloadError.UNKNOWN, "禁止在 main thread 下載");
        }
        YtDlpEngine.initOnce(context);
        if (!outputDir.isDirectory() && !outputDir.mkdirs()) {
            throw new DownloadException(DownloadError.STORAGE, "建輸出目錄失敗");
        }
        Set<String> before = listNames(outputDir);
        String template = new File(outputDir, "%(title)s.%(ext)s").getAbsolutePath();
        YtDlpRequest request = new YtDlpRequest(url)
                .setOutputTemplate(template)
                .addOption("--no-playlist");
        if (format != null && !format.isEmpty()) {
            request.addOption("-f", format);
        }
        DownloadProgressCallback callback = null;
        if (listener != null) {
            callback = (progress, eta, line) -> listener.onProgress(progress, eta, line);
        }
        final YtDlpResponse response;
        try {
            response = YtDlp.execute(request, callback);
        } catch (RuntimeException e) {
            // 進度回呼的取消控制流：原樣重拋，不包成 DownloadException。
            throw e;
        } catch (YtDlpException e) {
            DownloadCancelled cancelled = findCancelled(e);
            if (cancelled != null) {
                throw cancelled;
            }
            DownloadError code = ErrorMapper.fromMessage(e.getMessage());
            java.io.File partial = code == DownloadError.POSTPROCESS
                    ? newestNewFile(outputDir, before) : null;
            throw new DownloadException(code, "下載失敗", partial, e);
        }
        if (!response.isSuccess()) {
            throw new DownloadException(
                    DownloadError.EXTRACT, "yt-dlp exit=" + response.getExitCode());
        }
        File landed = newestNewFile(outputDir, before);
        if (landed == null) {
            throw new DownloadException(DownloadError.STORAGE, "下載成功但找不到檔案");
        }
        return landed;
    }

    private static DownloadCancelled findCancelled(Throwable t) {
        while (t != null) {
            if (t instanceof DownloadCancelled) {
                return (DownloadCancelled) t;
            }
            t = t.getCause();
        }
        return null;
    }

    private static Set<String> listNames(File dir) {
        Set<String> names = new HashSet<>();
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                names.add(f.getName());
            }
        }
        return names;
    }

    /** 只在本次呼叫新增的檔案中挑最新的；舊殘留檔不算數。 */
    private static File newestNewFile(File dir, Set<String> before) {
        File[] files = dir.listFiles();
        if (files == null) {
            return null;
        }
        File newest = null;
        for (File f : files) {
            if (before.contains(f.getName())) {
                continue;
            }
            if (newest == null || f.lastModified() > newest.lastModified()) {
                newest = f;
            }
        }
        return newest;
    }
}
