# 02 — 三站單片下載帶 cookie＋過期文案

**What to build:** 開關打開且有 cookie 時，三站單片 resolve／下載自動帶 `cookiefile`
（解密→暫存→用完即刪）；登入牆／過期回來時 UI 顯示「登入已過期，請重新貼上」
＋一鍵清除。

**Blocked by:** 01 — 手動貼 cookie（三站存取＋UI，不下載）.

**Status:** ready-for-agent

- [ ] `YoutubeDL(opts)` 建構處統一注入 cookiefile（三站同路，extractor→cookie 映射）
- [ ] 過期／401 類錯誤映射到重貼文案；假 cookie 測試照樣報可重試錯誤（非 crash）
- [ ] 真素材全環（素材由擁有者提供；無則降級驗）＋e2e 不碰真 token
