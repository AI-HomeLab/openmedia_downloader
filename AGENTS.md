# AGENTS.md — OpenMedia Downloader

Android APK 專案（Next.js UI → Capacitor WebView → 自訂 YtDlp Plugin → yt-dlp-android）。
詳細規範由 `opencode.jsonc` 載入 `.opencode/prompts/`（rules / workflow / gotchas）；
這裡只收錄沒寫進那三份、agent 容易踩的實作層事實。

## 指令（唯一入口，不自己發明）

- JS 只用 `corepack pnpm`；`pnpm-lock.yaml` 唯一，不混 npm/yarn。
- `pnpm setup` — 新機器一鍵裝本地工具鏈（JDK 21 + SDK，冪等）。先跑它再跑別的。
- `pnpm test` / `pnpm build` — 目前是 no-op（`apps/web` 尚未 scaffold）；變綠不代表任何事。
- `pnpm cap:sync` → `pnpm android:test` → `pnpm android:build`，順序固定；
  `pnpm build:apk` 是一鍵全鏈，「可打包」只認它。
- Gradle 一律經 `pnpm android:*`（背後是 `scripts/gradle.sh`），不手打 `gradlew`，
  不用系統全域 `gradle`。`gradle.sh` 會 `cd android/` 再跑——在 repo 根目錄直接打
  `./android/gradlew` 會因 cwd 錯誤失敗，這是已知坑不是 bug。

## 工具鏈現況（已驗證，別重裝）

- SDK 在 `.tools/android-sdk`（build-tools 35.0.0 + platform-35/36），`gradle.sh` 自動指過去，
  不需 export、不需裝 Android Studio。看到 ANDROID_HOME 報錯先查 `.tools/` 還在不在。
- JDK 用 `.tools/jdk-21`（symlink，Temurin 完整 JDK）。**系統 `/usr/lib/jvm/java-21-*` 是 JRE，
  沒有 javac**，`gradle.sh` 會跳過它；`JAVA_HOME` 被指到舊版時先清掉再查。
- `GRADLE_USER_HOME` 預設收進 `.tools/.gradle`；`.tools/` 全不進版控（見 `.gitignore`）。
- 模擬器走 `scripts/emulator.sh`（up 29|33|35、down、status），不用裝 Android Studio；
  要 KVM（`sudo gpasswd -a $USER kvm` 後重登）；`connectedAndroidTest` 是腳本測試主力，
  adb 點按只做手動補充。AVD 家目錄在 `.tools/.android`，不進版控。
- 版本 pin（AGP 8.13.0 / Gradle 8.14.3 / compile+target 36 / minSdk 29 / Capacitor 8，
  皆由 `cap add` 帶入，以 repo 內為準）
  見 rules §3.1，升級要開獨立 ticket。

## Repo 現況與邊界

- `android/` 是 pre-Capacitor 骨架：`MainActivity` 是佔位，`cap add` 後換 BridgeActivity；
  `apps/web/`、`plugins/ytdlp/`、`capacitor.config.ts` 都還沒建。`cap:sync` 在 web 產物出來前會失敗，是預期的。
- 能動：Next.js UI、自訂 Plugin 的 TS + Android 原生、兩側錯誤/進度對齊。
  只 pin 不改：Capacitor、yt-dlp-android（`ffmpegkit-maintained`，免費版僅 arm64-v8a + x86_64）、
  Chaquopy/CPython/yt-dlp 本體。FFmpeg 可開關，缺席要有 fallback。
- `download_yt_video_or_audio.ipynb` 是封存參考；`format_id` 白名單已過期，改用解析度語意選 format。
- 錯誤碼全 repo 一致：`NETWORK / EXTRACT / STORAGE / CANCELLED / POSTPROCESS / UNKNOWN`
 （Android 側已有 `DownloadError.java` 對應實作 + 測試）。

## 紅線

- 不讀不印 `.env*`、keystore、私鑰、tokens；簽章資訊絕不進 repo。
- 動檔前 `git status --short --ignored`；此 checkout 常有不相關進行中工作，不 revert、不混入。
- UI 禁止直接 `fetch` 影音 URL 或內嵌 yt-dlp，一律走 Plugin bridge；原生禁止跑 UI thread，
  進度用 event 不用 polling；`cancel()` 冪等。
- 使用者-facing 說明用繁體中文，專有名詞保持英文。
