package com.openmedia.downloader;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * cookiefile 暫存檔（ticket cookie-login/02）：解密內容寫到 app-specific
 * 快取，路徑餵給 yt-dlp `cookiefile` 參數，呼叫方用完即刪，不在磁碟留痕。
 * 檔名隨機（不含站點資訊）；內容絕不 Log。
 */
public final class CookieFiles {
    private CookieFiles() {
    }

    /**
     * @return 有可用 cookie（已存＋開關開）就回傳暫存檔，否則 null。
     * 開關關了或沒存都視為「不用 cookie」，不是錯誤。
     */
    public static File prepare(Context context, String extractor) throws DownloadException {
        if (extractor == null || extractor.isEmpty() || !CookieStore.isKnownExtractor(extractor)) {
            return null;
        }
        CookieStore store;
        try {
            store = new CookieStore(context.getApplicationContext());
        } catch (Exception e) {
            throw new DownloadException(DownloadError.STORAGE, "安全儲存不可用", e);
        }
        if (!store.has(extractor) || !store.isEnabled(extractor)) {
            return null;
        }
        String text = store.load(extractor);
        if (text == null || text.isEmpty()) {
            return null;
        }
        File dir = new File(context.getCacheDir(), "cookies");
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new DownloadException(DownloadError.STORAGE, "建暫存目錄失敗");
        }
        final File out;
        try {
            out = File.createTempFile("cj-", null, dir);
        } catch (Exception e) {
            throw new DownloadException(DownloadError.STORAGE, "建暫存檔失敗", e);
        }
        try (FileOutputStream fos = new FileOutputStream(out)) {
            fos.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            out.delete();
            throw new DownloadException(DownloadError.STORAGE, "寫暫存檔失敗", e);
        }
        return out;
    }

    /** 用完即刪；刪不掉也不要炸（快取目錄系統會清）。 */
    public static void dispose(File f) {
        if (f != null) {
            try {
                f.delete();
            } catch (Exception e) {
                // ignore
            }
        }
    }
}
