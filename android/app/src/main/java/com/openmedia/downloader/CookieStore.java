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

    /**
     * 各站 cookie 檔應含的網域（存檔錯站防線：X 的檔貼進 YouTube 格就擋下）。
     * YouTube 匯出常混 google.com，X 混 twitter.com——都算該站的。
     */
    static final java.util.Map<String, String[]> SITE_DOMAINS =
            new java.util.HashMap<String, String[]>() {{
                put(YOUTUBE, new String[]{"youtube.com", "google.com"});
                put(BILIBILI, new String[]{"bilibili.com"});
                put(TWITTER, new String[]{"x.com", "twitter.com"});
            }};

    /**
     * 內容是否含該站網域（Tab 第一欄比對，大小寫＋前導點正規化）。
     * 混合多站匯出也放行（有該站的就行）；純函式，可單測。
     */
    public static boolean matchesSite(String text, String extractor) {
        String[] want = SITE_DOMAINS.get(extractor);
        if (want == null || text == null) {
            return false;
        }
        for (String line : text.split("[\\r\\n]+")) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) {
                continue;
            }
            String[] parts = t.split("\t", -1);
            if (parts.length < 7) {
                continue;
            }
            String d = parts[0].trim().toLowerCase(Locale.US);
            if (d.startsWith(".")) {
                d = d.substring(1);
            }
            for (String w : want) {
                if (d.equals(w) || d.endsWith("." + w)) {
                    return true;
                }
            }
        }
        return false;
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
            // 去 BOM：有些匯出工具存成 UTF-8 with BOM，trim 去不掉 \uFEFF。
            String t = line.replace("\uFEFF", "").trim();
            if (!t.isEmpty()) {
                header = t;
                break;
            }
        }
        // Netscape 變體檔頭都接受（"Get cookies.txt" 等工具只寫 HTTP Cookie File）。
        if (!header.contains("Cookie File")) {
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
        throw new DownloadException(DownloadError.EXTRACT,
                "沒有有效的 cookie 條目（欄位要用 Tab 分隔；從聊天軟體複製容易把 Tab 洗成空格）");
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
        // 一站一檔：內容沒有該站網域就是貼錯格（存了也永遠用不到，還佔著位置）。
        if (!matchesSite(text, extractor)) {
            throw new DownloadException(DownloadError.EXTRACT,
                    "這份看起來不是 " + displayName(extractor) + " 的 cookie（沒有該站網域），請確認匯出來源");
        }
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
