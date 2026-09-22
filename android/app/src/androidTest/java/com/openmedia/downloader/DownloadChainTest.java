package com.openmedia.downloader;

import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 真鏈證明（emulator/實機跑，非 mock）：init 單例 + 下一個真實小檔。
 * 主鏈用直連 mp4（generic extractor）證明完整成功路徑；
 * 另以 YouTube 證明原生 dispatch 有進到 yt-dlp（機房 IP 會吃 403，
 * 預期收斂為 EXTRACT——免費版無 TLS impersonation 的已知限制）。
 */
@RunWith(AndroidJUnit4.class)
public class DownloadChainTest {
    private static final String DIRECT_MP4 =
            "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/360/Big_Buck_Bunny_360_10s_1MB.mp4";
    private static final String YOUTUBE_VIDEO =
            "https://www.youtube.com/watch?v=aqz-KE-bpKQ";

    @Test
    public void initOnceAndDownloadTinyFile() throws Exception {
        Context ctx = ApplicationProvider.getApplicationContext();

        YtDlpEngine.initOnce(ctx);
        YtDlpEngine.initOnce(ctx); // 重複 init 必須是 no-op，不炸
        assertTrue(YtDlpEngine.isInitialized());

        File outDir = new File(ctx.getCacheDir(),
                "chain-" + System.currentTimeMillis());
        AtomicBoolean sawProgress = new AtomicBoolean(false);

        File landed = Downloader.download(ctx, DIRECT_MP4, outDir, null,
                (percent, eta, line) -> sawProgress.set(true));

        assertTrue("檔案應落地", landed.exists() && landed.length() > 0);
        assertTrue("應有進度回傳", sawProgress.get());
    }

    @Test
    public void youtubeDispatchReachesYtdlp() throws Exception {
        Context ctx = ApplicationProvider.getApplicationContext();
        YtDlpEngine.initOnce(ctx);
        File outDir = new File(ctx.getCacheDir(),
                "chain-yt-" + System.currentTimeMillis());
        try {
            // 機房 IP 吃 403 時走這裡：證明 dispatch 進了 yt-dlp 且錯誤有分級。
            File landed = Downloader.download(ctx, YOUTUBE_VIDEO, outDir, "worst", null);
            assertTrue(landed.exists());
        } catch (DownloadException e) {
            // EXTRACT = dispatch 成功、站方拒絕；NETWORK = 環境網路問題（flake，非程式錯）。
            assertTrue("應為 EXTRACT 或 NETWORK，實際=" + e.getCode(),
                    e.getCode() == DownloadError.EXTRACT
                            || e.getCode() == DownloadError.NETWORK);
        }
    }
}
