package com.openmedia.downloader;

import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

/** 04 真鏈證明：bestaudio 下載 → ffmpeg-kit 轉 mp3 落地。 */
@RunWith(AndroidJUnit4.class)
public class AudioDownloadTest {
    // 有聲且機房 IP 抓得到的來源（YouTube bestaudio 在此環境 403，留給 06 實站驗）。
    private static final String URL =
            "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3";

    @Test
    public void bestaudioDownloadAndTranscodeToMp3() throws Exception {
        Context ctx = ApplicationProvider.getApplicationContext();
        File outDir = new File(ctx.getCacheDir(),
                "audio-" + System.currentTimeMillis());
        File audio = Downloader.download(ctx, URL, outDir, "bestaudio/best", "audio", null);
        assertTrue(audio.exists() && audio.length() > 0);

        File mp3 = new FFmpegAudioTranscoder().transcode(audio);
        assertTrue("應產出 mp3", mp3.getName().endsWith(".mp3") && mp3.exists()
                && mp3.length() > 0);
    }
}
