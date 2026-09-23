package com.openmedia.downloader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class ResolveResultTest {
    private static String fixture(String name) throws Exception {
        try (InputStream in = ResolveResultTest.class.getClassLoader()
                .getResourceAsStream(name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    public void parsesRealDumpSingleJson() throws Exception {
        // 真實 yt-dlp --dump-single-json 輸出（Big Buck Bunny），非手造。
        ResolveResult r = ResolveResult.parse(fixture("resolve-sample.json"));
        assertEquals("aqz-KE-bpKQ", r.videoId);
        assertTrue(r.title.contains("Big Buck Bunny"));
        assertEquals(53, r.formats.size());
        assertTrue(r.hasPlayableVideo());
    }

    @Test
    public void formatLabelsAreHumanReadable() throws Exception {
        ResolveResult r = ResolveResult.parse(fixture("resolve-sample.json"));
        boolean sawHeightLabel = false;
        for (VideoFormat f : r.formats) {
            if (f.hasVideo() && f.displayLabel().matches("\\d+p.*")) {
                sawHeightLabel = true;
                break;
            }
        }
        assertTrue(sawHeightLabel);
    }

    @Test
    public void videoOptionsAreDedupedAndSorted() throws Exception {
        ResolveResult r = ResolveResult.parse(fixture("resolve-sample.json"));
        java.util.List<VideoFormat> opts = r.videoOptions();
        assertTrue(!opts.isEmpty());
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < opts.size(); i++) {
            if (i > 0) {
                assertTrue(opts.get(i - 1).height >= opts.get(i).height);
            }
            // 同（高度、容器）不重複
            assertTrue(seen.add(opts.get(i).height + "/" + opts.get(i).ext));
        }
    }

    @Test
    public void genericSingleFileHasNoDimensionsButIsPlayable() throws Exception {
        // 直連單檔（generic extractor）：無 width/height、vcodec null，但可播且假設有聲。
        String json = "{\"id\":\"x\",\"title\":\"t\","
                + "\"formats\":[{\"format_id\":\"mp4\",\"ext\":\"mp4\","
                + "\"vcodec\":null,\"acodec\":null}]}";
        ResolveResult r = ResolveResult.parse(json);
        assertTrue(r.hasPlayableVideo());
        assertEquals(1, r.videoOptions().size());
        assertTrue(r.videoOptions().get(0).hasAudio());
    }
}
