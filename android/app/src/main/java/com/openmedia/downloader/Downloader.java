package com.openmedia.downloader;

import android.content.Context;
import android.os.Looper;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 下載管線入口（05 起）：resolve → 分段抓（→合併）→ 回傳媒體檔。
 * 不再經 YtDlp.execute（整包抓在 Android 上吃媒體 403）。
 * 同步阻塞，呼叫者必須在 background thread（有 Looper guard）。
 */
public final class Downloader {
    /** 進度回傳（library 從 background thread 呼叫）。 */
    public interface ProgressListener {
        void onProgress(float percent, long etaSeconds, long speedBps, String line);
    }

    private Downloader() {
    }

    /**
     * @param format formatId（resolve 清單的 opaque token），或 best/worst/bestaudio 語意；
     *               null/空＝預設最佳。
     * @param kind "video" 或 "audio"（audio 不要求影像、只取音軌）。
     * @return 落地檔案（outputDir 內本次新增者）
     */
    public static File download(Context context, String url, File outputDir,
                                String format, String kind, ProgressListener listener)
            throws DownloadException {
        return download(context, url, outputDir, format, kind, listener, null);
    }

    static File download(Context context, String url, File outputDir,
                         String format, String kind, ProgressListener listener,
                         AtomicBoolean cancelFlag)
            throws DownloadException {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new DownloadException(DownloadError.UNKNOWN, "禁止在 main thread 下載");
        }
        boolean audioMode = "audio".equals(kind);
        ResolveResult resolved;
        try {
            resolved = ResolveEngine.resolve(context, url, !audioMode);
        } catch (ResolveException e) {
            throw new DownloadException(e.getCode(), e.getMessage(), e);
        }
        VideoFormat picked = pick(resolved, format, audioMode);
        if (picked == null || picked.url == null) {
            throw new DownloadException(DownloadError.EXTRACT, "沒有可下載的格式");
        }
        if (!outputDir.isDirectory() && !outputDir.mkdirs()) {
            throw new DownloadException(DownloadError.STORAGE, "建輸出目錄失敗");
        }
        String base = sanitize(resolved.title.isEmpty() ? resolved.videoId : resolved.title);

        VideoFormat companion = null;
        if (!audioMode && !picked.hasAudio()) {
            companion = resolved.bestAudio();
            if (companion != null && (companion.url == null
                    || companion.formatId.equals(picked.formatId))) {
                companion = null; // 無伴可合，原樣回（UI 標無聲）
            }
        }

        // 進度尺度：抓取佔 0-90，合併尾段 90-100。
        File videoFile = new File(outputDir, "dl-" + base + "." + picked.ext);
        ChunkedFetcher.Result vr = fetchOne(picked, videoFile, url, listener, cancelFlag, 0,
                companion == null ? 90 : 45);
        verifySize(videoFile, picked.filesize, vr.eofClean);

