package com.openmedia.downloader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ChunkedFetcherTest {
    @Test
    public void bilibiliCdnNeedsPageReferer() {
        String page = "https://www.bilibili.com/video/BV1hy4y1D734";
        assertEquals(page,
                ChunkedFetcher.refererFor(
                        "https://upos-hz-mirrorakam.akamaized.net/upos/video.mp4", page));
        assertEquals(page,
                ChunkedFetcher.refererFor("https://cn-sj1.bilivideo.com/video.mp4", page));
        // 沒給頁 URL 就退回域名（總比不送好）。
        assertEquals("https://www.bilibili.com/",
                ChunkedFetcher.refererFor("https://cn-sj1.bilivideo.com/video.mp4", null));
    }

    @Test
    public void otherHostsNeedNoReferer() {
        assertNull(ChunkedFetcher.refererFor(
                "https://rr2---sn-3cgv-un5e6.googlevideo.com/videoplayback?x=1",
                "https://www.youtube.com/watch?v=aqz-KE-bpKQ"));
        assertNull(ChunkedFetcher.refererFor("https://video.twimg.com/vid.mp4",
                "https://x.com/a/status/1"));
        assertNull(ChunkedFetcher.refererFor("not a url", "https://x.com/"));
        // 通用 Akamai 非 B 站前綴：不送桌面 UA＋B 站 Referer。
        assertNull(ChunkedFetcher.refererFor("https://cdn.akamaized.net/v.mp4",
                "https://www.bilibili.com/video/BV1hy4y1D734"));
    }

    @Test
    public void bilibiliCdnGetsDesktopUa() {
        String ua = ChunkedFetcher.userAgentFor("https://cn-sj1.bilivideo.com/video.mp4");
        assertTrue(ua.contains("Windows NT"));
    }

    @Test
    public void otherHostsKeepMobileUa() {
        assertEquals(ChunkedFetcher.userAgent(),
                ChunkedFetcher.userAgentFor(
                        "https://rr2---sn-3cgv-un5e6.googlevideo.com/videoplayback?x=1"));
    }
}
