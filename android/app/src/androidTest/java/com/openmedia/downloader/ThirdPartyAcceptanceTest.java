package com.openmedia.downloader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.List;

/**
 * 07 逐站驗收：X 推文影片＋Bilibili 公開片各走一次 resolve→download，
 * 記錄站差異；刪除/不存在內容回 EXTRACT（非 crash）。
 * 需外網；站方改版會先在這裡紅掉（即驗收意義）。
 */
@RunWith(AndroidJUnit4.class)
public class ThirdPartyAcceptanceTest {
    // 自家 X 短片（3 秒），公開推文。
    private static final String X_VIDEO =
            "https://x.com/TsukiSama9292/status/2103494117791822160";
    // 自家 B 站短片（3 秒，b23 短連）。
    private static final String BILIBILI_VIDEO =
            "https://b23.tv/EQzrzUu";
    // 不存在的推文 ID：404，必定 EXTRACT。
    private static final String X_DELETED =
            "https://x.com/SpaceX/status/1";

    /**
     * 落地斷言（3 秒短片專用）：檔頭是 ftyp＋>4KB。
     * 舊門檻 100KB 是 74 秒長片調出來的，短片正常只有幾十 KB（實測 B 站 720p 僅 9KB，
     * 档頭 ftyp/isom 有效——驗的是「真媒體落地」不是「夠大」）。
     */
    private static void assertMediaLanded(File f) throws Exception {
        assertTrue("檔案應落地", f.isFile() && f.length() > 4096);
        byte[] head = new byte[8];
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            int n = 0, r;
            while (n < 8 && (r = in.read(head, n, 8 - n)) > 0) {
                n += r;
            }
            assertTrue("應讀到檔頭", n == 8);
        }
        assertEquals("應為 mp4（ftyp）", "ftyp", new String(head, 4, 4, "US-ASCII"));
    }

    @Test
    public void xResolvesSingleFile() throws Exception {
        Context ctx = ApplicationProvider.getApplicationContext();
        ResolveResult r = ResolveEngine.resolve(ctx, X_VIDEO);
        android.util.Log.w("Accept07", "X title=" + r.title
                + " formats=" + r.formats.size());
        assertTrue(r.hasPlayableVideo());
        // 站差異記錄：X 多為單檔 progressive（有聲免合併）。
        for (VideoFormat f : r.videoOptions()) {
            android.util.Log.w("Accept07", "X opt " + f.formatId
                    + " h=" + f.height + " audio=" + f.hasAudio());
        }
    }

    @Test
    public void xDownloads() throws Exception {
        Context ctx = ApplicationProvider.getApplicationContext();
        ResolveResult r = ResolveEngine.resolve(ctx, X_VIDEO);
        // 驗收用 720p 政策（跟整批同路）。
        VideoFormat picked = Downloader.pickByPolicy(r.videoOptions(), 720);
        if (picked == null) {
            fail("X 無可用畫質");
        }
        File outDir = new File(ctx.getCacheDir(), "accept-x");
        File f = Downloader.download(ctx, X_VIDEO, outDir, picked.formatId, "video", null);
        android.util.Log.w("Accept07", "X saved=" + f.getAbsolutePath()
                + " size=" + f.length() + " picked=" + picked.formatId);
        assertMediaLanded(f);
    }

    @Test
    public void bilibiliResolves() throws Exception {
        Context ctx = ApplicationProvider.getApplicationContext();
        ResolveResult r = ResolveEngine.resolve(ctx, BILIBILI_VIDEO);
        android.util.Log.w("Accept07", "Bili title=" + r.title
                + " formats=" + r.formats.size());
        assertTrue(r.hasPlayableVideo());
        for (VideoFormat f : r.videoOptions()) {
            android.util.Log.w("Accept07", "Bili opt " + f.formatId
                    + " h=" + f.height + " audio=" + f.hasAudio());
        }
    }

    @Test
    public void bilibiliDownloads() throws Exception {
        Context ctx = ApplicationProvider.getApplicationContext();
        ResolveResult r = ResolveEngine.resolve(ctx, BILIBILI_VIDEO);
        // 免登入清晰度：取政策上限內最高（通常 480p 有聲或分離式＋合併）。
        VideoFormat picked = Downloader.pickByPolicy(r.videoOptions(), 720);
        if (picked == null) {
            fail("B 站無可用畫質（可能改需登入）");
        }
        android.util.Log.w("Accept07", "Bili picked=" + picked.formatId
                + " host=" + picked.url.substring(0, Math.min(90, picked.url.length())));
        File outDir = new File(ctx.getCacheDir(), "accept-bili");
        File f = Downloader.download(ctx, BILIBILI_VIDEO, outDir,
                picked.formatId, "video", null);
        android.util.Log.w("Accept07", "Bili saved=" + f.getAbsolutePath()
                + " size=" + f.length() + " picked=" + picked.formatId);
        assertMediaLanded(f);
    }

    @Test
    public void deletedTweetMapsToExtract() throws Exception {
        Context ctx = ApplicationProvider.getApplicationContext();
        try {
            ResolveEngine.resolve(ctx, X_DELETED);
            fail("刪除推文應拋錯");
        } catch (ResolveException e) {
            assertEquals(DownloadError.EXTRACT, e.getCode());
        }
    }
}
