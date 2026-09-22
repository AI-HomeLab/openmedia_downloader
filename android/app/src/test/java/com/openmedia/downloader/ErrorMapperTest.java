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
    }

    @Test
    public void storageKeywords() {
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
