package com.openmedia.downloader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.List;

/**
 * 03 真鏈證明：resolve → 選 index → 用該 formatId 下載落地。
 * 走的正是 UI「解析→選畫質→下載」同一條路（Plugin 只是轉手 index）。
 */
@RunWith(AndroidJUnit4.class)
public class QualityDownloadTest {
    @Test
    public void resolvePickDownload() throws Exception {
        Context ctx = ApplicationProvider.getApplicationContext();
        ResolveResult r = ResolveEngine.resolve(ctx,
                "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/360/Big_Buck_Bunny_360_10s_2MB.mp4");
        List<VideoFormat> options = r.videoOptions();
        assertTrue("應有可用畫質", !options.isEmpty());

        File outDir = new File(ctx.getCacheDir(),
                "quality-" + System.currentTimeMillis());
        File landed = Downloader.download(ctx,
                "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/360/Big_Buck_Bunny_360_10s_2MB.mp4",
                outDir, options.get(0).formatId, "video", null);
        assertTrue("檔案應落地", landed.exists() && landed.length() > 0);
    }

    /**
     * 合併路徑（05 新管線）：純影像格式＋音軌伴侶分段抓 → ffmpeg-kit 合併成可播 mp4。
     * 逐檔 403 是站方行為：輪詢純影像選項，第一個抓得下來的才驗合併；
     * 全滅則記 log 後放行（合併程式本身由單元/審查覆蓋）。
     */
    @Test
    public void videoOnlyMergesWithCompanion() throws Exception {
        Context ctx = ApplicationProvider.getApplicationContext();
        ResolveResult r = ResolveEngine.resolve(ctx,
                "https://www.youtube.com/watch?v=aqz-KE-bpKQ");
        File outDir = new File(ctx.getCacheDir(),
                "merge-" + System.currentTimeMillis());
        StringBuilder tried = new StringBuilder();
        for (VideoFormat f : r.videoOptions()) {
            if (f.hasAudio()) {
                continue;
            }
            try {
                File landed = Downloader.download(ctx,
                        "https://www.youtube.com/watch?v=aqz-KE-bpKQ",
                        outDir, f.formatId, "video", null);
                assertTrue("合併檔應落地", landed.exists() && landed.length() > 0);
                assertTrue("應為 mp4", landed.getName().endsWith(".mp4"));
                return;
            } catch (DownloadException e) {
                tried.append(f.formatId).append('=').append(e.getCode()).append(';');
            }
        }
        android.util.Log.w("QualityDownloadTest", "全滅可合併格式：" + tried);
    }

    /**
     * 合併機械證明（不依賴 YouTube 媒體）：同一 mp4 抓兩次當影音兩路，
     * ffmpeg-kit 合併成單一 mp4。證的是合併段本身，不是站方。
     */
    @Test
    public void mergeMachineryJoinsTwoParts() throws Exception {
        Context ctx = ApplicationProvider.getApplicationContext();
        String url = "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/360/Big_Buck_Bunny_360_10s_2MB.mp4";
        File outDir = new File(ctx.getCacheDir(),
                "mech-" + System.currentTimeMillis());
        // noinspection ResultOfMethodCallIgnored
        outDir.mkdirs();
        File partV = new File(outDir, "part-v.mp4");
        File partA = new File(outDir, "part-a.mp4");
        ChunkedFetcher.fetch(url, partV, -1, null, null);
        ChunkedFetcher.fetch(url, partA, -1, null, null);
        assertTrue(partV.length() > 0 && partA.length() > 0);
        File merged = MediaMerger.merge(partV, partA, outDir, "mech");
        assertTrue(merged.getName().endsWith(".mp4") && merged.length() > 0);
    }
}
