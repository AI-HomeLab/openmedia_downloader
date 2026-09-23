package com.openmedia.downloader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;

public class CleanNameTest {
    @Test
    public void stripsStagingPrefix() throws Exception {
        File dir = new File(System.getProperty("java.io.tmpdir"),
                "cleann-" + System.currentTimeMillis());
        assertTrue(dir.mkdirs());
        File f = new File(dir, "dl-hello.mp4");
        assertTrue(f.createNewFile());
        File out = Downloader.toCleanName(f);
        assertEquals("hello.mp4", out.getName());
        assertTrue(out.isFile());
    }

    @Test
    public void leavesCleanNamesAlone() throws Exception {
        File dir = new File(System.getProperty("java.io.tmpdir"),
                "cleann2-" + System.currentTimeMillis());
        assertTrue(dir.mkdirs());
        File f = new File(dir, "hello.mp4");
        assertTrue(f.createNewFile());
        assertEquals(f, Downloader.toCleanName(f));
    }
}
