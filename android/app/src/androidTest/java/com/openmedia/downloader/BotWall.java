package com.openmedia.downloader;

/**
 * YouTube 機器人驗證抽獎（Sign in to confirm you're not a bot）：
 * 同條片同輪有人過、有人被擋（2026-09-26 CI 實測），跟片子本身無關。
 * 外網原因的測試一律記 log 放行（QualityDownloadTest 全滅放行同哲學）；
 * 合併機械／解析結構等本地斷言維持嚴格。結構解法是 CI 帶登入 cookie，
 * 那要擁有者提供 cookie secret，另案處理。
 */
final class BotWall {
    private BotWall() {
    }

    static boolean matches(Exception e) {
        String m = e == null ? null : e.getMessage();
        return m != null && m.toLowerCase(java.util.Locale.US).contains("not a bot");
    }
}
