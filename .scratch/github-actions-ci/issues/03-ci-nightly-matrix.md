# 03 — Nightly 全矩陣（connected＋e2e，不擋門）

**What to build:** 每天凌晨＋手動可觸發跑一次完整矩陣：
connected（API 29／33／35）＋`pnpm e2e`，結果留報告；
站外抖動（SABR、限速）只告警，永不擋 PR。

**Blocked by:** 01 — PR 擋門檢查（runner＋模擬器底座先通）.

**Status:** ready-for-agent

- [ ] 定時＋`workflow_dispatch` 觸發，emulator 跑 `reactivecircus/android-emulator-runner`
 （條件對齊本地 `emulator.sh`），Maestro 照 `pnpm e2e` 跑
- [ ] 手動跑一次全綠（或紅了能指出是站方抖動非回歸）；workflow 內註明 flaky 政策
