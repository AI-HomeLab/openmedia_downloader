package com.openmedia.downloader;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 播放清單掃描結果（yt-dlp flat extract：entries 只有 id/標題/連結/時長）。
 * 解析純函式，可在 JVM 單測。
 */
public class PlaylistResult {
    /** 整批上限：flat 不知道大小，用數量擋（ticket 06 決策）。 */
    public static final int MAX_ITEMS = 50;

    public final String playlistId;
    public final String title;
    public final List<PlaylistItem> items;

    public PlaylistResult(String playlistId, String title, List<PlaylistItem> items) {
        this.playlistId = playlistId;
        this.title = title;
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
    }

    /** yt-dlp `_type == "playlist"` 即清單；單片是 `"video"`。 */
    public static boolean isPlaylistJson(String json) {
        try {
            return "playlist".equals(new JSONObject(json).optString("_type", ""));
        } catch (JSONException e) {
            return false;
        }
    }

    /**
     * 解析 flat extract。無 id/url 的條目（下架/私享到連 id 都沒有）跳過；
     * 有 id 但無標題的退回 id。超過 MAX_ITEMS 拋 EXTRACT（UI 擋）。
     */
    public static PlaylistResult parse(String json) throws ResolveException {
        try {
            JSONObject o = new JSONObject(json);
            List<PlaylistItem> items = new ArrayList<>();
            JSONArray arr = o.optJSONArray("entries");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject e = arr.optJSONObject(i);
                    if (e == null) {
                        continue;
                    }
                    String id = strOrNull(e, "id", "");
                    String url = strOrNull(e, "url", "");
                    if (id.isEmpty() || url.isEmpty()) {
                        continue;
                    }
                    String title = strOrNull(e, "title", "");
                    if (title.isEmpty()) {
                        title = id;
                    }
                    items.add(new PlaylistItem(id, title, url, e.optLong("duration", -1)));
                }
            }
            if (items.size() > MAX_ITEMS) {
                throw new ResolveException(DownloadError.EXTRACT,
                        "清單太長（" + items.size() + " 項，最多 " + MAX_ITEMS + " 項）");
            }
            return new PlaylistResult(
                    o.optString("id", ""),
                    o.optString("title", ""),
                    items);
        } catch (JSONException e) {
            throw new ResolveException(DownloadError.UNKNOWN, "清單回傳無法解析", e);
        }
    }

    private static String strOrNull(JSONObject o, String key, String fallback) {
        if (!o.has(key) || o.isNull(key)) {
            return fallback;
        }
        String s = o.optString(key, fallback);
        return s == null ? fallback : s;
    }
}
