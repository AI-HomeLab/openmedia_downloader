# 04 — 音檔 mp3 模式

**What to build:** 用戶選「只要聲音」時，App 下載最佳音訊並轉成 mp3，
走同一套進度、取消、重試與通知列行為，完成後可在 Downloads 開啟播放。

> 實作決策：不用 runner 的 `--extract-audio`（它要系統 ffmpeg，二進位不存在），
> 改下載 bestaudio 後用 ffmpeg-kit-maintained audio 包（`libmp3lame`）自轉。
> 同一份 yt-dlp 下載、同一套 service/通知/取消/重試/MediapStore。

**Blocked by:** 03 — 畫質清單選擇與合併.

**Status:** completed

- [x] 音檔模式端到端走通（resolve → 下載 → 轉檔 → done）
  （service 層 instrumented 證明：mp3 落 Downloads 且 merged=true；
  UI 音檔解析/清單實機驗過；音檔無畫質清單是設計）
- [x] 進度、取消、重試、通知列行為與影片模式一致（同一 service/同一套 listener）
- [x] 轉檔失敗保留原檔並報 POSTPROCESS，不靜默丟檔
  （partial=input 機制＋「完成（未轉檔）」UI；Throwable 全收防懸空）

驗收記錄：emulator omd-35。connected 8/8 綠（含 `AudioDownloadTest`、
`ServiceAudioTest`）；單測含轉檔檔名推導；libmp3lame 機上實證可用。
已知缺口：YouTube bestaudio 在機房 IP 403，YouTube 側 mp3 真轉留給 07 實站驗。
附帶修：abiFilters 只留 arm64-v8a + x86_64（對齊 yt-dlp-android，舊 32-bit 機不支援）；
kind toggle 加 pressed 樣式（之前看不出模式）；resolve 加 kind（音檔不要求影像）。
附帶抓 bug：transcoder 先刪 output 會誤刪同名 input（經 temp 檔）、
ffmpeg 認不得 `.tmp` 要用 `.tmp.mp3`。
