# 06 — X 與 Bilibili 逐站驗收

**What to build:** X 推文影片與 Bilibili 公開影片各走通一次 resolve→download 全流程；
把站差異（X 多為單檔、B 站清晰度結構、各自的失敗長相）記入驗收記錄，
其他站維持 best-effort（能解就用，不逐站驗）。

**Blocked by:** 06 — 播放清單批次下載.

**Status:** completed

## 驗收記錄（emulator omd-29/33/35 x86_64；yt-dlp-android 2.0.2＋overlay yt-dlp 2026.08.19；Chaquopy 17＋CPython 3.13）

- [x] X 公開推文影片端到端走通（含實際畫質行為記錄）
  - 素材：`https://x.com/SpaceX/status/2072695632104468543`（Starship 靜點火，74 秒）。
  - 站差異：13 formats，清一色 progressive 單檔有聲（`http-256` 270p → `http-25128` 2160p）；
    best 即 2160p（234MB），驗收用政策 720p（`http-2176`）。
  - 站差異2：yt-dlp 回報 clen 20.3MB，伺服器在 10.6MB 就 416 真 EOF（clen 灌水）；
    由 `ChunkedFetcher` 續要＋`eofClean`＋`verifySize` 三段處理，下載檔 10611048 bytes。
- [x] Bilibili 公開影片端到端走通（含清晰度/限制行為記錄）
  - 素材：`https://www.bilibili.com/video/BV1hy4y1D734`（140 秒）。
  - 站差異：15 formats，DASH 分離式（`30011/30033/30066/30077` 無聲＋`302xx` 音軌），
    免登入列到 1080p；政策 720p 取 `30066`＋伴音合併，成品 3.7MB。
  - 站差異2（本次挖到的三道門，見 `ChunkedFetcher` 註解）：CDN 要影片頁完整 URL 當
    Referer（只送域名照樣 403）＋桌面版 UA（行動版 UA 403）＋`Accept-Encoding: identity`
   （Android 預設 gzip 會 403）。三者皆由 host curl 對照組定位。
  - API 29 另需讀中斷續傳（舊 OkHttp `unexpected end of stream`），同檔案已修。
- [x] 兩站需登入內容皆回 EXTRACT +「需登入、目前不支援」中文案（非 crash 非轉圈）
  - mapper 新增中英文登入/會員/付費關鍵字（單測）；刪除推文實測
    （`x.com/SpaceX/status/1` → `No video could be found` → EXTRACT）。
  - UI `errText` 把 EXTRACT＋登入關鍵字翻成「需登入、目前不支援」。
  - 誠實註記：真實登入牆內容（大會員工付費片）無穩定素材，未做 live 實測；
    以 mapper 單測＋UI 文案＋刪除推文 live 測試為驗收。
  - 後續：cookie-login 三站手動貼 cookie 上線（見 `.scratch/cookie-login/`），
    有 cookie 時登入牆可抓；本 ticket 的「不支援」文案保留給無 cookie 情境。
- [x] 驗收記裝置型號 / API level / library 版本
  - `ThirdPartyAcceptanceTest`（常駐 connected）：29/33/35 全綠；
    API 矩陣見 08。
