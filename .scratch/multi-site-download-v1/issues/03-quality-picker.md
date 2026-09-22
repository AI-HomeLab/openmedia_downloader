# 03 — 畫質清單選擇與合併

**What to build:** 下載前先列出該影片「實際有」的清晰度與大小，用戶選了再下；
需要影音合併的走 FFmpeg。合併失敗不刪原檔，明確報未合併狀態。
全程不 hardcode 任何站的 format 編號。

> 前置：library 2.0.2 無 resolve API。本票含「結構化 resolve」enabler：
> 以自建 Chaquopy 解析模組（或當時新版 library API，二選一，動工前定）取得
> 標題 + 實際格式清單；本票的 UI 清單一律來自該結果。

**Blocked by:** 02 — 單影片下載最小閉環.

**Status:** ready-for-agent

- [ ] resolve 回什麼，UI 就列什麼（含大小）；選項外不提供不存在的畫質
- [ ] 分離式影音合併成功；無 FFmpeg 能力時仍可下載並標示未合併
- [ ] 合併/轉碼失敗保留原檔，回 POSTPROCESS，UI 分得出 done 與 done（未合併）
- [ ] Plugin TS、原生、UI 文案三邊一致（改一名三邊同改）
