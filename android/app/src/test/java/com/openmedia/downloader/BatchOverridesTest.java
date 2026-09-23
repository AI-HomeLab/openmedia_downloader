package com.openmedia.downloader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Map;

public class BatchOverridesTest {
    @Test
    public void parsesValidMap() {
        Map<String, Integer> m =
                DownloadService.parseOverrides("{\"abc\":720,\"def\":360}");
        assertEquals(720, (int) m.get("abc"));
        assertEquals(360, (int) m.get("def"));
    }

    @Test
    public void ignoresGarbage() {
        Map<String, Integer> m = DownloadService.parseOverrides("not json");
        assertTrue(m.isEmpty());
        assertTrue(DownloadService.parseOverrides(null).isEmpty());
        assertTrue(DownloadService.parseOverrides("").isEmpty());
        Map<String, Integer> neg = DownloadService.parseOverrides("{\"x\":-1}");
        assertTrue(neg.isEmpty());
    }
}
