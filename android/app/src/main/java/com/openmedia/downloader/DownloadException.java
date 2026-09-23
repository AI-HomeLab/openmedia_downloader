package com.openmedia.downloader;

/** 下載失敗時拋出，帶全 repo 統一的分級錯誤碼（見 rules §5）。 */
public class DownloadException extends Exception {
    private final DownloadError code;
    /** POSTPROCESS 等情境下保留的原檔（可為 null）；上層決定存不存。 */
    private final java.io.File partialFile;

    public DownloadException(DownloadError code, String message) {
        this(code, message, null, null);
    }

    public DownloadException(DownloadError code, String message, Throwable cause) {
        this(code, message, null, cause);
    }

    public DownloadException(DownloadError code, String message,
                             java.io.File partialFile, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.partialFile = partialFile;
    }

    public DownloadError getCode() {
        return code;
    }

    public java.io.File getPartialFile() {
        return partialFile;
    }
}
