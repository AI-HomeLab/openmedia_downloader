package com.openmedia.downloader;

/**
 * 取消控制流標記（非錯誤）：進度回呼拋出它來中斷同步下載。
 * 會穿過 Chaquopy bridge，可能以原樣或包在 YtDlpException cause 鏈裡回來，
 * Downloader 兩種都認得並原樣重拋，不包成 DownloadException。
 */
public class DownloadCancelled extends RuntimeException {
    public DownloadCancelled() {
        super("cancelled");
    }
}
