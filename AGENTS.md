# AGENTS.md — OpenMedia Downloader

Android APK（Next.js UI → Capacitor WebView → 自訂 YtDlp Plugin → yt-dlp-android）。
產品說明看 README（解決什麼／架構／依賴／侷限／授權）；規範由 `opencode.jsonc`
載入 `.opencode/prompts/`（rules / workflow / gotchas）。
這裡只收錄沒寫進那三份、agent 容易踩的實作層事實。

## 指令（唯一入口，不自己發明）

- JS 只用 `corepack pnpm`；`pnpm-lock.yaml` 唯一，不混 npm/yarn。
- `pnpm setup` — 新機器一鍵裝本地工具鏈（JDK 21 + SDK，冪等）。先跑它再跑別的。
- `pnpm test`（Vitest）/ `pnpm build`（靜態匯出）是真的；`pnpm test:all`＝test＋android:test。
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
- API 29 模擬器的系統 WebView 凍結在 Chrome 74，不支援 `?.` 等語法：
  看到的是靜態預渲染頁，React onClick 全死（typing/label 正常），Maestro 點按無效。
  真機 WebView 經 Play 更新不受影響。結論：e2e 只跑 35，29 只跑免點擊 connected。
- E2E 用 Maestro（`pnpm e2e` 跑 `scripts/e2e.sh`：清場→全流→MediaStore 落地斷言，
  認文字不認座標；WebView 內容透得出來已驗證）。
  CLI 裝 `~/.maestro`（不進版控）；flow 在 `e2e/*.yaml`，共用前綴在 `e2e/_common/`
  （子流需自帶 `appId`；`maestro test e2e/` 不會跑 `_common/`）；跑之前先 up 模擬器＋裝 APK。
  坑：tapOn 是 regex 全比對（含中文 pattern 必 miss，用精確全文）；
  a11y 樹剪 fold 下節點（CTA 用 sticky 保可點，不在 flow 裡 scroll 再點）。
- `/dev/kvm` 掉權限（`user` 不在有效 groups）且 sudo 要密碼時，
  用 `sg kvm -c 'bash scripts/emulator.sh up 35'` 開模擬器；看到 member 在
  `/etc/group` 卻無效是 session 太舊，重登或 sg 即可。
- `capacitor-cordova-android-plugins:checkDebugAndroidTestDuplicateClasses`
  偶發 kotlin-stdlib duplicate 失敗：重跑，仍壞就加
  `-x :capacitor-cordova-android-plugins:connectedDebugAndroidTest`。
- 迴圈裡調 `adb shell` 必須 `< /dev/null`：adb 會吃掉迴圈的 stdin，
  沒加的話第一個 adb 之後迴圈直接 EOF（`scripts/e2e.sh` 實例）。
- 版本 pin（AGP 8.13.0 / Gradle 8.14.3 / compile+target 36 / minSdk 29 / Capacitor 8，
  皆由 `cap add` 帶入，以 repo 內為準）
  見 rules §3.1，升級要開獨立 ticket。

## Repo 現況與邊界

- 自研：`apps/web/`（UI＋`src/lib/ytdlp.ts` 介面）、`android/app/.../downloader/`
  （Plugin＋下載管線＋Service）。只 pin 不改：Capacitor、yt-dlp-android
  （`ffmpegkit-maintained`，免費版僅 arm64-v8a + x86_64）、
  Chaquopy/CPython/yt-dlp 本體（AAR 內建版太舊，用 `assets/ytdlp/` wheel 疊加蓋掉，
  見 `.scratch/archive/multi-site-download-v1/issues/09-ytdlp-overlay-upgrade.md`）。
  FFmpeg 可開關，缺席要有 fallback。
- `download_yt_video_or_audio.ipynb` 是封存參考；`format_id` 白名單已過期，改用解析度語意選 format。
- 錯誤碼全 repo 一致：`NETWORK / EXTRACT / STORAGE / CANCELLED / POSTPROCESS / UNKNOWN`。
- 授權：Unlicense（見 `UNLICENSE`＋README 授權節）；第三方元件授權各歸各，散佈前自行確認。

## 紅線

- 不讀不印 `.env*`、keystore、私鑰、tokens；簽章資訊絕不進 repo。
- 動檔前 `git status --short --ignored`；此 checkout 常有不相關進行中工作，不 revert、不混入。
- UI 禁止直接 `fetch` 影音 URL 或內嵌 yt-dlp，一律走 Plugin bridge；原生禁止跑 UI thread，
  進度用 event 不用 polling；`cancel()` 冪等。
- 使用者-facing 說明用繁體中文，專有名詞保持英文。
