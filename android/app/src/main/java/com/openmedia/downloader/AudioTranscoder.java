package com.openmedia.downloader;

import java.io.File;

/**
 * 音檔轉碼接縫：bestaudio 下載檔 → mp3。失敗拋 POSTPROCESS 並附原檔（fallback 照存原檔）。
 * FFmpegKit 是靜態 API，直接寫單測會載入 native lib，所以隔一層 interface。
 */
public interface AudioTranscoder {
    File transcode(File input) throws DownloadException;

    /** 輸出檔名推導（純函式，可單測）：base.mp4 → base.mp3。 */
    static File outputFor(File input) {
        String name = input.getName();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return new File(input.getParent(), base + ".mp3");
    }
}
