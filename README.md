# OpenMedia Downloader

基於 **Next.js + Capacitor** 開發、打包為 **Android APK** 的影音下載 App。
UI 跑在手機 WebView，下載能力經由**自訂 YtDlp Capacitor Plugin** 呼叫
**yt-dlp-android**（Chaquopy / CPython 3.13 + yt-dlp），需要時再用 FFmpeg 合併/轉檔。

> 舊版 `download_yt_video_or_audio.ipynb`（Gradio 範本）為**封存參考**，
> 只用來理解舊行為，不再新增功能，不直接移植 Python/Gradio 程式碼。

## 架構

```
┌─────────────────────────────────────┐
│ Next.js UI                          │  ← 你自己做
├─────────────────────────────────────┤
│ Capacitor                           │  ← 現成
├─────────────────────────────────────┤
│ 自訂 YtDlp Capacitor Plugin         │  ← 你自己做
├─────────────────────────────────────┤
│ yt-dlp-android                      │  ← 現成
├─────────────────────────────────────┤
│ Chaquopy / CPython 3.13             │  ← library 已處理
├─────────────────────────────────────┤
│ yt-dlp                              │  ← library 已處理
├─────────────────────────────────────┤
│ FFmpeg                              │  ← 可整合
└─────────────────────────────────────┘
```

| 層 | 來源 | 說明 |
| --- | --- | --- |
| Next.js UI | 自研 | 唯一的 UI 層，靜態輸出給 WebView 載入；下載一律走 Plugin bridge |
| Capacitor | 現成 | 只做 `sync / update / config`，不重造、不 fork |
| 自訂 YtDlp Plugin | 自研 | TS 介面（source of truth）+ Android 原生實作，負責 resolve/download/cancel/status、進度事件、錯誤分級 |
| yt-dlp-android | 現成 | 只做版本 pin + 呼叫，不改內部 |
| Chaquopy / CPython 3.13、yt-dlp | library 已處理 | 不要升級、不要 patch，初始化一次（singleton） |
| FFmpeg | 可整合 | 合併影音 / 轉 mp3 用，可開關；無 FFmpeg 也要有最低可用功能 |

下載狀態機：`idle → resolving → downloading → postprocessing → done | error | cancelled`，
每個狀態都有對應 UI（進度%、速度、ETA、取消/重試）。

## 功能範圍（以手機 UX 為準）

- 單一影片 / 播放清單連結解析（resolve）
- 下載影片（解析度語意選擇，不 hardcode YouTube `format_id`）/ 下載音檔（mp3）
- 進度顯示、取消（冪等）、重試、錯誤分級提示
- 儲存到 MediaStore / app-specific 目錄（Scoped Storage），playlist/zip 要有數量與大小上限提示
- 詳細行為以 `.scratch/<feature>/spec.md` 為準，notebook 只做歷史對照

## 環境需求

| 工具 | 基準 | 備註 |
| --- | --- | --- |
| Node | 22+（`.node-version` 為準，現為 24） | Capacitor 8 要求 Node 22+ |
| pnpm | 11.x（`packageManager` pin，只用 `corepack pnpm`） | `pnpm-lock.yaml` 唯一，不混用 npm/yarn |
| JDK | **21** | Capacitor 8 建議值；AGP 8.x 最低 17（本機系統 JDK 21 直用） |
| Android SDK | **專案本地 `.tools/android-sdk`**（build-tools 35.0.0 + platform-35 + platform-tools） | 已裝好！`source .tools/env.sh` 或直接跑 `pnpm android:*`（`scripts/gradle.sh` 會自動指過去）；不需裝 Android Studio |
| AGP / Gradle | 8.7.3 / 8.11.1（`gradle-wrapper.properties` 為準） | 只走 `scripts/gradle.sh`，不用系統 gradle |
| 實機 / emulator | Android 10+、13+、15 各一台（或映像） | 見〈Android 10+ 支援〉 |

完整版本基準見 `.opencode/prompts/rules-and-conventions.md §3.1`（升級要開獨立 ticket）。

## 指令怎麼用（根 `package.json` 即契約）

