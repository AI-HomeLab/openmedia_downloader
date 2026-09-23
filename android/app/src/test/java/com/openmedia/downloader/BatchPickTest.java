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
}
