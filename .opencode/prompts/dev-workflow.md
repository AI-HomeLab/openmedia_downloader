# Dev Workflow — OpenMedia Downloader 六階段開發管線

本專案是單一 Android APK（Next.js + Capacitor + 自訂 YtDlp Plugin）。
沿用 repo 已安裝的 skills，每個階段做完要明確同意才進下一階段，不跳階段、不合併。

前置：repo 已有 issue tracker / triage labels / domain docs 概念；
`setup-matt-pocock-skills` bootstrap 已跑過（沒有就先跑）。

1. **grilling** — 釐清問題。一次一問，走完決策樹分支，解決依賴。
   優先在環境裡查事實而不是問人；**使用者擁有 DECISIONS**。
   AI + 使用者有共同心智模型時才停。
   本專案必問：這是 UI 層 / Plugin TS 層 / Android 原生層 / 打包設定的哪一層？
   是否碰到 yt-dlp-android、Chaquopy、FFmpeg（現成/library 層盡量不動）？

2. **to-spec** — 不再訪談，直接收斂成 `.scratch/<feature-slug>/spec.md`，上 `ready-for-agent` label。
   Spec = 概念共識，**不是** code：Problem Statement、Solution、編號 User Stories、
   Implementation Decisions、Testing Decisions（預先約定 seam——優先用既有的，理想只有一個）、
   Out of Scope、Further Notes。不放檔案路徑與 code snippet。
   手機專案額外要寫：目標狀態機（idle→resolving→downloading→postprocessing→done|error|cancelled）、
   離線/取消/重試行為、儲存位置（MediaStore / app-specific）。

3. **to-tickets** — 把 spec 切成垂直切片 ticket，一個 ticket 一檔：
   `.scratch/<feature-slug>/issues/<NN>-<slug>.md`（從 01 編號）。
   不准合併票、不准按 前端/原生/測試 分層切。提出後迭代到核准才 publish。
   切片範例：先走通 `resolve→download→cancel→retry` 最小閉環，再加畫質選項、再加 playlist/zip。

4. **implement & tdd** — TDD 預設。每張 ticket 跑自己的 review-then-commit loop，做完一張才開下一張：
   - 先寫（或先改）能表達預期行為的測試。
   - 寫最小實作讓測試變綠。
   - 持續跑全套測試；失敗時兩邊都可修——測試假設錯就修測試，實作有 bug 就修實作。
   - 綠了且實作完整才往下走。
   - **code-review BEFORE commit** — 每張 ticket 發 TWO 個平行 subagent（見 stage 5 兩軸），
     修完 findings、重跑後才 commit 到當前 branch。

5. **code-review** — 每張 ticket 結束（commit 前）都跑，不是整個 feature 只跑一次。
   TWO 個平行 subagent，各負責一軸：
   - Standards agent：repo 規範（本專案的 rules-and-conventions：靜態輸出、Plugin 介面一致、
     background thread、錯誤分級、Scoped Storage）+ 是否與既有 code 功能重疊。
   - Spec agent：是否滿足其 `issues/NN-*.md`，spec 是否準確？
   修完、重跑、乾淨了才 commit。**最後一張** ticket 做完還要同步 docs
  （spec + issues 收尾，更新 guides/roadmap/CHANGELOG 或 README 對應段落），再 commit docs。

6. **archive** — 只有 stage 5 收尾完成（spec 定稿 + 每個 issue 檔 `Status:` → `completed`）才做：
   把 spec 的 `Status:` 翻成 `completed`，然後
   `git mv .scratch/<feature-slug>/ .scratch/archive/<feature-slug>/`。

## 本專案的 Testing Decisions 缺省值

- 驗證口徑是 **pnpm scripts（根 `package.json` 即契約）**，不自己發明別名：
  `test + android:test` 跑測試，`build:apk` 證明可打包，
  影響下載流程再加 `android:connected` 或實機/emulator 全流程。
- UI 邏輯：web test runner（Vitest / Jest / Playwright，按 repo 現況選一，只選一）只測 UI 狀態機，
  不作為「可打包」依據；UI 改完仍要走 `cap:sync` + Gradle build。
- Plugin TS 層：mock 原生回傳（success / progress events / 各級 error）測狀態機，
  原生行為以 `android/` 下的 JUnit / instrumented test + 實機驗收為準。
- 原生/下載鏈：不靠 unit test 硬測 yt-dlp 本體，以實機或 emulator 手動驗收
  `resolve→download→cancel→retry` 為準，並在 ticket 寫明裝置型號 / API level /
  跑過的 Gradle task。
- DoD 參照 `rules-and-conventions.md §7`：一律以 `android/gradlew` 跑出的結果為準，
  不以 JS build 通過代替。

Pipeline 是迴圈：stage-5 的 findings 可以打回 grilling/spec/tickets。
