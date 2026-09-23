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
     * @param pageUrl 來源頁 URL（B 站 CDN 要拿它當 Referer；其他站忽略，可 null）。
     */
    public static Result fetch(String mediaUrl, File dest, long totalBytes, String pageUrl,
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
            int emptyStreak = 0;
            int ioStreak = 0;
            while (true) {
                if (cancelFlag != null && cancelFlag.get()) {
                    throw new DownloadCancelled();
                }
                HttpURLConnection c =
                        (HttpURLConnection) new URL(mediaUrl).openConnection();
                try {
                    c.setRequestProperty("User-Agent", userAgentFor(mediaUrl));
                    // Android 預設會送 Accept-Encoding: gzip（透明解壓），B 站 CDN
                    // 見到 gzip 直接 403（實測）；強制 identity，跟 yt-dlp 一致。
                    c.setRequestProperty("Accept-Encoding", "identity");
                    String referer = refererFor(mediaUrl, pageUrl);
                    if (referer != null) {
                        c.setRequestProperty("Referer", referer);
                    }
                    c.setRequestProperty("Range", "bytes=" + start + "-"
                            + (start + CHUNK_SIZE - 1));
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(15000);
                    int code = c.getResponseCode();
                    if (code == 416) {
                        // 超出尾端＝伺服器說的 EOF（整除對齊時發生；X 的 clen 偶爾灌水，
                        // 416 才是真 EOF）。有拿到位元組就算乾淨結束。
                        return new Result(total, total > 0);
                    }
                    if (code == 200 && start > 0) {
                        // 伺服器無視 Range（每次都回全檔）：續要會無限疊檔，直接死。
                        throw new DownloadException(DownloadError.NETWORK,
                                "伺服器不支援分段下載（HTTP 200）");
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
                    } catch (java.io.IOException e) {
                        // 讀到一半斷線（舊 Android 的 OkHttp 常見 unexpected end of
                        // stream）：已寫入的保留，從 total 續要；Range 無狀態。
                        // 零進展連續多次才放棄，避免無限迴圈。
                        if (total > start) {
                            start = total;
                            emptyStreak = 0;
                            ioStreak = 0;
                            continue;
                        }
                        if (++ioStreak >= 5) {
                            throw new DownloadException(DownloadError.NETWORK,
                                    "讀取中斷且無法續傳", e);
                        }
                        continue;
                    }
                    ioStreak = 0;
                    if (listener != null) {
                        long dtMs = Math.max(1, System.currentTimeMillis() - beginMs);
                        listener.onChunk(total, totalBytes, total * 1000 / dtMs);
                    }
                    if (totalBytes > 0 && total >= totalBytes) {
                        return new Result(total, true); // 已達宣告總長
                    }
                    if (got == 0) {
                        // 空回應：總長已知且還沒下完＝抖動，續要；未知總長＝尾端。
                        if (totalBytes > 0 && total < totalBytes && ++emptyStreak < 5) {
                            continue;
                        }
                        return new Result(total, totalBytes <= 0);
                    }
                    emptyStreak = 0;
                    start += got;
                    if (got < CHUNK_SIZE && totalBytes <= 0) {
                        return new Result(total, true); // 總長未知時的尾段
                    }
                    // 總長已知但還沒下完：短讀不代表尾端（X/twimg 會中途短讀），
                    // 下一圈從 start 續要（Range 無狀態，可重接）。
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

    /** 抓取結果：位元組數＋是否乾淨結束（達宣告總長 / 未知總長讀到尾 / 416 真 EOF）。 */
    public static class Result {
        public final long bytes;
        public final boolean eofClean;

        public Result(long bytes, boolean eofClean) {
            this.bytes = bytes;
            this.eofClean = eofClean;
        }
    }

    static String userAgent() {
        return "Mozilla/5.0 (Linux; Android 10; Pixel 7) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
    }

    /**
     * 站點 UA：B 站 CDN 擋行動端 UA（同 header 下桌面版 206、行動版 403，實測），
     * 對它送桌面 Chrome UA（跟裝置內 yt-dlp 預設一致）；其他站沿用行動端。
     * 純函式，可單測。
     */
    static String userAgentFor(String mediaUrl) {
        try {
            String host = new URL(mediaUrl).getHost().toLowerCase(java.util.Locale.US);
            if (isBilibiliCdn(host)) {
                return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36";
            }
        } catch (Exception e) {
            // 壞 URL 交給連線層報錯。
        }
        return userAgent();
    }

    /**
     * B 站 CDN 判斷：bilivideo.com 整域是 B 站的；akamaized.net 是通用 Akamai，
     * 只認含 mirrorakam 的（B 站前綴，實測），避免把桌面 UA＋B 站 Referer
     * 送給別人家 CDN。
     */
    private static boolean isBilibiliCdn(String host) {
        if (host.endsWith(".bilivideo.com") || host.equals("bilivideo.com")) {
            return true;
        }
        return host.contains("mirrorakam") && host.endsWith(".akamaized.net");
    }

    /**
     * 站點 Referer：B 站 CDN（bilivideo/akamaized）擋無 Referer 請求，
     * 且認的是「影片頁完整 URL」（只送域名照樣 403，實測）。
     * 其他站回 null（不送）。純函式，可單測。
     */
    static String refererFor(String mediaUrl, String pageUrl) {
        try {
            String host = new URL(mediaUrl).getHost().toLowerCase(java.util.Locale.US);
            if (isBilibiliCdn(host)) {
                if (pageUrl != null && !pageUrl.isEmpty()) {
                    return pageUrl;
                }
                return "https://www.bilibili.com/";
            }
        } catch (Exception e) {
            // URL 壞掉就交給連線層報錯，這裡不擋。
        }
        return null;
    }
}
