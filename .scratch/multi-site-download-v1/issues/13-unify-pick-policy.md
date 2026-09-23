# 13 — pick()／pickByPolicy() 選片邏輯統一

**What to build:** 單下用 `pick()`（字串格式路徑），整批用 `pickByPolicy()`（高度政策），
兩套語意重疊。統一成單一政策函式（`maxHeight`＋`preferAudio` 參數），
單下預設走「最高可用」（等價現行 `best` 行為），整批走 1080p 政策。

**Blocked by:** 無.

**Status:** ready-for-agent

- [ ] 合併為一函式，舊 `pick` 呼叫端全轉接；單測矩陣覆蓋（沿用 `BatchPickTest`＋補單下等價案例）
- [ ] `pnpm test:all` 綠＋`download-video.yaml`＋`download-youtube-merge.yaml` 綠（選片結果不變）
