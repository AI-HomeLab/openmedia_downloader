package com.openmedia.downloader;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** 04 service 層證明：音檔模式經 service 下載→轉檔→MediaStore，merged=true。 */
@RunWith(AndroidJUnit4.class)
public class ServiceAudioTest {
    @Test
    public void serviceAudioDownloadTranscodes() throws Exception {
        Context ctx = ApplicationProvider.getApplicationContext();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Uri> doneUri = new AtomicReference<>();
        AtomicBoolean merged = new AtomicBoolean(false);
        AtomicReference<String> fileName = new AtomicReference<>("");
        DownloadService.setListener(new DownloadService.Listener() {
            @Override
            public void onProgress(float percent, long etaSeconds) {
            }

            @Override
            public void onDone(Uri fileUri, String name, boolean m) {
                doneUri.set(fileUri);
                fileName.set(name);
                merged.set(m);
                latch.countDown();
            }

            @Override
            public void onError(DownloadError code, String message) {
                latch.countDown();
            }
        });
        try {
            assertTrue(DownloadService.startDownload(ctx,
                    "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
                    "bestaudio/best", "audio"));
            assertTrue("3 分鐘內應完成", latch.await(180, TimeUnit.SECONDS));
            assertTrue("應轉檔成功 merged=true", merged.get());
            assertTrue(doneUri.get() != null);
            assertTrue("應為 mp3 檔：" + fileName.get(),
                    fileName.get().endsWith(".mp3"));
        } finally {
            DownloadService.setListener(null);
        }
    }
}
