package com.openmedia.downloader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class PlaylistResultTest {
    private static String fixture(String name) throws Exception {
        try (InputStream in = PlaylistResultTest.class.getClassLoader()
                .getResourceAsStream(name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    public void detectsPlaylistJson() throws Exception {
        assertTrue(PlaylistResult.isPlaylistJson(fixture("playlist-flat-sample.json")));
        assertFalse(PlaylistResult.isPlaylistJson(fixture("resolve-sample.json")));
    }

    @Test
    public void parsesFlatEntries() throws Exception {
        PlaylistResult r = PlaylistResult.parse(fixture("playlist-flat-sample.json"));
        assertEquals("PL59FEE129ADFF2B12", r.playlistId);
        assertTrue(r.title.contains("Test Playlist"));
        assertEquals(4, r.items.size());
        PlaylistItem first = r.items.get(0);
        assertEquals("EjN5avRvApk", first.videoId);
        assertEquals("The Google Story", first.title);
        assertEquals("https://www.youtube.com/watch?v=EjN5avRvApk", first.url);
        assertEquals(133, first.durationSec);
    }

    @Test
    public void skipsEntriesWithoutIdOrUrl() throws Exception {
        // 第 4 筆是下架/私享（title/duration 皆 NA）：id+url 還在就留，標題退回 id。
        PlaylistResult r = PlaylistResult.parse(fixture("playlist-flat-sample.json"));
        PlaylistItem na = r.items.get(3);
        assertEquals("yi50KlsCBio", na.videoId);
        assertEquals("yi50KlsCBio", na.title);
    }

    @Test
    public void rejectsOversizeList() throws Exception {
        StringBuilder entries = new StringBuilder("[");
        for (int i = 0; i < PlaylistResult.MAX_ITEMS + 1; i++) {
            if (i > 0) {
                entries.append(',');
            }
            entries.append("{\"id\":\"v").append(i)
                    .append("\",\"url\":\"https://www.youtube.com/watch?v=v")
                    .append(i).append("\"}");
        }
        entries.append(']');
        String json = "{\"_type\":\"playlist\",\"id\":\"PLx\",\"title\":\"T\",\"entries\":"
                + entries + "}";
        try {
            PlaylistResult.parse(json);
            assertTrue("超過上限應拒絕", false);
        } catch (ResolveException e) {
            assertEquals(DownloadError.EXTRACT, e.getCode());
        }
    }
}
