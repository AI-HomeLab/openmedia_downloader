package com.openmedia.downloader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.media.MediaExtractor;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

/**
 * YouTube 分離抓取＋合併：無聲影像＋音軌分開抓→ffmpeg 合併，
 * 驗 merged 檔裡真有 audio 軌。
 *
 * 前提：YtDlpEngine 的 wheel overlay（AAR 內建 yt-dlp 2026.06.09 太舊，
 * YouTube 直連全 403；overlay 見 YtDlpEngine.OVERLAY_WHEEL）。
 * 需網路＋YouTube 可達；跑外網的 connected test，CI 無外網時可略過。
 */
@RunWith(AndroidJUnit4.class)
public class YoutubeSplitMergeTest {
    // 自家 Shorts（3 秒）：有 DASH 分離式，取最小的無聲 mp4 驗合併。
    private static final String VIDEO =
            "https://www.youtube.com/shorts/tE0usg6bjJQ";

    @Test
    public void youtubeSplitFetchAndMerge() throws Exception {
        Context ctx = ApplicationProvider.getApplicationContext();
        YtDlpEngine.initOnce(ctx);
        String ver = com.chaquo.python.Python.getInstance()
                .getModule("yt_dlp.version").get("__version__")
                .toJava(String.class);
        // overlay 必須生效，否則舊版 yt-dlp 的 YouTube 直連全 403。
        assertEquals("2026.08.19", ver);

        ResolveResult r = ResolveEngine.resolve(ctx, VIDEO);
        assertTrue("解析無可用畫質", r.hasPlayableVideo());

        // 走跟 UI 同一路：videoOptions()（去重後清單）裡最小的無聲 mp4，
        // 用它的 formatId 當 download(format)——跟 Plugin index→formatId 同義。
        VideoFormat noAudio = null;
        for (VideoFormat f : r.videoOptions()) {
            if (!f.hasAudio() && "mp4".equals(f.ext)
                    && (noAudio == null || f.height < noAudio.height)) {
                noAudio = f;
            }
        }
        if (noAudio == null) {
            fail("此片無分離式影像格式，換片");
        }

        File outDir = new File(ctx.getCacheDir(), "splitmerge");
        File merged = Downloader.download(ctx, VIDEO, outDir,
                noAudio.formatId, "video", null);
        assertTrue("合併檔不存在", merged.isFile() && merged.length() > 0);
        assertTrue("合併檔無 audio 軌", hasAudioTrack(merged));
    }

    private static boolean hasAudioTrack(File f) {
        MediaExtractor ex = new MediaExtractor();
        try {
            ex.setDataSource(f.getAbsolutePath());
            for (int i = 0; i < ex.getTrackCount(); i++) {
                String mime = ex.getTrackFormat(i)
                        .getString(android.media.MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        } finally {
            ex.release();
        }
    }
}
