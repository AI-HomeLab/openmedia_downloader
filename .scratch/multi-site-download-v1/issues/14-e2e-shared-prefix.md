# 14 — e2e 共用前綴抽 runFlow

**What to build:** 5 個 flow 開頭一模一樣（launch＋assert＋貼連結＋hideKeyboard＋解析），
用 Maestro `runFlow` 抽成共用前綴（`e2e/_common/`），各 flow 只留差異段。
Maestro quirks（精確全文、regex 全比對）註解跟著搬到共用檔。

**Blocked by:** 無.

**Status:** completed

- [x] 抽 `_common/launch.yaml`＋`resolve-video.yaml`＋`resolve-audio.yaml`
  （URL 走 runFlow env；子流需自帶 appId 否則報 Config Section Required；
  執行期 log 印 `${URL}` 原樣但實際有代入——以下載完成為證）
- [x] `pnpm e2e` exit=0：5 流全綠＋檔案斷言（`maestro test e2e/` 不會跑 `_common/` 底下）
