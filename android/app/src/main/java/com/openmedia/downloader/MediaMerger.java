package com.openmedia.downloader;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.ReturnCode;

import java.io.File;

/**
 * 影音合併：分離式 video＋audio → 單一 mp4（`-c copy` 不重編碼，秒合）。
 * 同步執行，呼叫方必須在 bg thread。失敗拋 POSTPROCESS 並附已落地分段。
 */
public final class MediaMerger {
    private MediaMerger() {
    }

    public static File merge(File videoPart, File audioPart, File outputDir, String baseName)
            throws DownloadException {
        File output = new File(outputDir, baseName + ".mp4");
        if (output.exists()) {
            output.delete();
        }
        FFmpegSession session = FFmpegKit.execute("-i \"" + videoPart.getAbsolutePath()
                + "\" -i \"" + audioPart.getAbsolutePath() + "\" -y -c copy \""
                + output.getAbsolutePath() + "\"");
        if (ReturnCode.isSuccess(session.getReturnCode()) && output.exists()
                && output.length() > 0) {
            videoPart.delete();
            audioPart.delete();
            return output;
        }
        String fail = session.getFailStackTrace();
        if (output.exists()) {
            output.delete();
        }
        throw new DownloadException(DownloadError.POSTPROCESS,
                "合併失敗" + (fail == null ? "" : fail), videoPart, null);
    }
}
