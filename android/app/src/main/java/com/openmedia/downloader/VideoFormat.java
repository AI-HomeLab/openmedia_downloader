package com.openmedia.downloader;

import org.json.JSONObject;

/**
 * yt-dlp `--dump-single-json` 某一項 format。只取 UI 選畫質需要的欄位，
 * 不透出 format_id 給 UI（禁 hardcode，見 gotchas）。
 */
public class VideoFormat {
    public final String formatId;
    public final String ext;
    public final int width;
    public final int height;
    public final long filesize;
    public final String vcodec;
    public final String acodec;
    public final String note;
    /** 直連（native 管線下載用；永不進 UI，UI 只拿 index）。 */
    public final String url;

    public VideoFormat(String formatId, String ext, int width, int height,
                       long filesize, String vcodec, String acodec, String note,
                       String url) {
        this.formatId = formatId;
        this.ext = ext;
        this.width = width;
        this.height = height;
        this.filesize = filesize;
        this.vcodec = vcodec;
        this.acodec = acodec;
        this.note = note;
        this.url = url;
    }

    static VideoFormat fromJson(JSONObject o) {
        long size = o.optLong("filesize", -1);
        if (size < 0) {
            size = o.optLong("filesize_approx", -1);
        }
        return new VideoFormat(
                strOrNull(o, "format_id", ""),
                strOrNull(o, "ext", ""),
                o.optInt("width", 0),
                o.optInt("height", 0),
                size,
                strOrNull(o, "vcodec", null),
                strOrNull(o, "acodec", null),
                strOrNull(o, "format_note", ""),
                strOrNull(o, "url", null));
    }

    /** 明確 null / 缺鍵一律回 fallback（org.json 對 null 的預設行為不穩定， sober 處理）。 */
    private static String strOrNull(JSONObject o, String key, String fallback) {
        if (!o.has(key) || o.isNull(key)) {
            return fallback;
        }
        String s = o.optString(key, null);
        return s == null ? fallback : s;
    }

    public boolean hasVideo() {
        if (vcodec != null && !vcodec.equals("none")) {
            return true;
        }
        // 直連單檔常不標 vcodec（null）：影片容器一律視為有影像。
        // storyboard 等非影片會明確標 vcodec=none，不受影響。
        return vcodec == null && isVideoContainer(ext);
    }

    public boolean hasAudio() {
        if (acodec != null && !acodec.equals("none")) {
            return true;
        }
        // 單檔容器多半自帶聲音；未知時假設有（04 音檔模式再精確處理）。
        return acodec == null && hasVideo();
    }

    private static boolean isVideoContainer(String ext) {
        return "mp4".equals(ext) || "webm".equals(ext) || "mkv".equals(ext)
                || "mov".equals(ext) || "flv".equals(ext);
    }

    /** 人類可讀標籤，例如 "1080p mp4"；永遠不回 formatId（禁 hardcode）。 */
    public String displayLabel() {
        if (height > 0) {
            return height + "p" + (ext.isEmpty() ? "" : " " + ext);
        }
        if (!note.isEmpty()) {
            return note + (ext.isEmpty() ? "" : " " + ext);
        }
        return ext.isEmpty() ? "其他格式" : ext;
    }
}
