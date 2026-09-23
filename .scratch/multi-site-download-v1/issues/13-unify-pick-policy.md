# 13 — pick()／pickByPolicy() 選片邏輯統一

**What to build:** 單下用 `pick()`（字串格式路徑），整批用 `pickByPolicy()`（高度政策），
兩套語意重疊。統一成單一政策函式（`maxHeight`＋`preferAudio` 參數），
單下預設走「最高可用」（等價現行 `best` 行為），整批走 1080p 政策。

**Blocked by:** 無.

**Status:** completed

- [x] 合併為 `selectByPolicy(options, maxHeight, preferAudio)` 共用核心；
  `pickByPolicy`＝preferAudio 版、`pick` 的 best／worst 走核心（等價舊排序頭尾，
  不依賴輸入排序；height=0 直連單檔照樣可選）
- [x] 單測：`BatchPickTest` 原 5 例全過（語意不變）＋新增 best/worst/height-0 等價案例；
  `pnpm test:all` 綠（43/43）
- [x] `download-video.yaml`＋`download-youtube-merge.yaml` 綠（選片結果不變）
