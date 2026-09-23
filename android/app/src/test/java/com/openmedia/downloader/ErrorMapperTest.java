package com.openmedia.downloader;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ErrorMapperTest {
    @Test
    public void networkKeywords() {
        assertEquals(DownloadError.NETWORK,
                ErrorMapper.fromMessage("ERROR: Unable to resolve host: www.youtube.com"));
        assertEquals(DownloadError.NETWORK,
                ErrorMapper.fromMessage("HTTPSConnectionPool Read timed out"));
        assertEquals(DownloadError.NETWORK,
                ErrorMapper.fromMessage("UnknownHostException: x.com"));
    }

    @Test
    public void extractKeywords() {
        assertEquals(DownloadError.EXTRACT,
                ErrorMapper.fromMessage("ERROR: [youtube] aqz-KE-bpKQ: Private video"));
        assertEquals(DownloadError.EXTRACT,
                ErrorMapper.fromMessage("ERROR: Sign in to confirm you're not a bot"));
        assertEquals(DownloadError.EXTRACT,
                ErrorMapper.fromMessage("ERROR: Video unavailable"));
        assertEquals(DownloadError.EXTRACT,
                ErrorMapper.fromMessage("ERROR: Requested format not available"));
        assertEquals(DownloadError.EXTRACT,
                ErrorMapper.fromMessage(
                        "DownloadError: ERROR: [youtube] xxx: This video is unavailable"));
        // B 站中文案：需登入/大會員/付費牆一律 EXTRACT（07 逐站驗收）。
        assertEquals(DownloadError.EXTRACT,
                ErrorMapper.fromMessage("ERROR: [BiliBili] xxx: 需要登录后观看"));
        assertEquals(DownloadError.EXTRACT,
                ErrorMapper.fromMessage("ERROR: [BiliBili] xxx: 该视频需要大会员观看"));
        assertEquals(DownloadError.EXTRACT,
                ErrorMapper.fromMessage("ERROR: [Twitter] 1: Login required"));
        assertEquals(DownloadError.EXTRACT,
                ErrorMapper.fromMessage(
                        "ERROR: [twitter] 1: No video could be found in this tweet"));
    }

    @Test
    public void ffmpegFailureMapsToPostprocess() {
        assertEquals(DownloadError.POSTPROCESS,
                ErrorMapper.fromMessage("ERROR: Postprocessing: ffmpeg not found"));
    }

    @Test
    public void storageKeywords() {
        assertEquals(DownloadError.EXTRACT,
                ErrorMapper.fromMessage("分段下載被拒（HTTP 403）"));
        assertEquals(DownloadError.STORAGE,
                ErrorMapper.fromMessage("OSError: [Errno 28] No space left on device"));
        assertEquals(DownloadError.STORAGE,
                ErrorMapper.fromMessage("Permission denied: '/sdcard/x.mp4'"));
    }

    @Test
    public void unknownFallback() {
        assertEquals(DownloadError.UNKNOWN, ErrorMapper.fromMessage("weird new error"));
        assertEquals(DownloadError.UNKNOWN, ErrorMapper.fromMessage(null));
        assertEquals(DownloadError.UNKNOWN, ErrorMapper.fromMessage(""));
    }
}
