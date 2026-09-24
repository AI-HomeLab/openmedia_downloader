# 01 — PR 擋門檢查（test＋打包）

**What to build:** 推 PR（或 push）就自動在乾淨 runner 上跑完 JS 測試、
Android 單測、debug 打包並亮綠燈；紅了擋 merge。

**Blocked by:** None — can start immediately.

**Status:** ready-for-agent

- [ ] workflow 跑 `pnpm test`＋`android:test`＋`build:apk`，用 runner 官方工具鏈
  （setup-node＋corepack、Temurin 21、SDK 36、Gradle 快取），不用 repo `.tools/`
- [ ] 空 commit 觸發一次，全綠；故意弄紅一次，確認會擋
