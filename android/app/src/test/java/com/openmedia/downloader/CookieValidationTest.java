package com.openmedia.downloader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class CookieValidationTest {
    private static final String GOOD =
            "# Netscape HTTP Cookie File\n"
                    + ".youtube.com\tTRUE\t/\tTRUE\t9999999999\tSID\tABC123\n"
                    + ".google.com\tTRUE\t/\tTRUE\t9999999999\tNID\tXYZ\n";

    @Test
    public void acceptsValidFile() throws Exception {
        CookieStore.validate(GOOD);
        assertEquals(2, CookieStore.domainCount(GOOD));
    }

    @Test
    public void countsDedupedDomains() {
        String dup = "# Netscape HTTP Cookie File\n"
                + ".youtube.com\tTRUE\t/\tTRUE\t1\tA\t1\n"
                + "youtube.com\tTRUE\t/\tTRUE\t1\tB\t2\n";
        assertEquals(1, CookieStore.domainCount(dup));
    }

    @Test
    public void rejectsEmpty() {
        try {
            CookieStore.validate("  \n ");
            fail("空字串應拒絕");
        } catch (DownloadException e) {
            assertEquals(DownloadError.EXTRACT, e.getCode());
        }
    }

    @Test
    public void rejectsNonCookieText() {
        try {
            CookieStore.validate("hello world");
            fail("非 cookies.txt 應拒絕");
        } catch (DownloadException e) {
            assertEquals(DownloadError.EXTRACT, e.getCode());
        }
    }

    @Test
    public void rejectsHeaderOnly() {
        try {
            CookieStore.validate("# Netscape HTTP Cookie File\n# comment only\n");
            fail("無條目應拒絕");
        } catch (DownloadException e) {
            assertEquals(DownloadError.EXTRACT, e.getCode());
        }
    }

    @Test
    public void rejectsShortLines() {
        try {
            CookieStore.validate("# Netscape HTTP Cookie File\n.youtube.com\tTRUE\n");
            fail("欄位不足應拒絕");
        } catch (DownloadException e) {
            assertEquals(DownloadError.EXTRACT, e.getCode());
        }
    }

    @Test
    public void displayNames() {
        assertEquals("YouTube", CookieStore.displayName("youtube"));
        assertEquals("Bilibili", CookieStore.displayName("bilibili"));
        assertEquals("X", CookieStore.displayName("twitter"));
        assertTrue(CookieStore.isKnownExtractor("youtube"));
        assertTrue(!CookieStore.isKnownExtractor("netflix"));
    }

    @Test
    public void extractorForUrl() {
        assertEquals("youtube", CookieStore.extractorForUrl("https://www.youtube.com/watch?v=x"));
        assertEquals("youtube", CookieStore.extractorForUrl("https://youtu.be/x"));
        assertEquals("youtube", CookieStore.extractorForUrl("https://www.youtube.com/playlist?list=PLx"));
        assertEquals("bilibili", CookieStore.extractorForUrl("https://www.bilibili.com/video/BVx"));
        assertEquals("bilibili", CookieStore.extractorForUrl("https://b23.tv/czLSA4v"));
        assertEquals("twitter", CookieStore.extractorForUrl("https://x.com/a/status/1"));
        assertEquals("twitter", CookieStore.extractorForUrl("https://twitter.com/a/status/1"));
        assertEquals("", CookieStore.extractorForUrl("https://example.com/v.mp4"));
        assertEquals("", CookieStore.extractorForUrl(null));
        assertEquals("", CookieStore.extractorForUrl("not a url"));
        // 子字串誤判防線（review Finding 1）：這些都不該命中任何站。
        assertEquals("", CookieStore.extractorForUrl("https://linux.com/video"));
        assertEquals("", CookieStore.extractorForUrl("https://x.com.evil.com/v"));
        assertEquals("", CookieStore.extractorForUrl("https://example.com/?next=x.com"));
        assertEquals("youtube", CookieStore.extractorForUrl("https://m.youtube.com/watch?v=x"));
        assertEquals("twitter", CookieStore.extractorForUrl("https://mobile.twitter.com/a/status/1"));
    }
}
