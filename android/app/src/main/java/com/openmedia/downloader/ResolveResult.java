package com.openmedia.downloader;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * resolve 結果：影片資訊 + 實際可用格式清單。解析純函式，可在 JVM 單測。
 */
public class ResolveResult {
    public final String videoId;
    public final String title;
    public final String uploader;
    public final long durationSec;
    public final List<VideoFormat> formats;
    /** 解析後的正規頁面 URL（短連結展開後；B 站 CDN 認它當 Referer）。 */
    public final String webpageUrl;

    public ResolveResult(String videoId, String title, String uploader,
                         long durationSec, List<VideoFormat> formats) {
        this(videoId, title, uploader, durationSec, formats, "");
    }

    public ResolveResult(String videoId, String title, String uploader,
                         long durationSec, List<VideoFormat> formats, String webpageUrl) {
        this.videoId = videoId;
        this.title = title;
        this.uploader = uploader;
        this.durationSec = durationSec;
        this.formats = Collections.unmodifiableList(new ArrayList<>(formats));
        this.webpageUrl = webpageUrl == null ? "" : webpageUrl;
    }

    /** 解析 yt-dlp `--dump-single-json` 的 stdout。失敗拋 UNKNOWN。 */
    public static ResolveResult parse(String json) throws ResolveException {
        try {
            JSONObject o = new JSONObject(json);
            List<VideoFormat> formats = new ArrayList<>();
            JSONArray arr = o.optJSONArray("formats");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject f = arr.optJSONObject(i);
                    if (f != null) {
                        formats.add(VideoFormat.fromJson(f));
                    }
                }
            }
            return new ResolveResult(
                    o.optString("id", ""),
                    o.optString("title", ""),
                    o.optString("uploader", ""),
                    o.optLong("duration", -1),
                    formats,
                    o.optString("webpage_url", ""));
        } catch (JSONException e) {
            throw new ResolveException(DownloadError.UNKNOWN, "resolve 回傳無法解析", e);
        }
    }

    /** 至少一個含影像的格式才算可下載影片。 */
    public boolean hasPlayableVideo() {
        for (VideoFormat f : formats) {
            if (f.hasVideo()) {
                return true;
            }
        }
        return false;
    }

    /** 最佳純音軌（無則退回第一個含聲格式；都沒有回 null）。合併時找伴用。 */
    public VideoFormat bestAudio() {
        VideoFormat fallback = null;
        for (VideoFormat f : formats) {
            if (!f.hasAudio()) {
                continue;
            }
            if (!f.hasVideo()) {
                return f;
            }
            if (fallback == null) {
                fallback = f;
            }
        }
        return fallback;
    }

    /**
     * UI 畫質清單：只留含影像格式，按高度降序、同（高度、容器）留檔案最大者；
     * 無尺寸資訊（直連單檔）排最後但保留——它們通常是免合併單檔。
     * UI 只吃回傳的 index + label，不碰 formatId（禁 hardcode）。
     */
    public List<VideoFormat> videoOptions() {
        List<VideoFormat> videos = new ArrayList<>();
        for (VideoFormat f : formats) {
            if (f.hasVideo()) {
                videos.add(f);
            }
        }
        videos.sort((a, b) -> {
            int c = Integer.compare(b.height, a.height);
            if (c != 0) {
                return c;
            }
            return Long.compare(b.filesize, a.filesize);
        });
        List<VideoFormat> deduped = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (VideoFormat f : videos) {
            // 同（高度、容器）只留第一個＝該組檔案最大者（已按大小排好）
            if (seen.add(f.height + "/" + f.ext)) {
                deduped.add(f);
            }
        }
        return deduped;
    }
}
