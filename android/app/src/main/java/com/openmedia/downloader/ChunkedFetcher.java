package com.openmedia.downloader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 分段下載器：googlevideo 對小 Range 回 206、整包回 403，所以逐段要。
 * 每段回報進度；取消旗標每段檢查；失敗整單重來（不斷點續傳，見 ticket 05）。
 * 同步阻塞，只能在 background thread。
 */
public final class ChunkedFetcher {
    /** 單段大小（實證值：1MB 段在 emulator 連續命中，見 NativeFetchProbeTest）。 */
    static final long CHUNK_SIZE = 1024 * 1024;

    /** 下載進度（含速度；speedBps < 0 表未知）。 */
    public interface Listener {
        void onChunk(long downloadedBytes, long totalBytes, long speedBps);
    }

    private ChunkedFetcher() {
    }

    /**
     * @param totalBytes 總位元組（未知傳 -1；此時 percent 由上層處理）。
     * @return 實際寫入位元組數
     */
    public static long fetch(String mediaUrl, File dest, long totalBytes,
                             Listener listener, java.util.concurrent.atomic.AtomicBoolean cancelFlag)
            throws DownloadException {
        // NOTE: 先清掉舊殘檔，避免斷尾續接污染。
        if (dest.exists()) {
            dest.delete();
        }
        long total = 0;
        try (FileOutputStream fos = new FileOutputStream(dest)) {
            long start = 0;
            long beginMs = System.currentTimeMillis();
            while (true) {
                if (cancelFlag != null && cancelFlag.get()) {
                    throw new DownloadCancelled();
                }
                HttpURLConnection c =
                        (HttpURLConnection) new URL(mediaUrl).openConnection();
                try {
                    c.setRequestProperty("User-Agent", userAgent());
                    c.setRequestProperty("Range", "bytes=" + start + "-"
                            + (start + CHUNK_SIZE - 1));
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(15000);
                    int code = c.getResponseCode();
                    if (code == 416) {
                        return total; // 超出尾端＝已下完（整除對齊時發生）
                    }
                    if (code != 206 && !(code == 200 && start == 0)) {
                        DownloadError mapped = (code == 403 || code == 429)
                                ? DownloadError.EXTRACT : DownloadError.NETWORK;
                        throw new DownloadException(mapped,
                                "分段下載被拒（HTTP " + code + "）");
                    }
                    long got = 0;
                    try (InputStream in = c.getInputStream()) {
                        byte[] buf = new byte[64 * 1024];
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            if (cancelFlag != null && cancelFlag.get()) {
                                throw new DownloadCancelled();
                            }
                            fos.write(buf, 0, n);
                            total += n;
                            got += n;
                        }
                    }
                    if (listener != null) {
                        long dtMs = Math.max(1, System.currentTimeMillis() - beginMs);
                        listener.onChunk(total, totalBytes, total * 1000 / dtMs);
                    }
                    if (got == 0) {
                        return total; // 空回應＝尾端
                    }
                    if (got < CHUNK_SIZE) {
                        return total; // 尾段，不足一塊＝下完
                    }
                    start += got;
                } finally {
                    c.disconnect();
                }
            }
        } catch (DownloadCancelled e) {
            throw e;
        } catch (DownloadException e) {
            throw e;
        } catch (Exception e) {
            throw new DownloadException(
                    ErrorMapper.fromMessage(e.getMessage()), "分段下載失敗", e);
        }
    }

    static String userAgent() {
        return "Mozilla/5.0 (Linux; Android 10; Pixel 7) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
    }
}
