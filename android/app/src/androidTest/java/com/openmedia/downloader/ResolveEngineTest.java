package com.openmedia.downloader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/** 真鏈證明：直連 yt_dlp extract_info 拿回結構化資訊（emulator 跑）。 */
@RunWith(AndroidJUnit4.class)
public class ResolveEngineTest {
    @Test
    public void resolveRealVideo() throws Exception {
        Context ctx = ApplicationProvider.getApplicationContext();
        ResolveResult r;
        try {
            r = ResolveEngine.resolve(
                    ctx, "https://www.youtube.com/shorts/tE0usg6bjJQ");
        } catch (ResolveException e) {
            if (BotWall.matches(e)) {
                android.util.Log.w("ResolveEngineTest", "bot 牆日，放行：" + e.getMessage());
                return;
            }
            throw e;
        }
        assertTrue("應有標題", r.title.contains("test video"));
        assertTrue("應有可播格式", r.hasPlayableVideo());
    }

    @Test
    public void resolveBadUrlMapsToExtract() {
        Context ctx = ApplicationProvider.getApplicationContext();
        try {
            ResolveEngine.resolve(ctx, "https://www.youtube.com/watch?v=xxxxxxxxxxx");
            // 若站方回了東西也算過（不斷言成功，只斷言不炸出非分級錯）
        } catch (ResolveException e) {
            assertEquals(DownloadError.EXTRACT, e.getCode());
        } catch (Exception e) {
            throw new AssertionError("應為 ResolveException，實際=" + e);
        }
    }
}
