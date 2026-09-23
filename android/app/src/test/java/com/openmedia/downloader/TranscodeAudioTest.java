package com.openmedia.downloader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;

public class TranscodeAudioTest {
    @Test
    public void successPassesThrough() throws Exception {
        File in = new File("/tmp/in.m4a");
        File out = new File("/tmp/out.mp3");
        AudioTranscoder fake = new AudioTranscoder() {
            @Override
            public File transcode(File input) {
                return out;
            }
        };
        DownloadService.TranscodeResult r = DownloadService.transcodeAudio(fake, in);
        assertTrue(r.processed);
        assertEquals(out, r.file);
    }

    @Test
    public void nativeMissingFallsBackToOriginal() throws Exception {
        // JVM 上無 ffmpeg native：走 Throwable 分支，留原檔＋processed=false。
        File in = new File("/tmp/in.m4a");
        DownloadService.TranscodeResult r = DownloadService.transcodeAudio(
                new FFmpegAudioTranscoder(), in);
        assertFalse(r.processed);
        assertEquals(in, r.file);
    }

    @Test
    public void downloadExceptionKeepsPartialFile() throws Exception {
        File in = new File("/tmp/in.m4a");
        final File partial = new File("/tmp/partial.mp3");
        AudioTranscoder failing = new AudioTranscoder() {
            @Override
            public File transcode(File input) throws DownloadException {
                throw new DownloadException(DownloadError.POSTPROCESS, "爛了", partial, null);
            }
        };
        DownloadService.TranscodeResult r = DownloadService.transcodeAudio(failing, in);
        assertFalse(r.processed);
        assertEquals(partial, r.file);
    }
}
