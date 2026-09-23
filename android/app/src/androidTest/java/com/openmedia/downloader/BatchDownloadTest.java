package com.openmedia.downloader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 整批驗收（ticket 06）：Google 官方測試清單（11 項，多為 1 分鐘短片，
 * 含下架項可順便看容錯），政策 144p 省流量。
 * 全跑完約數分鐘；需外網。
 */
@RunWith(AndroidJUnit4.class)
public class BatchDownloadTest {
    private static final String PLAYLIST =
            "https://www.youtube.com/playlist?list=PL59FEE129ADFF2B12";

    @Test
    public void scanListsItems() throws Exception {
        Context ctx = ApplicationProvider.getApplicationContext();
        PlaylistResult r = ResolveEngine.resolvePlaylist(ctx, PLAYLIST);
        assertTrue("應掃到多項", r.items.size() >= 5);
        assertTrue("上限內", r.items.size() <= PlaylistResult.MAX_ITEMS);
        assertTrue(r.items.get(0).url.startsWith("https://"));
    }

    @Test
    public void fullBatchDownloadsAll() throws Exception {
        Context ctx = ApplicationProvider.getApplicationContext();
        PlaylistResult list = ResolveEngine.resolvePlaylist(ctx, PLAYLIST);
        int total = list.items.size();

        CountDownLatch done = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger(-1);
        AtomicReference<List<BatchFailure>> failed = new AtomicReference<>();
        List<String> progress = new ArrayList<>();
        DownloadService.setListener(new DownloadService.Listener() {
            @Override
            public void onProgress(float percent, long etaSeconds, long speedBps) {
            }

            @Override
            public void onDone(Uri fileUri, String fileName, boolean merged) {
            }

            @Override
            public void onError(DownloadError code, String message) {
                failed.set(new ArrayList<>());
                done.countDown();
            }

            @Override
            public void onBatchProgress(int index, int totalItems, String itemTitle,
                                        float percent, long etaSeconds, long speedBps) {
                synchronized (progress) {
                    progress.add(index + "/" + totalItems + ":" + itemTitle);
                }
            }

            @Override
            public void onBatchDone(int ok, int totalItems, List<BatchFailure> fails) {
                succeeded.set(ok);
                failed.set(fails);
                done.countDown();
            }
        });

        // 直接跑 service 迴圈（mimic Plugin.downloadBatch 的參數組裝）。
        Context app = ctx.getApplicationContext();
        boolean started = DownloadService.startBatch(app, PLAYLIST, "video", 144, "{}");
        assertTrue("整批應啟動", started);
        assertTrue("整批超時", done.await(15, TimeUnit.MINUTES));
        // 3 支下架/私享片會失敗（被收集後繼續跑）；成功＋失敗＝總數才算對。
        StringBuilder codes = new StringBuilder();
        for (BatchFailure f : failed.get()) {
            codes.append(f.index).append(':').append(f.code).append(';');
        }
        android.util.Log.w("BatchTest", "failed=" + codes);
        assertEquals("成功＋失敗應等於總數", total, succeeded.get() + failed.get().size());
        assertTrue("應有成功項", succeeded.get() >= 5);
        for (BatchFailure f : failed.get()) {
            assertEquals("下架項應為 EXTRACT（可重試语义）",
                    DownloadError.EXTRACT, f.code);
        }
        assertTrue("應有逐項進度", progress.size() >= total);
        assertTrue("首項應為 The Google Story",
                progress.get(0).contains("The Google Story"));
    }
}
