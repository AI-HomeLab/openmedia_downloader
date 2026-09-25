package com.openmedia.downloader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WrapFailureTest {
    @Test
    public void keepsFirstLineOfCause() {
        ResolveException e = ResolveEngine.wrapFailure(
                new RuntimeException("[twitter] 123: Rate-limit reached\nTraceback..."));
        assertEquals(DownloadError.EXTRACT, e.getCode());
        assertTrue(e.getMessage().startsWith("解析失敗："));
        assertTrue(e.getMessage().contains("Rate-limit"));
        assertTrue(!e.getMessage().contains("Traceback"));
    }

    @Test
    public void truncatesLongMessages() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            sb.append('x');
        }
        ResolveException e = ResolveEngine.wrapFailure(new RuntimeException(sb.toString()));
        assertTrue(e.getMessage().length() <= "解析失敗：".length() + 161);
    }

    @Test
    public void emptyCauseFallsBackToGeneric() {
        ResolveException e = ResolveEngine.wrapFailure(new RuntimeException());
        assertEquals("解析失敗", e.getMessage());
        assertEquals(DownloadError.UNKNOWN, e.getCode());
    }

    @Test
    public void expiredCookieRewritesMessage() {
        ResolveException e = ResolveEngine.withExpiryNote(
                new RuntimeException("ERROR: [youtube] x: Login required"), true);
        assertEquals(DownloadError.EXTRACT, e.getCode());
        assertEquals("登入已過期，請重新貼上 cookie", e.getMessage());
        ResolveException cn = ResolveEngine.withExpiryNote(
                new RuntimeException("ERROR: [BiliBili] x: 需要登录后观看"), true);
        assertEquals("登入已過期，請重新貼上 cookie", cn.getMessage());
    }

    @Test
    public void noCookieSentKeepsOriginal() {
        ResolveException e = ResolveEngine.withExpiryNote(
                new RuntimeException("ERROR: [youtube] x: Login required"), false);
        assertTrue(e.getMessage().contains("Login required"));
    }

    @Test
    public void nonLoginErrorUntouched() {
        ResolveException e = ResolveEngine.withExpiryNote(
                new RuntimeException("ERROR: [youtube] x: Video unavailable"), true);
        assertTrue(e.getMessage().contains("Video unavailable"));
    }
}
