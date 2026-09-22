package com.openmedia.downloader;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DownloadErrorTest {
    @Test
    public void knownCodesRoundTrip() {
        assertEquals(DownloadError.NETWORK, DownloadError.fromName("NETWORK"));
        assertEquals(DownloadError.CANCELLED, DownloadError.fromName("CANCELLED"));
    }

    @Test
    public void unknownConvergesToUnknown() {
        assertEquals(DownloadError.UNKNOWN, DownloadError.fromName("SOMETHING_NEW"));
        assertEquals(DownloadError.UNKNOWN, DownloadError.fromName(null));
        assertEquals(DownloadError.UNKNOWN, DownloadError.fromName(""));
    }
}
