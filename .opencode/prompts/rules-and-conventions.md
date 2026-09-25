# Rules & Conventions — OpenMedia Downloader 強制性規範

你是本專案的 coding agent。這些是 HARD rules，每次變更都必須遵守。
若規則擋住你，停下來問，而不要繞過。

## 1. 專案定位

- 本專案是 **Android APK 專案**，不是純 Web 專案。
- UI 用 **Next.js** 開發，最終跑在 Android 手機 WebView 並包成 APK。
- `download_yt_video_or_audio.ipynb`（Gradio 範本）是**封存參考**：只用來理解舊行為，
  不再新增功能、不直接移植 Python/Gradio 程式碼。新功能以手機 UX 為準。
- 使用者-facing 說明用**繁體中文**，專有名詞（Capacitor、Plugin、yt-dlp 等）保持英文。

## 2. 架構與權責邊界（必讀）

```
┌─────────────────────────────────────┐
│ Next.js UI                          │  ← 你自己做
├─────────────────────────────────────┤
│ Capacitor                           │  ← 現成，不要重造
├─────────────────────────────────────┤
│ 自訂 YtDlp Capacitor Plugin         │  ← 你自己做（TS 介面 + Android 原生）
├─────────────────────────────────────┤
│ yt-dlp-android                      │  ← 現成（library），只做版本 pin + 呼叫
├─────────────────────────────────────┤
│ Chaquopy / CPython 3.13             │  ← library 已處理，不要動
├─────────────────────────────────────┤
│ yt-dlp                              │  ← library 已處理，不要動
├─────────────────────────────────────┤
│ FFmpeg                              │  ← 可整合（合併/轉碼用）
└─────────────────────────────────────┘
```

- **你自己做**：`Next.js UI`、自訂 Plugin 的 TS 定義 + Android 原生實作 + 兩側的錯誤/進度對齊。
- **現成**：Capacitor 核心、yt-dlp-android。只做 `sync / update / config`，不 fork、不改內部。
- **library 已處理**：Chaquopy、CPython、yt-dlp 本體。不要升級、不要 patch，除非有明確 ticket。
- **FFmpeg**：只有在需要合併影音 / 轉 mp3 時才接，保持可開關（fallback：無 FFmpeg 也能跑最低功能）。

## 3. Runtime 與工具鏈（pnpm 為 JS 入口，Gradle 為打包權威）

- JS 側唯一入口是根目錄 `package.json` 的 pnpm scripts，只用 `corepack pnpm`，
  不引入 npm/yarn，不新增第二份 lockfile（`pnpm-lock.yaml` 唯一）。
- 「測試 / 建置」的口徑固定如下，不自己發明別名：
  - `pnpm test` — JS 全測試（各 workspace 的 `test`，目前用 Vitest 方向；scaffold 前是 no-op）。
  - `pnpm build` — Web 靜態匯出（Next.js `output: 'export'` → `out/`），**不需要** Android SDK 也能跑。
  - `pnpm cap:sync` — `cap sync android`，把 `out/` 同步進 `android/`。
  - `pnpm android:test` — `testDebugUnitTest`（經 `scripts/gradle.sh`，統一 JDK/SDK 檢查）。
  - `pnpm android:build` — `assembleDebug`（同上）。
  - `pnpm build:apk` — `build → cap:sync → android:build` 一鍵鏈，**「可打包」只認這條鏈成功**。
  - `pnpm test:all` — `test + android:test`；CI 要綠就是這條 + `build:apk`。
- 一律走 `scripts/gradle.sh` 調 `android/gradlew`（Gradle Wrapper），不用系統全域 `gradle`，
  版本以 repo 內 `gradle-wrapper.properties` 為準。不以 `pnpm build` 通過就聲稱「可打包」。
- Android 驗證順序固定：`pnpm build` → `pnpm cap:sync` → `pnpm android:test` → `pnpm android:build`；
  跳過 `cap:sync` 直接 build 看到的是舊 Web 產物，視為無效驗證。
- 影響下載流程 → 還要 `pnpm android:connected` 或實機/emulator 走一次 resolve→download→cancel→retry。

## 3.1 版本基準（pin，升級要開獨立 ticket 並重測全流程）

