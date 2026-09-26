# OpenMedia

## 1. 解決什麼問題？

想在 Android 手機上把 YouTube、X、Bilibili 的公開影片或聲音存下來離線看，
不用開電腦、不用接一堆轉檔工具。這個 App 就是做這件事的：
**貼連結 → 選畫質 → 下載 → 存到手機 Downloads**，單片、整批清單、轉 mp3 都包。

起點是桌機跑的一個 Gradio 範本（檔案已移除），第一版把它重做成手機原生體驗的 APK。

## 2. 如何解決？用了哪些技術？

核心想法只有一句：**解析跟下載分開，髒活全在原生端做，UI 只負責顯示**。

- **Next.js UI** 跑在手機 WebView（靜態輸出，無 server）。UI 禁止碰影音 URL，
  所有下載能力一律走自訂 Plugin bridge。
- **自訂 YtDlp Capacitor Plugin**（TS 介面是唯一真相＋Android 原生實作）：
  `resolve / download / cancel / getStatus`，進度走事件，錯誤分六級。
- **yt-dlp 只做解析拿直連**，位元組走 Android 原生 `HttpURLConnection` 1MB 分段抓
  （googlevideo 整包要會 403，逐段要就通；B 站另需完整頁 Referer＋桌面 UA＋`identity`）。
- 需要合併/轉檔才用 **FFmpeg**（`bestaudio→mp3` 自轉；缺席或失敗就留原檔＋報 `POSTPROCESS`）。
- 下載狀態機：`idle → resolving → downloading → postprocessing → done | error | cancelled`；
  錯誤碼全 repo 一致：`NETWORK / EXTRACT / STORAGE / CANCELLED / POSTPROCESS / UNKNOWN`。
- 儲存走 MediaStore（Scoped Storage），檔名為乾淨標題；播放清單上限 50 項、不打包 ZIP。

## 3. 專案架構

```
┌─────────────────────────────────────┐
│ Next.js UI（apps/web）              │  ← 自研
├─────────────────────────────────────┤
│ Capacitor（bridge）                 │  ← 現成
├─────────────────────────────────────┤
│ 自訂 YtDlp Plugin                   │  ← 自研（TS＋Android 原生）
├─────────────────────────────────────┤
│ yt-dlp-android（Maven Central）     │  ← 現成，只 pin 版＋呼叫
├─────────────────────────────────────┤
│ Chaquopy / CPython 3.13＋yt-dlp    │  ← library；AAR 內建版太舊，
│                                     │    用自帶 wheel 疊加蓋掉
│                                     │    （見 `.scratch/archive/multi-site-download-v1/issues/09-ytdlp-overlay-upgrade.md`）
├─────────────────────────────────────┤
│ FFmpeg（ffmpeg-kit audio，可開關）  │  ← 合併／轉檔用
└─────────────────────────────────────┘
```

```
.
├── apps/web/               # Next.js UI（靜態輸出給 WebView）
│   ├── app/page.tsx        # 單下＋整批主畫面
│   ├── app/batch-panel.tsx # 整批設定／進度／結果
│   └── src/lib/ytdlp.ts    # Plugin TS 介面（source of truth）
├── android/app/src/main/   # 原生側：Plugin＋下載管線＋Service
│   └── java/com/openmedia/downloader/
│       ├── YtDlpPlugin.java      # bridge（存 call、轉事件）
│       ├── DownloadService.java  # foreground service（單下＋整批迴圈）
│       ├── Downloader.java       # resolve→選片→分段抓→合併
│       ├── ChunkedFetcher.java   # 1MB 分段＋續傳＋站點 headers
│       ├── ResolveEngine.java    # 調 yt_dlp（單片＋清單 flat 掃描）
│       └── MediaStoreSaver.java  # 存檔＋乾淨檔名
├── e2e/                    # Maestro UI 全環（認文字不認座標）
├── scripts/                # gradle.sh / emulator.sh / e2e.sh / setup.sh
├── .tools/                 # 本地 JDK 21＋SDK（不進版控）
├── .scratch/               # spec＋tickets（01–15）
```

## 4. 依賴（第三方元件清單）

