# 11 — DownloadService 單下／整批共用 helper

**What to build:** `runDownload` 與 `downloadBatchItem` 內的轉檔→存檔→清暫存三段
幾乎一樣，抽成共用 helper（約省 40 行），行為零差異。

**Blocked by:** 無.

**Status:** ready-for-agent

- [ ] 抽 `finishItem(staging, landed, audioMode) -> File`（轉檔含失敗留原檔＋存檔＋清暫存），
  兩路共用；單測覆蓋命名/回傳（沿用 `CleanNameTest` 思路）
- [ ] `pnpm test:all` 綠＋任一單下 e2e 綠（行為無變）
