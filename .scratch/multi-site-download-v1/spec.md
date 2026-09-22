# Spec — OpenMedia Downloader 第一版：三站影音下載（Android 10+）

Status: ready-for-agent

## Problem Statement

我想在 Android 10 以上的手機上，把 YouTube、X、Bilibili 的公開影片或聲音存下來離線看。
現在只有一台電腦跑的 Gradio 範本：要開電腦、一次一種寫法，播放清單還要自己打包 ZIP，
手機上完全不能用。我要的是一個 App：貼連結、選畫質、下载，檔案進系統 Downloads，
下載時切去做別的事也不會莫名其妙斷掉沒交代。

## Solution

一個 Android App（Next.js UI 跑在 WebView，經自訂 Plugin 呼叫 yt-dlp 下載）。
流程：貼連結 → App 解析出該影片實際有的清晰度與大小 → 我選一個 →
下載（通知列看得到進度）→ 完成後在 Downloads 資料夾拿到檔並可直接開啟。
單影片、轉 mp3、播放清單批次都要；不用登入、不打包 ZIP、不做分享。

## User Stories

1. 作為使用者，我想貼一條 YouTube 影片連結並下載，以免開電腦。
2. 作為使用者，我想貼一條 X 推文連結並下載影片，以免只能線上看。
3. 作為使用者，我想貼一條 Bilibili 影片連結並下載，以免只能線上看。
4. 作為使用者，我想在下載前看到該影片實際有的清晰度與預估大小，以免選到不存在的畫質或爆空間。
5. 作為使用者，我想選一個清晰度後開始下載影片，拿到可播放的單一檔案（需要合併時 App 自己合）。
6. 作為使用者，我想把影片轉成 mp3 下載，以便只聽聲音。
7. 作為使用者，我想貼一條播放清單（YouTube 播放清單 / B 站合集）整批下載，以免一條一條貼。
8. 作為使用者，我想在批次下載中看到整體進度（第幾項 / 共幾項）以及當項進度，以免不知道要等多久。
9. 作為使用者，我想在下載中看到進度百分比、速度、預估剩餘時間，以便決定要不要等。
10. 作為使用者，我想隨時取消下載（含連點取消），且 App 不閃退。
11. 作為使用者，我想在下載失敗後看到「發生什麼事 + 能不能重試」，以便一鍵重試。
12. 作為使用者，我想切到別的 App 或關掉螢幕時下載繼續跑，並在通知列看到進度。
13. 作為使用者，我想下載完成後收到明確通知，點通知能看到檔案。
14. 作為使用者，我想在 App 內完成頁直接開啟下載好的檔案（影片/音檔用系統預設開啟）。
15. 作為使用者，我想檔案固定進系統 Downloads 資料夾，在檔案 App 找得到，刪 App 也不會丟。
16. 作為使用者，當內容需要登入才能看（X 未登入可見範圍外、B 站會員/地區牆）時，
    我想看到「這支影片需要登入，目前不支援」的明確提示，而不是轉圈圈或亂碼錯誤。
17. 作為使用者，當連結無效、私人影片、已被刪除或站方改版導致解析失敗時，
    我想看到人類看得懂的原因，而不是技術堆疊。
18. 作為使用者，當沒網路或下載到一半斷線時，我想看到可重試的失敗狀態，而不是卡住。
19. 作為使用者，我想在沒 FFmpeg 相關能力時仍能下載（至少拿到未合併檔並被告知狀態），以免整單失敗。
20. 作為舊 Gradio 範本的使用者，我想第一版的畫質概念（自動最高/選解析度）有人味地保留，
    但不要沿用舊的寫死編號行為。

## Implementation Decisions

- 第一版承諾驗收三站：YouTube、X、Bilibili；其他站 yt-dlp 解得出就可用，不保證、不逐站驗。
- 內容只做公開（免登入）範圍；需登入內容以分級錯誤 + 文案告知，不做 cookie 匯入與帳號登入。
- Plugin TS 介面為唯一真相：resolve（回傳影片資訊 + 實際可用清晰度/大小清單）、
  download、cancel（冪等）、getStatus；另加進度事件通道。不定死之前不寫原生。
- 畫質選擇一律來自 resolve 結果的實際清單；禁止 hardcode 各站 format 編號。
- 下載狀態機：idle → resolving → downloading → postprocessing → done | error | cancelled；
  每個狀態 UI 皆有對應呈現（含速度、ETA、取消/重試）。
- 錯誤全 repo 六碼：NETWORK / EXTRACT / STORAGE / CANCELLED / POSTPROCESS / UNKNOWN；
  login-required、站方改版、私人/刪除內容皆收斂為 EXTRACT 並配不同文案。
- 儲存位置：系統 Downloads（Scoped Storage 公開集合，minSdk 29 免額外權限寫入自有檔案）；
  不做目錄選擇、不做私有目錄版本。
- 完成後動作只有「開啟」（系統預設 intent）；不做分享、不做內建播放器、不做檔案管理器。
- 背景行為：foreground service + 通知列進度；被系統殺掉視為可重試的失敗態，不做跨進程斷點續傳。
  API 33 起通知權限、API 34 起 service 類型宣告為必要實作。
- 原生耗時工作全在 background thread；Python/Chaquopy 單例初始化一次；FFmpeg 缺席有 fallback，
  轉碼失敗保留原檔並回 POSTPROCESS，UI 區分 done 與 done（未合併）。
- 播放清單語意：逐項 resolve→下載，逐項可重試；整批進度為「第 N 項 / 共 M 項」+ 當項進度。
- 版本與工具鏈沿用 repo 基準（Capacitor 8、JDK 21、compile/target 35、minSdk 29、
  Chaquopy 配 CPython 3.13、yt-dlp 隨 library 綁定版）；extractor 被站方改版弄壞時，
  以 EXTRACT 文案告知並等 library 升版發新 APK，不做熱更新。

## Testing Decisions

- Seam 只有一個主入口：Plugin TS 介面——UI 狀態機測試一律 mock 原生回傳
  （success / progress events / 六碼 error / cancel），不依賴真下載。
- 原生邏輯（錯誤收斂、狀態對應、清單語意）走既有 Android JUnit seam，
  由 `pnpm android:test` 執行；不為 yt-dlp 本體寫測試。
- DoD 以 Gradle 跑出的結果為準：`pnpm test + android:test` 綠、`pnpm build:apk` 產物有效；
  動下載流程再加實機或 emulator 走一次 resolve→download→cancel→retry。
- 驗收矩陣：三站 ×（單影片 / mp3 / 播放清單）× API 29 / 33 / 35，
  在 ticket 記裝置型號與 API level。

## Out of Scope

- 帳號登入、cookie 匯入、會員/地區牆內容的取得手段。
- ZIP 打包、系統分享、內建播放器、App 內檔案管理。
- 下載目錄自選、私有目錄儲存。
- 背景被殺後的斷點續傳、跨進程任務恢復。
- yt-dlp in-app 更新與熱修通道。
- 三站之外的逐站保證、32-bit（armeabi-v7a）裝置、iOS。
- DRM / 付費牆規避。

## Further Notes

- 僅下載使用者有權利保存的內容；合規提醒進 README，不進 App 阻斷流程。
- yt-dlp 免費版 AAR 僅 arm64-v8a + x86_64 且約 60–80MB：首版 APK 破百 MB 是預期，
  上架說明寫清支援範圍，不靠刪功能壓體積。
- 通知文案、錯誤文案一律繁體中文，專有名詞保持英文。
