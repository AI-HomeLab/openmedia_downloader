# 03 — 整批吃 cookie＋文件同步＋安全稽核

**What to build:** 整批下載同路帶 cookie；README「不做登入」舊文案、ticket 07 註記同步；
全 repo 安全稽核（無 Log token、無測試夾帶真 token、備份除外確認）。

**Blocked by:** 02 — 單片下載帶 cookie＋過期文案.

**Status:** ready-for-agent

- [ ] batch loop 內逐項 resolve／下載同 02 注入（不另起爐灶）
- [ ] README＋ticket 07 更新為「cookie 登入（YT）」現狀
- [ ] 安全稽核：grep＋manifest 備份設定確認，記入 ticket
