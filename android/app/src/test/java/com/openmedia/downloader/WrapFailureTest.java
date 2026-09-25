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
}
