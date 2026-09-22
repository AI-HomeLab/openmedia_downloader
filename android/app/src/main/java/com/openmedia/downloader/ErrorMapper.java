package com.openmedia.downloader;

import java.util.Locale;

/**
 * 把底層失敗收斂為全 repo 六碼。純函式、無 Android 依賴，可在 JVM 單元測試。
 * 規則：網路關鍵字 → NETWORK；需登入/私人/刪除/站方改版 → EXTRACT；
 * 其他一律 UNKNOWN（由上層配文案，不在這裡猜）。
 */
public final class ErrorMapper {
    private ErrorMapper() {
    }

    public static DownloadError fromMessage(String message) {
        if (message == null) {
            return DownloadError.UNKNOWN;
        }
        String m = message.toLowerCase(Locale.US);
        if (containsAny(m, "unknownhost", "connectexception", "sockettimeout",
                "ssl", "connection reset", "connection refused", "timeout", "timed out",
                "unable to resolve host", "network is unreachable", "ehostunreach")) {
            return DownloadError.NETWORK;
        }
        if (containsAny(m, "no space left", "enospc", "permission denied",
                "eacces", "read-only file system")) {
            return DownloadError.STORAGE;
        }
        if (containsAny(m, "private video", "login required", "sign in to confirm",
                "log in", "cookies", "age gate", "age-verification",
                "video unavailable", "has been removed", "has been deleted",
                "not available in your country", "requested format not available",
                "unsupported url", "no video formats found", "unable to extract",
                "http error 403", "http error 429", "forbidden")) {
            return DownloadError.EXTRACT;
        }
        return DownloadError.UNKNOWN;
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String n : needles) {
            if (haystack.contains(n)) {
                return true;
            }
        }
        return false;
    }
}
