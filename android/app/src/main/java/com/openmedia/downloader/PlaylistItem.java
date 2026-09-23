package com.openmedia.downloader;

/**
 * 播放清單 flat 條目（id/標題/連結/時長，不含格式）。
 * 下載時逐項再走完整 resolve＋政策選畫質（見 Downloader.pickByPolicy）。
 */
public class PlaylistItem {
    public final String videoId;
    public final String title;
    public final String url;
    public final long durationSec;

    public PlaylistItem(String videoId, String title, String url, long durationSec) {
        this.videoId = videoId;
        this.title = title;
        this.url = url;
        this.durationSec = durationSec;
    }
}
