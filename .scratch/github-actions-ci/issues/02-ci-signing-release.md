# 02 — 簽章發版（tag 即出包）

**What to build:** 打一個 tag 就產出簽章過的 AAB（主）＋APK（含 ABI split），
上傳到 GitHub Releases 下載得到。私鑰只活在 Secrets＋runner 暫存，不進 repo。

**Blocked by:** 01 — PR 擋門檢查（CI 打包底座先綠）.

**Status:** ready-for-agent

- [ ] `signingConfigs.release` 讀 env（`ANDROID_KEYSTORE_BASE64`／`KEYSTORE_PASSWORD`／
  `KEY_ALIAS`／`KEY_PASSWORD`），無 env 時本地行為不變
- [ ] 用丟棄式測試 tag 實跑一次，Releases 出現 signed 包且能安裝；跑完刪 tag＋release
- [ ] Secrets 填寫步驟寫進文件（值由擁有者去網頁填，agent 不經手）
