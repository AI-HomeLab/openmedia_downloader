# 02 — 簽章發版（tag 即出包）

**What to build:** 打一個 tag 就產出簽章過的 AAB（主）＋APK（含 ABI split），
上傳到 GitHub Releases 下載得到。私鑰只活在 Secrets＋runner 暫存，不進 repo。

**Blocked by:** 01 — PR 擋門檢查（CI 打包底座先綠）.

**Status:** completed

- [x] `signingConfigs.release` 讀 env（4 個 Secrets），無 env 時本地行為不變
  （unsigned；驗證過 `app-release-unsigned.apk` 照出）
- [x] 丟棄式測試 tag 實跑：tag 觸發＋unsigned 全鏈＋Releases 出現 AAB/APK，
  擁有者手動確認發布成功；跑完刪 tag＋release
- [x] Secrets 填寫步驟寫進文件（值由擁有者去網頁填，agent 不經手）
- [x] 修掉兩個實測坑：`secrets` 不可進 `if:`（改 env 轉手）；unsigned APK 檔名差異

剩餘（擁有者動作）：去 GitHub Settings → Secrets 填 4 個值，
之後打正式 tag 即出 signed 包（屆時建議再跑一次驗簽章）。
