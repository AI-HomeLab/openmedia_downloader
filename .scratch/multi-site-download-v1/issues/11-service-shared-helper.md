# 11 — DownloadService 單下／整批共用 helper

**What to build:** `runDownload` 與 `downloadBatchItem` 內的轉檔→存檔→清暫存三段
幾乎一樣，抽成共用 helper（約省 40 行），行為零差異。

**Blocked by:** 無.

**Status:** completed

- [x] 抽 `transcodeAudio(transcoder, landed) -> TranscodeResult`（轉檔含失敗留原檔），
  單下／整批共用；單測 `TranscodeAudioTest`（成功直通／native 缺席 fallback／partial 保留）
- [x] `pnpm test:all` 綠（40/40）＋connected `ServiceAudioTest` 綠（行為無變）
