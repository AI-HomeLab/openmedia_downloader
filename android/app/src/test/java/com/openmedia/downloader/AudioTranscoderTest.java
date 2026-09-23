package com.openmedia.downloader;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.File;

public class AudioTranscoderTest {
    @Test
    public void outputNameDerivation() {
        assertEquals(new File("/a/base.mp3"),
                AudioTranscoder.outputFor(new File("/a/base.m4a")));
        assertEquals(new File("/a/base.mp3"),
                AudioTranscoder.outputFor(new File("/a/base.mp4")));
        assertEquals(new File("/a/base.mp3"),
                AudioTranscoder.outputFor(new File("/a/base")));
    }
}
