package com.openmedia.downloader;

/**
 * 整批單項失敗記錄（ticket 06）：記下繼續跑，最後一次回報，
 * UI 逐項重試走既有單下路徑。
 */
public class BatchFailure {
    public final int index;
    public final String videoId;
    public final String title;
    public final DownloadError code;
    public final String message;

    public BatchFailure(int index, String videoId, String title,
                        DownloadError code, String message) {
        this.index = index;
        this.videoId = videoId;
        this.title = title;
        this.code = code;
        this.message = message;
    }
}
