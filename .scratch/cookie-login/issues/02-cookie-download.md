# 02 — 三站單片下載帶 cookie＋過期文案

**What to build:** 開關打開且有 cookie 時，三站單片 resolve／下載自動帶 `cookiefile`
（解密→暫存→用完即刪）；登入牆／過期回來時 UI 顯示「登入已過期，請重新貼上」
＋一鍵清除。

**Blocked by:** 01 — 手動貼 cookie（三站存取＋UI，不下載）.

**Status:** completed

- [x] `YoutubeDL(opts)` 建構處統一注入 cookiefile（三站同路，extractor→cookie 映射；
  單片 resolve＋清單 flat＋Downloader 內部 resolve 全吃到；暫存用完即刪）
- [x] 過期／401 類錯誤映射到「登入已過期，請重新貼上 cookie」（`withExpiryNote`，
  有帶 cookie 才改寫；UI 既有登入中文案接住顯示；一鍵清除走 01 既有逐站清除）
- [x] 假 cookie＋公開片全環綠（爛 cookie 被忽略不斷正常路；真登入牆素材待擁有者手動驗）
- [x] e2e 不新增（無 UI 變更）；單測 `extractorForUrl`＋`withExpiryNote`＋connected
  `prepare` 暫存＋開關語意全綠
