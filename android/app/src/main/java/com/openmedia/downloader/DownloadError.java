package com.openmedia.downloader;

/** 下載錯誤分級：與 Plugin TS 介面 + UI 文案共用同一組代碼（見 rules §5）。 */
public enum DownloadError {
    NETWORK,
    EXTRACT,
    STORAGE,
    CANCELLED,
    POSTPROCESS,
    UNKNOWN;

    /** 未知字串一律收斂為 UNKNOWN，不拋例外。 */
    public static DownloadError fromName(String name) {
        if (name == null) {
            return UNKNOWN;
        }
        try {
            return DownloadError.valueOf(name);
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