| 元件 | 版本／來源 | 用途 | 授權（已核對上游原文） |
| --- | --- | --- | --- |
| [yt-dlp](https://github.com/yt-dlp/yt-dlp) | wheel overlay 2026.08.19（蓋掉 AAR 內建 2026.06.09） | 只做解析拿直連，不做下載 | Unlicense（公眾領域） |
| yt-dlp-android | Maven Central `dev.ffmpegkit-maintained:yt-dlp-android:2.0.2` | Chaquopy＋Python 殼＋`YtDlp.init` | MIT（Copyright 2026 LucQuebec）；內附第三方聲明（AAR 內 `THIRD-PARTY-NOTICES.txt`） |
| ffmpeg-kit audio | Maven Central `dev.ffmpegkit-maintained:ffmpeg-kit-audio:8.1.8`（**非 `-gpl` 版**） | 合併影音／轉 mp3 | **LGPL-3.0**（弱 copyleft；`-gpl` 版才是 GPL，絕對不要換過去） |
| Chaquopy＋CPython | 17.0.0＋Python 3.13 | Android 上跑 yt-dlp | MIT（SDK） |
| Capacitor 8／Next.js／React | 見 lockfile | bridge／UI | MIT |

政策：現成/library 層只 pin 版、不 fork、不手改；升級開獨立 ticket 並重測全流程。
不要把 notebook 的 `format_id` 白名單照搬進 App，改用解析度語意選 format。

## 5. 侷限性（有實證才寫）

實證基礎：單測 61＋connected 常駐＋`pnpm e2e` 7 流，API 29/33/35 映像皆跑過。
**但全部都在模擬器（x86_64）驗的，沒上過 arm64 真機**，這是目前最大盲點。

- **支援平台（端到端實測）**：YouTube、X、Bilibili；其餘站點不做測試（能解就用，不保證）。
  支援的三站皆可在設定區貼 `cookies.txt` 登入。
- **ABI**：僅 `arm64-v8a`＋`x86_64`（32-bit 舊機不支援；免費版 yt-dlp-android 只包這兩個）。
- **測試素材**：3 秒黑底測試片（作者本人上傳、無版權疑慮），單片／音檔／合併／整批全鏈共用。

- **YouTube 靠 overlay 續命**：AAR 內建 yt-dlp 已跟不上，YouTube 服務端再改版就要 bump
  （流程見 `.scratch/archive/multi-site-download-v1/issues/09-ytdlp-overlay-upgrade.md`）。
- **B 站是軍備競賽**：CDN 規則一換就可能再斷
  （定位方法：host curl 對照組，
  見 `.scratch/archive/multi-site-download-v1/issues/07-x-bilibili-acceptance.md`）。
- **通知列進度約 3 秒早退**：下載本身靠 worker 續命不受影響；背景長批次有被回收風險。
- **e2e 有外部脆弱點**：SoundHelix 限速、YouTube 格式浮動、測試清單刪片——這類紅是驗收報警，不是 bug。
- **功能邊界**：不做帳密登入／App 內登入頁／DRM／私享片；無分享、無內建播放器、
  無斷點續傳；逐項調整是「每項選高度上限」；minSdk 29、64-bit only；APK 約 94MB。
- **Cookie 登入（三站）**：設定區貼桌機 `cookies.txt`（YouTube／Bilibili／X 各站開關，
  加密存、備份排除），登入牆／會員／高画質可抓；過期提示重貼。真登入牆素材未經
  live 驗收（以單測＋假 cookie 不炸正常路為驗收）。

## 快速開始

```bash
corepack pnpm install      # 唯一安裝入口
pnpm setup                 # 新機器一鍵裝本地工具鏈（JDK 21＋SDK，冪等）

pnpm build                 # Web 靜態匯出（免 SDK）
pnpm cap:sync              # 同步進 android/（改 UI 必跑，否則看到舊產物）
pnpm android:test          # testDebugUnitTest
pnpm android:build         # assembleDebug
pnpm android:connected     # connectedAndroidTest（要接實機/emulator）
pnpm test:all              # test＋android:test

pnpm build:apk             # 一鍵鏈：build → cap:sync → android:build
pnpm e2e                   # Maestro 全環＋檔案落地斷言（先 up 模擬器＋裝 APK）
```

> 為什麼 `build` 不直接產 APK？純 Web 產物，沒 SDK 也能跑；
> **「可打包」只認 `pnpm build:apk` 成功**。CI 要綠＝`test:all`＋`build:apk`。
> Gradle 一律經 `scripts/gradle.sh`（會 `cd android/`），不用系統 gradle。

### CI 流程（三條，各司其職）

| workflow | 觸發 | 做什麼 | 紅了代表什麼 |
| --- | --- | --- | --- |
| `ci` | PR＋push main | `test`＋`android:test`＋`build:apk`（全 GH-hosted，確定性高） | 真回歸，擋 merge |
| `nightly` | push main（只限程式／測試／CI 路徑變更）＋每週一 00:00＋手動 | 自架機：connected（29 冒煙＋35 全量）＋`pnpm e2e` | 站外抖動或真回歸，看 log 定性；從不擋 PR |
| `release` | 打 `v*` tag＋手動 | signed AAB＋APK → Releases | 發版鏈斷了才修 |

讀懂 Actions 頁的三個重點：

- **PR 本身只會觸發 `ci`**。你在 PR 旁邊看到的 `nightly`，是「合併進 main 那一刻的 push」或「有人手動按的」，不是 PR 帶起來的。
- **一次只跑一份 nightly**（`concurrency`）：後到的 run 會砍掉先到的，被砍的那個顯示 cancelled——這是省分鐘數的設計，不是失敗。
- **cancelled 會被 GitHub 算進 unsuccessful**：所以 merge 頁可能出現「2 cancelled and 1 successful」紅字，只要真正跑完的那次是綠的就沒事，不要被紅字嚇到。

### 模擬器＋E2E

```bash
bash scripts/emulator.sh up 35            # up 29|33|35、down、status；要 KVM
source .tools/env.sh && adb install -r android/app/build/outputs/apk/debug/app-debug.apk
pnpm e2e
```

- Maestro（`~/.maestro`，不進版控）認 accessibility 文字；WebView 內容透得出來已驗證。
- 坑（完整版見 AGENTS）：`tapOn` 是 regex 全比對，中文用精確全文；
  a11y 樹剪 fold 下節點，CTA 用 sticky 保可點，不要在 flow 裡 scroll 再點。

### 簽章發版（打 tag 即出包）

- keystore（`.jks`）、密碼、key alias **絕不進 repo、不貼 log**，只活在 GitHub Secrets＋runner 暫存。
- 擁有者一次性設定（GitHub repo → Settings → Secrets and variables → Actions）：
  1. 本地生 keystore：`keytool -genkeypair -keystore release.jks -alias openmedia -keyalg RSA -keysize 2048 -validity 9125`
  2. 填 4 個 Secrets：`ANDROID_KEYSTORE_BASE64`（`base64 -w0 release.jks` 全文）、
     `KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`
- 發版：`git tag v1.0.0 && git push origin v1.0.0` → signed AAB＋APK 出現在 Releases。
  沒填 secrets 也能跑（產物為 unsigned，僅驗證鏈路）。

## 疑難排解

| 症狀 | 先查 |
| --- | --- |
| 白屏 / 404 | `output: 'export'`、Capacitor `webDir`、有無 API Route/SSR |
| 下載沒反應 | 是否繞過 Plugin 直接 `fetch`、listener 是否重複訂閱 |
| 變慢 / OOM | Chaquopy 是否重複 init、是否在 UI thread 跑下載 |
| 合併失敗 | FFmpeg 是否缺席、有無留原檔＋`POSTPROCESS` |
| 存檔找不到 | 是否寫死路徑、有無用 MediaStore、測過的 API level |
| Gradle 失敗 | `java -version` 是否 21、是否經 `scripts/gradle.sh`、有無跑過 `cap:sync` |

## 開發流程（六階段，不跳階段）

grilling → to-spec → to-tickets → implement&tdd → code-review → archive，
見 `.opencode/prompts/dev-workflow.md`；規範 `rules-and-conventions.md`；避坑 `gotchas.md`。
`opencode.jsonc` 已掛載三份，不需另行指定。Plugin 介面約定：TS 唯一真相、
原生跑 background、進度走 event、`cancel()` 冪等、卸載時清 listener。

## 合規提醒

僅下載你有權利保存的內容。YouTube 等平台的 ToS 可能限制下載，請遵守當地法規與平台條款，
本專案不提供規避 DRM/付費牆的功能。

## 授權（Unlicense，公眾領域）

本專案以 [Unlicense](https://unlicense.org/) 釋出到公眾領域，全文見 `UNLICENSE`。
跟上游 yt-dlp 一樣：你可以自由複製、修改、散佈、商用，不用署名。

**但請注意（無保證＋法律風險自負）：**

- 本軟體按「現狀」提供，**不提供任何保證**，作者不對使用後果負責。
- Unlicense 處理的是**本專案自研程式碼**的授權；打包進 APK 的第三方元件
  （見上表：FFmpeg 的 GPL 系列、Chaquopy、Capacitor 等）**各有自己的授權**，
  散佈前請自行確認相容與義務。
- 下載功能本身**可能侵害第三方權利**（平台服務條款、著作權、地區法規）：
  用這個工具抓了不該抓的東西，責任在使用者，不在本專案。
  不確定的內容就不要下載。
