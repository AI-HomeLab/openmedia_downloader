package com.openmedia.downloader;

/** 下載失敗時拋出，帶全 repo 統一的分級錯誤碼（見 rules §5）。 */
public class DownloadException extends Exception {
    private final DownloadError code;

    public DownloadException(DownloadError code, String message) {
        super(message);
        this.code = code;
    }

    public DownloadException(DownloadError code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public DownloadError getCode() {
        return code;
    }
}
