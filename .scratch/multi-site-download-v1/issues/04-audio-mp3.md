# 04 — 音檔 mp3 模式

**What to build:** 用戶選「只要聲音」時，App 下載最佳音訊並轉成 mp3，
走同一套進度、取消、重試與通知列行為，完成後可在 Downloads 開啟播放。

**Blocked by:** 03 — 畫質清單選擇與合併.

**Status:** ready-for-agent

- [ ] 音檔模式端到端走通（resolve → 下載 → 轉檔 → done）
- [ ] 進度、取消、重試、通知列行為與影片模式一致
- [ ] 轉檔失敗保留原檔並報 POSTPROCESS，不靜默丟檔
