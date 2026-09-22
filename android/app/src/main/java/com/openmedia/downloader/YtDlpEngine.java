package com.openmedia.downloader;

import android.content.Context;

import dev.ffmpegkit_maintained.ytdlp.YtDlp;
import dev.ffmpegkit_maintained.ytdlp.YtDlpException;

/**
 * library 初始化單例：YtDlp.init（內含 Chaquopy/Python 啟動）全進程只跑一次。
 * 重複 init 視為 bug，上層不應依賴多次呼叫。
 */
public final class YtDlpEngine {
    private static volatile boolean initialized = false;

    private YtDlpEngine() {
    }

    public static synchronized void initOnce(Context context) throws DownloadException {
        if (initialized) {
            return;
        }
        try {
            YtDlp.init(context.getApplicationContext());
            initialized = true;
        } catch (YtDlpException e) {
            throw new DownloadException(
                    ErrorMapper.fromMessage(e.getMessage()), "init 失敗", e);
        }
    }

    public static boolean isInitialized() {
        return initialized;
    }
}
