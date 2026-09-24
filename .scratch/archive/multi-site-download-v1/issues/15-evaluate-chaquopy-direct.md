# 15 — 評估：直引 Chaquopy，丟掉 yt-dlp-android AAR

**What to build:** 評估案（不保證執行）。現狀：AAR 只剩 Chaquopy＋Python 殼的價值
（`YtDlp.execute` 無人調、內建 yt-dlp 被 overlay 蓋掉），佔 APK 60–80MB。
評估 app 模組直套 Chaquopy Gradle plugin＋pip 裝 yt-dlp 的可行性、體積收益、
風險（單 module 限制、Python 版對齊、overlay 機制存廢）。

**Blocked by:** 無.

**Status:** completed（評估完成，決策：不做）

## 現況測量（2026-09-24）
- AAR 本體僅 22.8MB（`yt-dlp-android-2.0.2.aar`），不是傳說的 60–80MB；
  APK 98MB 的大頭是 ffmpeg-kit-audio native＋雙 ABI（arm64/x86_64 各一份）。
- AAR 內容（未壓縮）：`requirements-common.imy` 5.7MB（含被 overlay 蓋掉的舊 yt-dlp）、
  `libpython3.13.so` ×2＝10.7MB、`stdlib` 7.2MB、crypto/ssl/sqlite、chaquopy runtime。
- 我方只用 AAR 的 `YtDlp.init()`＋`YtDlpException`（各一處）；`YtDlp.execute` 無人調。

## 做法（若要做）
app 模組套 `com.chaquo.python` 17.0.0＋`pip { install "yt-dlp==2026.8.19" }`，
`YtDlp.init` 換成 `Python.start(AndroidPlatform)` 數行；overlay wheel 刪除，
版號改 pin 在 build.gradle。

## 體積差
- 省下：舊 yt-dlp requirements（5.7MB）＋YtDlp Java 殼（<1MB）。
- 加回：Chaquopy 直引帶來的同一套 Python/stdlib/runtime（等量）＋pip 的 yt-dlp（≈wheel 3MB）。
- **淨省約 6MB**，且 ffmpeg-kit 大頭完全不動。

## 風險
1. 離線可重現性倒退：pip 在 build 期下載，CI/新機器沒網就掛；現狀全 vendored（AAR＋wheel 進版控）是賣點。
2. QA 成本：換直譯器交付路徑要重跑 resolve→download→merge 全矩陣，只省 6MB 不值。
3. 新失敗面：buildPython 版本、pip 解析、plugin 與 Capacitor/Gradle 互動（AAR 方案這些都由上游測過）。

## 回退方案
保留 `implementation 'dev.ffmpegkit-maintained:yt-dlp-android:2.0.2'` 一行即回退；
overlay 機制不受影響（sys.path 插入照樣蓋得掉任何來源的舊版）。

## 決策：不做
6MB 不值得賠掉離線构建＋重測成本。APK 真要瘦，先走 Play AAB＋ABI split
（用戶端直接少一半），那是零風險的打包選項。