```bash
corepack pnpm install      # 唯一安裝入口

pnpm dev                   # Web UI 開發（scaffold 後指向 apps/web）
pnpm build                 # Web 靜態匯出 → out/（不需要 Android SDK）
pnpm test                  # JS 全測試（Vitest 方向；scaffold 前是 no-op）
pnpm typecheck / pnpm lint # 有什麼跑什麼（--if-present）

pnpm cap:sync              # cap sync android：把 out/ 同步進 android/
pnpm android:test          # testDebugUnitTest（自動檢查 JDK 21 + SDK）
pnpm android:build         # assembleDebug
pnpm android:connected     # connectedAndroidTest（要接實機/emulator）

pnpm build:apk             # 一鍵鏈：build → cap:sync → android:build
pnpm test:all              # test + android:test
```

> 為什麼 `build` 不直接產 APK？`pnpm build` 是純 Web 產物，沒 SDK 也能跑，
> 讓只改 UI 的人不用裝 Android 環境；**「可打包」只認 `pnpm build:apk` 成功**。
> CI 要綠 = `pnpm test:all` + `pnpm build:apk`。

## 快速開始

```bash
# 0. 確認工具鏈
node -v                    # 22+
pnpm -v                    # 11.x

# 新機器第一件事：一鍵裝本地工具鏈（JDK 21 + SDK，冪等，已有會跳過）
pnpm setup
# 只需 curl/unzip/tar + Linux x86_64；全裝在 .tools/（不進版控）

# 1. 安裝 + Web 建置（不需要 Android SDK 也能跑）
corepack pnpm install
pnpm build

# 2. 同步進 Android（每次改 UI 必跑，否則看到舊產物）
pnpm cap:sync

# 3. Android 單元測試 + 打包（需要 SDK；本機沒裝會直接報錯）
pnpm android:test
pnpm android:build
# 產物：android/app/build/outputs/apk/debug/app-debug.apk

# 或一鍵：pnpm build:apk
```

## 專案結構（目標）

```
.
├── package.json / pnpm-workspace.yaml / pnpm-lock.yaml  # pnpm 契約（test/build/apk 唯一入口）
├── .node-version           # Node pin（現為 24）
├── scripts/gradle.sh       # Gradle 統一入口（JDK/SDK 檢查 + 調 android/gradlew）
├── .tools/                 # 專案本地工具（SDK、JDK 21、Gradle 快取；不進版控）
├── android/                # Gradle 骨架（pre-Capacitor：可跑 test/assemble；`cap add` 後補 BridgeActivity）
├── apps/web/               # Next.js UI（靜態輸出，output: 'export' 方向；待 scaffold）
├── plugins/ytdlp/          # 自訂 Plugin：TS 定義 + Android 原生實作（待 scaffold）
├── capacitor.config.ts     # Capacitor 設定（webDir 指向 out/；待 scaffold）
├── docs/ / guides/         # 補充文件（roadmap、驗收記錄）
├── .scratch/               # spec + tickets（依 dev-workflow）
├── .opencode/prompts/      # 本專案的 rules / workflow / gotchas
├── download_yt_video_or_audio.ipynb  # 封存參考（Gradio 範本，不再改）
└── README.md
```

實際目錄名以建置時的 repo 為準，結構漂移時優先更新本節。

## Android 10+ 支援

`minSdk 29`，即 Android 10（含）以上。選 29 而不是 Capacitor 最低的 24，理由：

- **Scoped Storage（API 29 起強制）**：29 允許 `requestLegacyExternalStorage` 過渡，
  30+ 完全移除。直接以 29 為底，全版本統一走 MediaStore / app-specific 目錄，
  不寫過渡期髒 code。
- **權限分水嶺一次處理**：API 33+ 媒體權限改 `READ_MEDIA_VIDEO/AUDIO`、
  通知要 `POST_NOTIFICATIONS`；API 34+ 前景服務要宣告類型。targetSdk 35 下這些全是強制行為，
  minSdk 29 讓相容層只需覆蓋 29→35。
- **64-bit 全覆蓋**：yt-dlp-android 免費版只包 arm64-v8a + x86_64。
  Android 10+ 裝置幾乎全 64-bit；32-bit（armeabi-v7a）舊機明確不支援，要在上架說明寫清。
