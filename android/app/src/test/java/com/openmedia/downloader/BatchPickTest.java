package com.openmedia.downloader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class BatchPickTest {
    private static VideoFormat v(String id, String ext, int h, String vcodec, String acodec) {
        return new VideoFormat(id, ext, 0, h, 1000, vcodec, acodec, "", "http://x/" + id);
    }

    private static final List<VideoFormat> MIXED = Arrays.asList(
            v("401", "mp4", 2160, "av01", "none"), // 無聲 4K
            v("136", "mp4", 720, "avc1", "none"), // 無聲 720p
            v("22", "mp4", 720, "avc1", "mp4a"), // 有聲 720p
            v("18", "mp4", 360, "avc1", "mp4a"), // 有聲 360p
            v("160", "mp4", 144, "avc1", "none")); // 無聲 144p

    @Test
    public void prefersSpokenUnderCap() {
        // 預設 1080p 政策：720p 有聲（22）勝過同高無聲（136）。
        assertEquals("22", Downloader.pickByPolicy(MIXED, 1080).formatId);
    }

    @Test
    public void respectsLowerCap() {
        // 逐項覆寫 360p：只考慮 ≤360p，有聲 18。
        assertEquals("18", Downloader.pickByPolicy(MIXED, 360).formatId);
    }

    @Test
    public void fallsBackToMuteWhenNoSpoken() {
        List<VideoFormat> muteOnly = Arrays.asList(
                v("401", "mp4", 2160, "av01", "none"),
                v("136", "mp4", 720, "avc1", "none"));
        // ≤1080 無有聲：取最高無聲走合併（136）。
        assertEquals("136", Downloader.pickByPolicy(muteOnly, 1080).formatId);
    }

    @Test
    public void fallsBackToSmallestWhenNothingUnderCap() {
        List<VideoFormat> big = Arrays.asList(v("401", "mp4", 2160, "av01", "none"));
        // 144p 政策但只有 4K：退回最小（不讓整批卡死）。
        assertEquals("401", Downloader.pickByPolicy(big, 144).formatId);
    }

    @Test
    public void emptyOptionsReturnsNull() {
        assertNull(Downloader.pickByPolicy(java.util.Collections.emptyList(), 1080));
    }

    @Test
    public void singleBestIsTallestRegardlessOfAudio() {
        // 舊 best == options.get(0)（高度降序＋同高檔大者）：統一路徑等價。
        assertEquals("401", Downloader.selectByPolicy(MIXED, Integer.MAX_VALUE, false).formatId);
        List<VideoFormat> tie = Arrays.asList(
                v("a", "mp4", 720, "avc1", "mp4a"),
                v("b", "mp4", 720, "avc1", "mp4a"));
        // 同高比檔大（filesize 寫死同值 1000 → 取首個；語意穩定即可）。
        assertEquals("a", Downloader.selectByPolicy(tie, Integer.MAX_VALUE, false).formatId);
    }

    @Test
    public void heightUnknownSingleFileStillPickable() {
        // 直連單檔 height=0：舊 best/worst 取排序頭尾本就含它，不可判死。
        VideoFormat direct = v("mp4", "mp4", 0, null, null);
        assertEquals("mp4",
                Downloader.selectByPolicy(Arrays.asList(direct), 1080, true).formatId);
        assertEquals("mp4",
                Downloader.selectByPolicy(Arrays.asList(direct), 1080, false).formatId);
    }

    @Test
    public void worstPicksSmallest() {
        ResolveResult r = new ResolveResult("id", "t", "u", 10, MIXED);
        VideoFormat w = Downloader.pick(r, "worst", false);
        assertEquals("160", w.formatId);
        VideoFormat b = Downloader.pick(r, "best", false);
        assertEquals("401", b.formatId);
    }
}
