package com.openmedia.downloader;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 站點 session cookie 存取（ticket cookie-login/01）。
 * EncryptedSharedPreferences（AES256-GCM，AndroidKeyStore 管 key）；
 * key 丟失（換機／清資料）時建庫即炸，呼叫方視為未設定。
 * 絕不 Log 內容；callee 傳輸見 02（解密→暫存→用完即刪）。
 */
public class CookieStore {
    public static final String YOUTUBE = "youtube";
    public static final String BILIBILI = "bilibili";
    public static final String TWITTER = "twitter";
    public static final String[] EXTRACTORS = {YOUTUBE, BILIBILI, TWITTER};

    private static final String PREFS_FILE = "cookies";

    private final SharedPreferences prefs;

    public CookieStore(Context context) throws Exception {
        MasterKey key = new MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build();
        prefs = EncryptedSharedPreferences.create(
                context,
                PREFS_FILE,
                key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
    }

    private static String textKey(String extractor) {
        return "cookie." + extractor;
    }

    private static String enabledKey(String extractor) {
        return "enabled." + extractor;
    }

    public static boolean isKnownExtractor(String extractor) {
        for (String e : EXTRACTORS) {
            if (e.equals(extractor)) {
                return true;
            }
        }
        return false;
    }

    /**
     * URL→extractor（ticket 02：選 cookie 前先知道是哪站）。
     * 比 host（equals／.字尾），不用子字串——`x.com` 會誤中 `linux.com`，
     * 送錯站的 session 是憑證外送事故。純函式，可單測。
     */
    public static String extractorForUrl(String url) {
        String host;
        try {
            host = new java.net.URI(url == null ? "" : url).getHost();
        } catch (Exception e) {
            return "";
        }
        if (host == null) {
            return "";
        }
        host = host.toLowerCase(Locale.US);
        if (host.equals("youtube.com") || host.endsWith(".youtube.com")
                || host.equals("youtu.be")) {
            return YOUTUBE;
        }
        if (host.equals("bilibili.com") || host.endsWith(".bilibili.com")
                || host.equals("b23.tv")) {
            return BILIBILI;
        }
        if (host.equals("x.com") || host.endsWith(".x.com")
                || host.equals("twitter.com") || host.endsWith(".twitter.com")) {
            return TWITTER;
        }
        return "";
    }

    public static String displayName(String extractor) {
        if (YOUTUBE.equals(extractor)) {
            return "YouTube";
        }
        if (BILIBILI.equals(extractor)) {
            return "Bilibili";
        }
        if (TWITTER.equals(extractor)) {
            return "X";
        }
        return extractor;
    }

    /**
     * Netscape cookies.txt 校驗（純函式）：檔頭＋至少一行有效條目（7 欄以上）。
     * 不合格拋 EXTRACT（UI 擋下並說明，不送原生深處）。
     */
    public static void validate(String text) throws DownloadException {
        if (text == null || text.trim().isEmpty()) {
            throw new DownloadException(DownloadError.EXTRACT, "cookie 是空的");
        }
        String[] lines = text.split("[\\r\\n]+");
        String header = "";
        for (String line : lines) {
            if (!line.trim().isEmpty()) {
                header = line;
                break;
            }
        }
        if (!header.contains("Netscape HTTP Cookie File")) {
            throw new DownloadException(DownloadError.EXTRACT, "不是 cookies.txt 格式");
        }
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) {
                continue;
            }
            if (t.split("\t", -1).length >= 7) {
                return;
            }
        }
        throw new DownloadException(DownloadError.EXTRACT, "沒有有效的 cookie 條目");
    }

    /** 去重網域數（UI 狀態顯示用；前導點去掉）。純函式。 */
    public static int domainCount(String text) {
        Set<String> domains = new HashSet<>();
        if (text == null) {
            return 0;
        }
        for (String line : text.split("[\\r\\n]+")) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) {
                continue;
            }
            String[] parts = t.split("\t", -1);
            if (parts.length >= 7 && !parts[0].trim().isEmpty()) {
                String d = parts[0].trim().toLowerCase(Locale.US);
                if (d.startsWith(".")) {
                    d = d.substring(1);
                }
                domains.add(d);
            }
        }
        return domains.size();
    }

    public void save(String extractor, String text) throws DownloadException {
        if (!isKnownExtractor(extractor)) {
            throw new DownloadException(DownloadError.UNKNOWN, "不支援的站點");
        }
        validate(text);
        try {
            prefs.edit().putString(textKey(extractor), text).apply();
        } catch (Exception e) {
            throw new DownloadException(DownloadError.STORAGE, "cookie 存檔失敗", e);
        }
    }

    public String load(String extractor) {
        return prefs.getString(textKey(extractor), null);
    }

    public boolean has(String extractor) {
        return prefs.contains(textKey(extractor));
    }

    public void clear(String extractor) {
        // 清除連開關一起重置：重貼後預設啟用，不沿用舊開關。
        prefs.edit().remove(textKey(extractor)).remove(enabledKey(extractor)).apply();
    }

    public void setEnabled(String extractor, boolean enabled) {
        prefs.edit().putBoolean(enabledKey(extractor), enabled).apply();
    }

    public boolean isEnabled(String extractor) {
        return prefs.getBoolean(enabledKey(extractor), true);
    }
}
