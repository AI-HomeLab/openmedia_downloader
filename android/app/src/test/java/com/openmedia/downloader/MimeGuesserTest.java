package com.openmedia.downloader;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MimeGuesserTest {
    @Test
    public void audioAndVideo() {
        assertEquals("audio/mpeg", YtDlpPlugin.MimeGuesser.guess("content://x/1.mp3"));
        assertEquals("audio/mp4", YtDlpPlugin.MimeGuesser.guess("content://x/2.m4a"));
        assertEquals("video/webm", YtDlpPlugin.MimeGuesser.guess("content://x/3.webm"));
        assertEquals("video/mp4", YtDlpPlugin.MimeGuesser.guess("content://x/4.mp4"));
    }

    @Test
    public void queryStringDoesNotFoolIt() {
        assertEquals("video/mp4",
                YtDlpPlugin.MimeGuesser.guess("content://x/5.mp4?download=1.mp3"));
    }

    @Test
    public void unknownDefaultsToMp4() {
        assertEquals("video/mp4", YtDlpPlugin.MimeGuesser.guess("content://x/6"));
    }
}
