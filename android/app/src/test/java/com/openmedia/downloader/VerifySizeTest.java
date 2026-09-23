package com.openmedia.downloader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;

public class VerifySizeTest {
    private static File sizedFile(String name, int bytes) throws Exception {
        File dir = new File(System.getProperty("java.io.tmpdir"),
                "vsize-" + System.currentTimeMillis() + "-" + name + "-" + Math.random());
        if (!dir.mkdirs()) {
            throw new IllegalStateException("建暫存目錄失敗");
        }
        File f = new File(dir, name);
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(new byte[bytes]);
        }
        return f;
    }

    @Test
    public void shortFileWithoutCleanEofIsNetworkError() throws Exception {
        try {
            Downloader.verifySize(sizedFile("a.bin", 100), 1000, false);
            fail("非乾淨短檔應丟 NETWORK");
        } catch (DownloadException e) {
            assertEquals(DownloadError.NETWORK, e.getCode());
        }
    }

    @Test
    public void shortFileWithCleanEofPasses() throws Exception {
        // 伺服器 416 真 EOF（X clen 灌水案）：位元組有效就放行。
        Downloader.verifySize(sizedFile("b.bin", 100), 1000, true);
    }

    @Test
    public void emptyFileAlwaysDies() throws Exception {
        try {
            Downloader.verifySize(sizedFile("c.bin", 0), 1000, true);
            fail("空檔恆死");
        } catch (DownloadException e) {
            assertEquals(DownloadError.NETWORK, e.getCode());
        }
    }
}
