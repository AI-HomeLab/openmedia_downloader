package com.openmedia.downloader;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.ReturnCode;

import java.io.File;

/** FFmpegKit 實作：audio 包帶 libmp3lame。同步執行，呼叫方必須在 bg thread。 */
public class FFmpegAudioTranscoder implements AudioTranscoder {
    @Override
    public File transcode(File input) throws DownloadException {
        File output = AudioTranscoder.outputFor(input);
        // 經 temp 檔（副檔名保持 .mp3，ffmpeg 靠它判格式）：
        // 輸入本身已是 .mp3 時 output 路徑等於 input，不能先刪。
        File tmp = new File(output.getParent(), output.getName() + ".tmp.mp3");
        if (tmp.exists()) {
            tmp.delete();
        }
        FFmpegSession session = FFmpegKit.execute(
                "-i \"" + input.getAbsolutePath() + "\" -y -codec:a libmp3lame -q:a 4 \""
                        + tmp.getAbsolutePath() + "\"");
        if (ReturnCode.isSuccess(session.getReturnCode()) && tmp.exists()) {
            input.delete();
            if (tmp.renameTo(output)) {
                return output;
            }
            // 改名失敗：原檔已刪，只能誠實回報無 partial（不再拿已刪的充數）。
            tmp.delete();
            throw new DownloadException(DownloadError.POSTPROCESS, "轉檔搬移失敗", null, null);
        }
        String fail = session.getFailStackTrace();
        tmp.delete();
        throw new DownloadException(DownloadError.POSTPROCESS,
                "轉檔失敗" + (fail == null ? "" : fail), input, null);
    }
}