| 項目 | 基準 | 備註 |
| --- | --- | --- |
| Node | 22+（`.node-version` 為準，現為 24） | Capacitor 8 要求 Node 22+ |
| pnpm | 11.x（`packageManager` pin） | 根 `package.json` 即契約 |
| Next.js / React | 16.x / 19（scaffold 時 pin minor） | `output: 'export'`，禁 API Route/SSR |
| Capacitor | 8.x（`@capacitor/cli ^8.5.1`） | 支援 API 24+；本專案 minSdk 29 相容 |
| JDK | **21** | Capacitor 8 建議值；AGP 8.x 最低 17；`scripts/gradle.sh` 會檢查 |
| AGP / Gradle | 8.13.0 / 8.14.3（wrapper，`cap add` 帶入） | Capacitor 8.5 配對；以 repo 內為準 |
| compileSdk / targetSdk | 36 | `variables.gradle`；Play 上架要求 |
| minSdk | 29 | 即 Android 10+（見 README〈Android 10+ 支援〉） |
| Chaquopy + CPython | 17.0.0 + Python 3.13 | 16.0 起支援 3.13；3.13 利於 16KB page 裝置 |
| AndroidX security-crypto | 1.1.0-alpha06（MasterKey Builder 要 1.1.x；stable 出了就跟進） | cookie session 加密存；minSdk 29 ≥ 所需 23 |
| yt-dlp-android | Maven Central `dev.ffmpegkit-maintained:yt-dlp-android:2.0.2` | 無 resolve API（只回 exit code）；免費版僅 arm64-v8a + x86_64 |
| yt-dlp | wheel overlay 2026.08.19（蓋掉 AAR 內建 2026.06.09，見 ticket 09） | AAR 無新版時的權宜；上游更新即重估移除 |
| FFmpeg | ffmpeg-kit audio 包 8.1.8（Maven Central，可開關） | bestaudio→mp3 自轉（libmp3lame）；缺席/失敗 fallback 留原檔 |

## 4. Next.js UI 規範

- Next.js 必須是 **Capacitor 可載入的靜態輸出**（`output: 'export'` 方向）：
  不依賴 Node server、API Route、SSR 動態功能、Gradio、Python。
- 所有下載能力只能走自訂 Plugin 介面，UI 層**禁止**直接 `fetch` 影音 URL、禁止內嵌 yt-dlp 呼叫。
- 行動優先：觸控目標 ≥ 44px、暗色/亮色可讀、安全區（notch/手勢列）不可遮擋主要按鈕。
- 狀態機要明確：`idle → resolving → downloading → postprocessing → done | error | cancelled`，
  每個狀態都要有對應 UI（進度%、速度、ETA、取消/重試）。
- 變更 UI 前先看最接近的既有 page/component，既有 pattern 優先，不為小元件引入新 UI library。

## 5. 自訂 YtDlp Plugin 規範

- TS 介面是 source of truth：`resolve() / download() / cancel() / getStatus()`（命名依實作調整，
  但一經確定就全 repo 一致），先定介面再寫 Android 原生。
- 原生一律跑在 background thread，禁止 block UI thread；進度用 event/listener 回傳，不用 polling 迴圈。
- Python/Chaquopy 初始化一次（singleton），重複 init 視為 bug。
- 錯誤要分級回傳：`NETWORK / EXTRACT / STORAGE / CANCELLED / POSTPROCESS / UNKNOWN`，
  UI 層據此顯示不同的重試/提示文案。
- 儲存遵守 Scoped Storage：用 MediaStore / app-specific 目錄，不寫死 `/sdcard/` 路徑，
  不要求不必要的 `MANAGE_ALL_FILES` 權限。

## 6. Git 與 Secrets

- 動到檔案前先看 `git status --short --ignored`；此 checkout 常有不相關的進行中工作，不要 revert、不要混入。
- 不讀、不印 `.env`、`.env.*`、`.npmrc`、keystore（`.jks`/`.keystore`）、`google-services.json`、
  cookies、tokens、私鑰。`.env.example` 只有 placeholder 時可讀。
- 簽章資訊（keystore 密碼、key alias）絕不進 repo、絕不貼在 log/issue。

## 7. 完成定義（DoD）

- 未跑過對應 checks 就不說「完成」：UI 改 → `pnpm build`；Plugin/原生改 → `pnpm cap:sync` + `pnpm android:build`；
  影響下載流程 → 還要在實機或 emulator 走一次 resolve→download→cancel→retry。
- 多檔或高衝擊變更先寫短版實作計畫，範圍變了就更新計畫。
- 不要為了小需求重寫整個視覺系統或引入新框架。
- 「可打包」一律指 `pnpm build:apk` 成功產出 APK，不以 `pnpm build` / `pnpm test` 成功代替。
