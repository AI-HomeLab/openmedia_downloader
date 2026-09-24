# Spec — GitHub Actions 免費 CI：test 與 release

Status: ready-for-agent

## Problem Statement

目前所有驗證（`test:all`、`build:apk`、connected、e2e）都只能在開發者的本機跑：
換機器、新人加入、改完東西想確認沒炸，都要先裝完整工具鏈。
簽章發版更是純手工。想要用 GitHub 免費額度把「每次必跑的檢查」自動化，
把「慢的、會抖的」放定時，把「簽章發版」做成打 tag 即出包。

## Solution

開三條 GitHub Actions workflow，全部跑在免費額度內：

- PR 檢查（擋門）：`pnpm test`＋`android:test`＋`build:apk`（debug），約 10 分鐘。
- Nightly（不擋門）：connected（API 29/33/35）＋Maestro e2e，約 30 分鐘，定時＋手動。
- Release（簽章）：打 tag 即編 signed AAB/APK 並上傳到 GitHub Releases。

簽章私鑰由擁有者以 GitHub Secrets 提供（base64 keystore＋密碼），不進 repo；
沒配 secret 的環境照樣編 debug，互不干擾。

## User Stories

1. As a 開發者，I want 推了 PR 就自動跑單測＋打包，so that 不用等人肉驗就知道有沒有炸。
2. As a 開發者，I want 每天凌晨自動跑一次 connected＋e2e 全矩陣，so that 站方改版／依賴漂移第一時間報警。
3. As a 擁有者，I want 打一個 tag 就產出可簽章發版的 AAB/APK，so that 發版不用手工接 keystore。
4. As a 新加入者，I want 看 workflow 檔就知道驗證口徑，so that 不用問人（口徑與根 `package.json` 一致）。
5. As a 開發者，I want flaky 的站外測試失敗時只告警不擋 PR，so that SoundHelix 限速這種事不用半夜修 CI。
6. As a 擁有者，I want 簽章資訊永遠不出現在 repo/log，so that 符合紅線。
7. As a 開發者，I want 本地照樣能跑完全套（不斷 CI 後路），so that 沒網／沒 secret 也能驗。

## Implementation Decisions

- Runner：一律 `ubuntu-latest`（有 KVM，模擬器硬加速；不需要 macOS/Windows runner）。
- JS 側：官方 setup-node（Node 24，開 corepack）跑既有 pnpm scripts，不發明新口徑。
- Android 側：官方 setup-java（Temurin 21）＋setup-android（SDK 36 下得到）＋
  `gradle/actions/setup-gradle` 快取；CI 走 runner 官方工具鏈，不用 repo 內 `.tools/`
  （`scripts/gradle.sh` 的 `.tools` 優先邏輯需確認認得 `ANDROID_HOME`／`JAVA_HOME`，
  不認就繞過直調 gradlew——實作時驗）。
- 模擬器：`reactivecircus/android-emulator-runner`，API 29／33／35，x86_64，
  條件與本地 `scripts/emulator.sh` 對齊（swiftshader、no-window）。
- Maestro：官網 curl 腳本安裝（runner 自帶 Java 17+），e2e 入口沿用 `pnpm e2e`
 （內含清場＋檔案落地斷言，不另起爐灶）。
- 簽章：`android/app/build.gradle` 加 `signingConfigs.release` 讀環境變數
 （keystore base64 解碼＋store/key 密碼）；無 env 時 fallback 不簽（本地行為不變）。
  Secrets 命名：`ANDROID_KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`。
- 發版物：signed AAB（主）＋signed APK＋ABI split（APK 體積順手減半）；
  上傳用官方 `softprops/action-gh-release` 或內建 `gh`（只選一）。
- 紅線：workflow 檔禁印 secret（用 `***` 遮罩為預設行為，不額外 echo）；
  keystore 檔只存在 runner 暫存，跑完即丟。

## Testing Decisions

- CI 本身的驗收＝把三條 workflow 跑通一次：PR workflow 用一個空 commit 觸發看綠；
  nightly 用 `workflow_dispatch` 手動跑一次看全矩陣；release 用測試 tag（如 `v0.0.0-ci-test`，
  跑完即刪 tag＋release）驗簽章鏈。
- 既有測試 seam 不變：Vitest、JVM 單測、connected、Maestro 全沿用，
  CI 只負責「在哪跑、何時跑、紅了擋不擋」。
- Flaky 政策：connected／e2e 失敗只告警（nightly），不設為 required check；
  PR 擋門只放確定性高的（test／android:test／build:apk）。

## Out of Scope

- iOS／其他平台；macOS／Windows runner。
- Play 上架自動送審（只出包不上架）。
- 自建 runner／付費分鐘。
- e2e／connected 的 flake 根治（SoundHelix 限速、SABR 浮動另案處理）。
- 本地 `.tools/` 工具鏈改動（CI 與本地各走各的工具鏈）。

## Further Notes

- 公開 repo Actions 無限分鐘；私有 repo 每月 2000 分鐘 Linux——兩種都夠。
- connected／e2e 打的是真實站（YouTube／B 站／SoundHelix），runner 出口 IP 與本地不同，
  首次跑若遇站方擋，先比對本地行為再決定是環境問題還是真回歸。
- 簽章 Secrets 的值必須由擁有者去 GitHub 網頁填，agent 不經手私鑰。
