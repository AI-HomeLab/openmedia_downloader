# 12 — page.tsx 拆 BatchPanel 元件

**What to build:** `page.tsx` 目前單檔塞單下＋整批兩個模式（300+ 行），
把批次設定／進度／完成三段拆成 `BatchPanel` 元件（props：playlist＋回調），
`page.tsx` 只留模式分流。純搬移，無行為變更、無視覺變更。

**Blocked by:** 無.

**Status:** completed

- [x] 新增 `apps/web/app/batch-panel.tsx`（`BatchConfig`／`BatchProgressView`／
  `BatchDoneView`＋`fmtDur`），`page.tsx` 只留模式分流＋state（純搬移，無行為/視覺變更）
- [x] `pnpm build` 綠＋Maestro `download-playlist.yaml` 綠（行為無變）