        if (companion == null) {
            return toCleanName(videoFile);
        }
        File audioFile = new File(outputDir, "dl-" + base + ".m4a");
        ChunkedFetcher.Result ar = fetchOne(companion, audioFile, url, listener, cancelFlag, 45, 90);
        verifySize(audioFile, companion.filesize, ar.eofClean);
        if (listener != null) {
            listener.onProgress(95, 0, -1, "");
        }
        return MediaMerger.merge(videoFile, audioFile, outputDir, base);
    }

    /**
     * 落檔基本驗證：空檔必死；已知總長、抓取又非乾淨結束（非達標/非真 EOF）
     * 且差太多也死（NETWORK）。eofClean 是 ChunkedFetcher 的結束方式回報。
     */
    static void verifySize(File file, long expected, boolean eofClean) throws DownloadException {
        long len = file.length();
        if (len <= 0) {
            throw new DownloadException(DownloadError.NETWORK, "下載為空檔");
        }
        if (expected > 0 && len < expected && !eofClean) {
            throw new DownloadException(DownloadError.NETWORK,
                    "檔案不完整（" + len + "/" + expected + "）");
        }
    }

    private static ChunkedFetcher.Result fetchOne(VideoFormat format, File dest, String pageUrl,
                                                  ProgressListener listener, AtomicBoolean cancelFlag,
                                                  int rangeStart, int rangeEnd) throws DownloadException {
        long total = format.filesize;
        return ChunkedFetcher.fetch(format.url, dest, total, pageUrl,
                (downloaded, partTotal, speed) -> {
                    if (listener == null) {
                        return;
                    }
                    float percent;
                    long eta;
                    if (partTotal > 0) {
                        percent = rangeStart
                                + (downloaded * (rangeEnd - rangeStart) / (float) partTotal);
                        eta = speed > 0 ? (partTotal - downloaded) / speed : 0;
                    } else {
                        percent = -1f;
                        eta = 0;
                    }
                    listener.onProgress(percent, eta, speed, "");
                }, cancelFlag);
    }

    /**
     * 去 staging 前綴：中間檔叫 dl-&lt;base&gt;（防快取撞名），交出去的成品用乾淨標題。
     * 合併產物本來就乾淨，不動。rename 失敗就回原檔（不讓改名拖死下載）。
     */
    static File toCleanName(File landed) {
        String name = landed.getName();
        if (!name.startsWith("dl-")) {
            return landed;
        }
        File target = new File(landed.getParent(), name.substring(3));
        try {
            if (landed.renameTo(target)) {
                return target;
            }
        } catch (Exception e) {
            // fall through，回原檔
        }
        return landed;
    }

    /**
     * 整批政策選片（ticket 06）：上限高度內取最高；有聲優先（免合併），
     * 無有聲取最高無聲走合併；上限內全無則退回最小（不讓整批卡死）。
     * 純函式，可單測。音檔整批不走這裡（bestaudio 即最高音質）。
     */
    static VideoFormat pickByPolicy(List<VideoFormat> options, int maxHeight) {
        if (options == null || options.isEmpty()) {
            return null;
        }
        VideoFormat bestSpoken = null;
        VideoFormat bestMute = null;
        VideoFormat smallest = null;
        for (VideoFormat f : options) {
            if (!f.hasVideo() || f.height <= 0) {
                continue;
            }
            if (smallest == null || f.height < smallest.height) {
                smallest = f;
            }
            if (f.height > maxHeight) {
                continue;
            }
            if (f.hasAudio()) {
                if (bestSpoken == null || f.height > bestSpoken.height) {
                    bestSpoken = f;
                }
            } else if (bestMute == null || f.height > bestMute.height) {
                bestMute = f;
            }
        }
        if (bestSpoken != null) {
            return bestSpoken;
        }
        if (bestMute != null) {
            return bestMute;
        }
        return smallest;
    }

    static VideoFormat pick(ResolveResult resolved, String format, boolean audioMode) {
        if (audioMode) {
            VideoFormat audio = resolved.bestAudio();
            if (audio != null) {
                return audio;
            }
        }
        List<VideoFormat> options = resolved.videoOptions();
        if (format != null && !format.isEmpty()
                && !"best".equals(format) && !"worst".equals(format)
                && !format.startsWith("bestaudio")) {
            for (VideoFormat f : options) {
                if (format.equals(f.formatId)) {
                    return f;
                }
            }
            return null;
        }
        if ("worst".equals(format)) {
            return options.isEmpty() ? null : options.get(options.size() - 1);
        }
        if (format != null && format.startsWith("bestaudio")) {
            VideoFormat audio = resolved.bestAudio();
            if (audio != null) {
                return audio;
            }
        }
        return options.isEmpty() ? null : options.get(0);
    }

    /** 檔名消毒（純函式，可單測）：只留安全字元，限長，空了退回 videoId。 */
    static String sanitize(String title) {
        String s = title.replaceAll("[^a-zA-Z0-9-_.\u4e00-\u9fff ]", "_").trim();
        if (s.length() > 80) {
            s = s.substring(0, 80);
        }
        return s.isEmpty() ? "video" : s;
    }
}
