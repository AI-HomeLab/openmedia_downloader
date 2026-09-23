# 14 — e2e 共用前綴抽 runFlow

**What to build:** 5 個 flow 開頭一模一樣（launch＋assert＋貼連結＋hideKeyboard＋解析），
用 Maestro `runFlow` 抽成共用前綴（`e2e/_common/`），各 flow 只留差異段。
Maestro quirks（精確全文、regex 全比對）註解跟著搬到共用檔。

**Blocked by:** 無.

**Status:** ready-for-agent

- [ ] 抽 `_common/launch.yaml`（開 App＋斷言首屏）與 `_common/paste-resolve.yaml`
  （貼連結＋解析，URL 參數化）；5 流改寫並全綠
- [ ] `pnpm e2e` exit=0（含檔案斷言；注意共用化後仍恰好一份）
