package com.openmedia.downloader;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 下載檔 → 系統 Downloads（MediaStore.Downloads，minSdk 29，
 * 自有檔案免額外權限）。回傳 content Uri，開啟直接用它。
 */
public final class MediaStoreSaver {
    private MediaStoreSaver() {
    }

    public static Uri save(Context context, File srcFile) throws DownloadException {
        String mime = mimeOf(srcFile.getName());
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, srcFile.getName());
        values.put(MediaStore.Downloads.MIME_TYPE, mime);
        values.put(MediaStore.Downloads.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/OpenMedia");
        values.put(MediaStore.Downloads.IS_PENDING, 1);

        ContentResolver resolver = context.getContentResolver();
        Uri collection = MediaStore.Downloads.getContentUri(
                MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri item = resolver.insert(collection, values);
        if (item == null) {
            throw new DownloadException(DownloadError.STORAGE, "寫入 Downloads 失敗");
        }
        try (InputStream in = new FileInputStream(srcFile);
             OutputStream out = resolver.openOutputStream(item)) {
            if (out == null) {
                throw new FileNotFoundException(item.toString());
            }
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        } catch (Exception e) {
            resolver.delete(item, null, null);
            throw new DownloadException(DownloadError.STORAGE, "寫入 Downloads 失敗", e);
        }
        values.clear();
        values.put(MediaStore.Downloads.IS_PENDING, 0);
        resolver.update(item, values, null, null);
        return item;
    }

    static String mimeOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            String mime = MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(name.substring(dot + 1).toLowerCase());
            if (mime != null) {
                return mime;
            }
        }
        return "application/octet-stream";
    }

    /** content Uri → 顯示用檔名（完成頁顯示）。 */
    public static String displayName(Context context, Uri uri) {
        try {
            ContentUris.parseId(uri);
            try (android.database.Cursor c = context.getContentResolver().query(
                    uri, new String[]{MediaStore.Downloads.DISPLAY_NAME},
                    null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    return c.getString(0);
                }
            }
        } catch (Exception ignored) {
            // 非 content Uri（mock/測試）→ 掉到底下用 path 推斷
        }
        String segment = uri.getLastPathSegment();
        return segment == null ? uri.toString() : segment;
    }
}
