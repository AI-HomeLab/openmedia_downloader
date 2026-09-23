# 10 — 刪除過期探針測試

**What to build:** 刪掉結論已被取代的早期探針測試，只留現行管線的驗收測試，
避免後人誤讀過時結論。

**Blocked by:** 無.

**Status:** completed

- [x] 刪除 `NativeFetchProbeTest.java`（「原生抓全滅」結論已被分段管線＋overlay 推翻）
- [x] 檢查 `QualityDownloadTest.java`、`AudioDownloadTest.java`、`ServiceAudioTest.java`、
  現行行為相關，全留；`PlayerClientProbeTest.java` 早已不存在
- [x] `pnpm test:all` 綠＋`compileDebugAndroidTestJavaWithJavac` 綠＋測試 APK 無殘留
  （註：`capacitor-cordova-android-plugins` 的 duplicate-classes flake 持續中，
  用 `-x` 該模組 check task 繞過；純刪除變更，免雙軸 review）
