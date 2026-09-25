# 03 — 整批吃 cookie＋文件同步＋安全稽核

**What to build:** 整批下載同路帶 cookie；README「不做登入」舊文案、ticket 07 註記同步；
全 repo 安全稽核（無 Log token、無測試夾帶真 token、備份除外確認）。

**Blocked by:** 02 — 單片下載帶 cookie＋過期文案.

**Status:** completed

- [x] batch loop 內逐項 resolve／下載同 02 注入——實為零改動：
  全 repo 僅兩處 `YoutubeDL(` 皆已在 02 注入，`Downloader`／batch 經由
  `ResolveEngine.resolve` 委派吃到（code-review 雙重確認）
- [x] README 限制節＋功能邊界同步為 cookie 登入現狀；ticket 07 加註後續
- [x] 安全稽核：`CookieStore`／`CookieFiles`／cookie 四方法零 `Log.*`
  （僅既有 resolve 路徑有 `Log.e`，不含 cookie）；測試僅假字串
  （`SID ABC123`／`NID XYZ`／`hello world`）；`backup_rules.xml` 排除
  `cookies.xml`＋manifest 已接；TS 無 `console.*`
