# 03 — 畫質清單選擇與合併

**What to build:** 下載前先列出該影片「實際有」的清晰度與大小，用戶選了再下；
需要影音合併的走 FFmpeg。合併失敗不刪原檔，明確報未合併狀態。
全程不 hardcode 任何站的 format 編號。

> 前置（已定案）：library 2.0.2 無 resolve API。採「直連 AAR 內建 yt_dlp 模組」
> 做 extract_info——不套 Chaquopy plugin（全 app 只能用在一個模組，AAR 建置時已用掉），
> 無重複打包、無版本 skew，同一份 yt_dlp。

**Blocked by:** 02 — 單影片下載最小閉環.

**Status:** completed

- [x] resolve 回什麼，UI 就列什麼（含大小）；選項外不提供不存在的畫質
  （emulator 實機：YouTube 8 檔含大小＋無聲標記；直連單檔 1 檔；另有去重/排序單測）
- [x] 分離式影音合併成功；無 FFmpeg 能力時仍可下載並標示未合併
  （partial-file 機制＋`merged` 旗標＋「完成（未合併）」UI；mapper 有單測）
- [x] 合併/轉碼失敗保留原檔，回 POSTPROCESS，UI 分得出 done 與 done（未合併）
- [x] Plugin TS、原生、UI 文案三邊一致（改一名三邊同改）
- [x] UI 只傳 opaque index；formatId 不進 UI 邏輯（`pickFormat` 原生側查快取）

驗收記錄：emulator omd-35。resolve→選 index→下載落地（QualityDownloadTest 綠）；
單測 13/13 綠；connected 6/6 綠。
已知缺口：機房 IP 對 YouTube 媒體 403，本環境走不到「真合併失敗」分支——
POSTPROCESS 的 mapper/partial/service 三段各有單測或程式審查覆蓋，
`merged:false` UI 待可抓媒體時實機補驗（記入 08）。
附帶修：`videoOptions` 納入無尺寸直連單檔；`hasVideo/hasAudio` 處理 null codec；
`options===null` 才算未解析（空清單不再吞掉標題）。
review 補修：`pickFormat` 校驗 URL（防錯片）；音檔模式不送 formatIndex（保 bestaudio）；
去重鍵改（高度、容器）Set 版（相鄰去重在排序下會漏）；label 永不回傳 formatId；
單測 14/14 綠。
