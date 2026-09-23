# 15 — 評估：直引 Chaquopy，丟掉 yt-dlp-android AAR

**What to build:** 評估案（不保證執行）。現狀：AAR 只剩 Chaquopy＋Python 殼的價值
（`YtDlp.execute` 無人調、內建 yt-dlp 被 overlay 蓋掉），佔 APK 60–80MB。
評估 app 模組直套 Chaquopy Gradle plugin＋pip 裝 yt-dlp 的可行性、體積收益、
風險（單 module 限制、Python 版對齊、overlay 機制存廢）。

**Blocked by:** 無.

**Status:** ready-for-agent

- [ ] 產出一頁評估（做法、體積差、風險、回退方案），不寫 production code
- [ ] 決策：做／不做；若做，另開實作 ticket（本票只評估）
