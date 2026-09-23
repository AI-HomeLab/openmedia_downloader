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
    // SpaceX Starship 靜態點火（74 秒），公開推文。
    private static final String X_VIDEO =
            "https://x.com/SpaceX/status/2072695632104468543";
    // B 站公開短片（140 秒）。
    private static final String BILIBILI_VIDEO =
            "https://www.bilibili.com/video/BV1hy4y1D734";
    // 不存在的推文 ID：404，必定 EXTRACT。
    private static final String X_DELETED =
            "https://x.com/SpaceX/status/1";

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
        // 站差異：best 是 2160p（234MB），驗收用 720p 政策（跟整批同路）。
        VideoFormat picked = Downloader.pickByPolicy(r.videoOptions(), 720);
        if (picked == null) {
            fail("X 無可用畫質");
        }
        File outDir = new File(ctx.getCacheDir(), "accept-x");
        File f = Downloader.download(ctx, X_VIDEO, outDir, picked.formatId, "video", null);
        android.util.Log.w("Accept07", "X saved=" + f.getAbsolutePath()
                + " size=" + f.length() + " picked=" + picked.formatId);
        assertTrue(f.isFile() && f.length() > 100 * 1024);
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
        assertTrue(f.isFile() && f.length() > 100 * 1024);
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