- **16KB page**：Android 15+（API 35）裝置要求 16KB page 相容；
  Chaquopy 配 CPython 3.13 即為此（3.13+ 相容性最佳）。

驗收矩陣（至少）：API 29 實機或映像（儲存行為）+ API 33（新媒體/通知權限）+
API 35（targetSdk、16KB）。ticket 要寫明測過的 API level。

## 開發流程

- Web UI：`pnpm build`，再 `pnpm cap:sync`，最後一定要跑 Gradle build 才算數。
- Plugin/原生：`pnpm cap:sync` + `pnpm android:test` + `pnpm android:build`。
- 下載鏈：`pnpm android:connected` 或實機/emulator，走一次 `resolve → download → cancel → retry`，並記錄裝置型號 / API level。
- 完整六階段管線（grilling → to-spec → to-tickets → implement&tdd → code-review → archive）
  見 `.opencode/prompts/dev-workflow.md`；規範見 `rules-and-conventions.md`，避坑見 `gotchas.md`。
- `opencode.jsonc` 已掛載上述三份提示詞，不需另行指定。

## Plugin 介面約定

- TS 定義是唯一真相，建議形狀（命名定案後全 repo 一致）：`resolve() / download() / cancel() / getStatus()`。
- 原生跑 background thread，進度用 event/listener 回傳，不 polling。
- 錯誤分級：`NETWORK / EXTRACT / STORAGE / CANCELLED / POSTPROCESS / UNKNOWN`，
  UI 據此顯示重試/提示文案。
- `cancel()` 冪等；頁面卸載/完成/取消時移除 listener。

## yt-dlp / FFmpeg 版本策略

- `yt-dlp-android`、Chaquopy、yt-dlp 只 pin 版本，不 fork、不手改；升級開獨立 ticket 並重測全流程。
- 不要把 notebook 的 `format_id` 白名單照搬進 App，改用解析度 + 檔案大小語意選 format。
- FFmpeg 缺席時仍可下載；轉碼失敗保留原檔並回 `POSTPROCESS`，UI 要區分 `done` 與 `done（未合併）`。

## 打包與簽章（`pnpm build:apk` 為唯一口徑）

```bash
pnpm build                   # 先產 Web 靜態檔（免 SDK）
pnpm cap:sync                # 同步進 android/
pnpm android:test            # 先測（testDebugUnitTest）
pnpm android:build           # debug APK（可打包 = 這步成功）
pnpm android:release         # release（需簽章）
pnpm android:connected       # 有真機/emulator 且動到下載鏈時加跑

# 一鍵：pnpm build:apk（= 上面 build→sync→build 全鏈）
```

- keystore（`.jks`/`.keystore`）、密碼、key alias **絕不進 repo**，也不貼在 log/issue。
- 權限遵守 Scoped Storage，不寫死 `/sdcard/`，不申請非必要的 `MANAGE_ALL_FILES`。

## 疑難排解

| 症狀 | 先查 |
| --- | --- |
| 白屏 / 404 | Next.js 是否 `output: 'export'`、Capacitor `webDir` 是否指向輸出目錄、有無用到 API Route/SSR |
| 下載沒反應 | 是否繞過 Plugin 直接 `fetch`、listener 是否重複訂閱/未清除 |
| 變慢 / OOM | Chaquopy 是否重複 init、是否在 UI thread 跑下載 |
| 合併失敗 | FFmpeg 是否缺席、有無保留原檔並回 `POSTPROCESS` |
| 存檔找不到 / 權限拒絕 | 是否寫死路徑、有無用 MediaStore/app-specific、測過的 API level（29/33/35 行為不同） |
| Gradle 失敗 | `java -version` 是否 21、是否經 `scripts/gradle.sh`、有無跑過 `cap:sync` |
| `pnpm android:*` 直接報錯 | `ANDROID_HOME` 是否存在（沒裝 SDK 是預期的，先裝 SDK Platform 35） |

## 合規提醒

僅下載你有權利保存的內容。YouTube 等平台的 ToS 可能限制下載，請遵守當地法規與平台條款，
本專案不提供規避 DRM/付費牆的功能。

## 授權

（尚未定案時保留本節，定案後填入，例如 MIT / Apache-2.0，並確認 yt-dlp-android、Chaquopy、
FFmpeg 各自授權相容。）
