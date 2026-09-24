# 01 — PR 擋門檢查（test＋打包）

**What to build:** 推 PR（或 push）就自動在乾淨 runner 上跑完 JS 測試、
Android 單測、debug 打包並亮綠燈；紅了擋 merge。

**Blocked by:** None — can start immediately.

**Status:** completed

- [x] workflow 跑 `pnpm test`＋`android:test`＋`build:apk`，用 runner 官方工具鏈
  （setup-node＋corepack、Temurin 21、預裝 SDK＋補包、Gradle 快取），不用 repo `.tools/`
- [x] PR #1 綠（2m10s）；故意弄紅（type error）確認擋 merge，revert 後回綠
- [x] 修掉兩個 CI 獨有坑：setup-android 內建舊 `tools` 包已死（改用預裝 SDK）、
  `cap:sync` 必須在 `android:test` 之前（否則缺 cordova 目錄）
