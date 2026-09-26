# Gotchas — OpenMedia Downloader 避坑指南

Hard-won pitfalls。動到對應層之前先看這一節。

## Next.js × Capacitor

- Next.js 必須能靜態匯出給 WebView 載入。不要引入 Node server、API Route、
  SSR-only、middleware 動態功能；加新套件前先確認它能在 `output: 'export'` 下跑。
- 不要在 UI 層直接 `fetch` 影音檔 URL 或內嵌 yt-dlp 邏輯——一律走自訂 Plugin bridge。
  Web 上能跑的下載 code，在 WebView + 權限模型下通常是錯的。
- WebView 沒有桌面瀏覽器的下載/檔案行為：存檔、分享、開啟都要走原生端（MediaStore / FileProvider / Intent）。
- 安全區與手勢列：主要按鈕（下載/取消/重試）不可被 notch / 導覽列遮擋；用實機直式 + 橫式各看一次。

## 自訂 Plugin / Bridge

- TS 定義是唯一真相。改參數、改事件名、改錯誤碼時，TS + Android + UI 文案要一起改，不留「一邊舊」。
- 原生禁止跑在 UI thread；耗時工作（resolve / download / 轉碼）一律 background executor + progress event。
  用 polling 迴圈等進度視為 bug。
- Listener 記得清：頁面卸載 / 取消 / 完成時 remove listener，避免重複訂閱造成雙倍進度條。
- `cancel()` 必須是冪等的：連點取消、完成後再取消、重試前取消，都不能 crash。

## yt-dlp-android / Chaquopy / yt-dlp

- 舊 Gradio 範本的 `queue.Queue` / `threading` 那套不要直接搬進 Android。
  Android 端只認 Plugin 介面 + yt-dlp-android 的呼叫方式。
- Chaquopy / Python 只初始化一次。每次 download 都 init 會變慢 + 爆記憶體，視為 bug。
- yt-dlp-android、Chaquopy、yt-dlp 本體只做版本 pin，不 fork、不手改內部；升級要開獨立 ticket 並在實機重測全流程。
- 舊 notebook 的 `format_id` 白名單（571/628/…）是 YouTube 當下快照，會過期。
  新實作改用「解析度 + 檔案大小」語意去選 format，不要 hardcode format_id 陣列。

## FFmpeg

- FFmpeg 是選配：沒它要能下載（可能只有合併前狀態），有它才能合併影音 / 轉 mp3。
  UI 要分得出 `done` 和 `done（未合併）/ postprocess-failed`。
- 轉碼失敗要保留原檔 + 明確錯誤碼（`POSTPROCESS`），不要靜默刪檔。

## Android 儲存 / 權限 / 背景

- 遵守 Scoped Storage：用 MediaStore / app-specific 目錄，不寫死 `/sdcard/xxx`，
  不申請 `MANAGE_ALL_FILES` 除非有 ticket 明確要求。
- Android 10+、13+ 的媒體權限（`READ_MEDIA_*`）和通知權限行為不同；在 ticket 寫明測過的 API level。
- 背景下載會被系統暫停/殺掉：螢幕關閉、切換 App、省電模式都要測；進度要可恢復或可明確重試。
- 大檔 + playlist 會放大 APK 體積、磁碟、電量問題：做 zip / playlist 前先估上限並在 UI 擋（例如數量/總大小提示）。

## Gradle / 打包

- 一律經 `scripts/gradle.sh` 調 `android/gradlew`（Gradle Wrapper），不用系統全域 `gradle`；
  版本以 repo 內 `gradle-wrapper.properties` 為準；JS 側只用 `pnpm android:*`，不手打 gradlew。
- 順序固定：`pnpm build` → `pnpm cap:sync` → `pnpm android:test` → `pnpm android:build`
 （或一鍵 `pnpm build:apk`）。跳過 `cap:sync` 直接 build 看到的是舊 Web 產物，視為無效驗證。
- JDK 版本錯最常見：本專案預設 **Java 21**（Capacitor 8 建議；AGP 8.x 最低 17）。
  本機預設 `java` 是 21，若曾設 `JAVA_HOME` 指到舊版，先查 `java -version`。
  `scripts/gradle.sh` 會自動找 JDK 21（找不到才退回 17），不要硬改。
- SDK 已在專案本地（`.tools/android-sdk`，`scripts/gradle.sh` 自動指過去，不需 export）。
  若看到 ANDROID_HOME 報錯，先查是否有人刪了 `.tools/`，而不是重裝全域 SDK。
- 不要以 JS build / web test 綠了就說「可打包」；沒跑過 `build:apk` 就不說「可打包」。

## 32-bit / ABI 與體積

- yt-dlp-android 免費版只包 **arm64-v8a + x86_64**（無 32-bit）：armeabi-v7a 舊機裝了會閃退，
  要在 UI/上架說明寫清支援範圍，不要試圖手塞 32-bit so。
- 該 AAR 約 60–80MB：APK/AAB 體積暴漲是預期的，用 ABI split / AAB 壓，不是靠刪功能壓。
- 另起爐灶前先看：不要引入第二個 Python runtime、第二份 FFmpeg、第二套下載器。

## Git & Secrets

- `git status --short --ignored` 先看再動手；不要把不相關的進行中工作混入本次變更。
- 不讀不印 `.env*`、`.npmrc`、keystore、私鑰、tokens。簽章密碼絕不進 repo。
- 改 Plugin/原生後沒跑過 `cap sync` + Gradle build，就不說「可打包」。
