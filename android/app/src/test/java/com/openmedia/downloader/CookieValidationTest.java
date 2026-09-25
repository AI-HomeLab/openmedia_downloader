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
}
