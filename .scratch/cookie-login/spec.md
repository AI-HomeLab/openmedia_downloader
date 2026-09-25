# Spec — Cookie 登入（YouTube＋Bilibili＋X、手動貼上、加密存）

Status: ready-for-agent

## Problem Statement

公開範圍能抓的都抓完了，但年齡限制片、會員片、B 站高码率／4K、大會員／付費內容、
X 敏感／年齡限制推文擋在登入牆後面，現在一律回 EXTRACT「需登入、目前不支援」。
用戶願意拿自己的帳號 session 來換這些內容，需要一條路把 cookie 交給 App。

## Solution

用戶從桌機瀏覽器匯出 Netscape `cookies.txt`，按站（YouTube／Bilibili／X）
貼進 App，App 加密存起來，之後 resolve／下載該站時自動帶上。
第二期（另開，不在本 spec）：App 內開站方登入頁，登完自動取 cookie。

## User Stories

1. As a YouTube 使用者，I want 把桌機瀏覽器的 cookies 貼進 App，so that 年齡限制／會員片能解析下載。
2. As a B 站使用者，I want 貼上含 SESSDATA 的 cookies，so that 大會員片／高码率／4K 能下。
3. As a X 使用者，I want 貼上含 auth_token 的 cookies，so that 敏感／年齡限制推文能下、限流時更穩。
4. As a 使用者，I want 每個站獨立開關，so that 不想用的站不送 session。
5. As a 使用者，I want cookie 過期／被站方踢掉時看到「重新貼上」而不是轉圈，so that 知道怎麼自救。
6. As a 使用者，I want 隨時刪掉已存的 cookie，so that 換帳號／不信任時可清除。
7. As a 使用者，I want 整批下載也能吃到 cookie，so that 清單裡的會員片一起下來。
8. As a 開發者，I want cookie 永不進 log／repo／備份外洩面，so that 不變成 token 洩漏源。

## Implementation Decisions

- 格式：只收 Netscape `cookies.txt` 全文（yt-dlp 原生吃這個，不自創格式）；
  貼上時做基本校驗（`# Netscape HTTP Cookie File` 檔頭＋至少一行有效條目），
  不合格就擋在 UI，不送原生。
- 儲存：`EncryptedSharedPreferences`（AES256-GCM，AndroidKeyStore 管 key），
  建庫失敗（key 丟失等）直接報 `STORAGE` 錯誤（UI 保留舊畫面＋訊息），
  不偽裝成「未設定」；絕不明文寫檔、絕不 `Log`、備份排除 `cookies.xml`
 （`backup_rules.xml`＋manifest）；用完即刪暫存檔（不在磁碟留痕）。
- 使用面：`ResolveEngine`／`Downloader` 內部每次 `YoutubeDL(opts)` 建構時，
  依 extractor（youtube／BiliBili／Twitter）拿對應 cookie，有就把解密內容寫到
  app-specific 暫存檔並傳 `cookiefile` 參數；用完即刪暫存檔（不在磁碟留痕）。
  單下／整批同一條路。
- 有效期：不主動驗 cookie 有效性（打了才知道）；401／`Login required` 類回來時，
  錯誤文案從「需登入、目前不支援」改成「登入已過期，請重新貼上 cookie」，
  並提供一鍵清除舊 cookie。
- 範圍：YouTube／Bilibili／X 一次做（`extractor→cookie` 映射，管線同一條）。
  B 站整批政策梯維持 1080p 上限（會員高码率／4K 檔位以後再加，不在本期）。
- UI：設定區加三站 cookie 欄（YouTube／Bilibili／X）：狀態（未設定／已設定＋網域數）、
  貼上框、儲存、清除、開關。 brutally 簡單，不做自動抓取、不做有效期顯示。
- 第二期（WebView 登入頁） explicitly 不在本 spec：要處理 Google 登入反嵌入、
  2FA、WebView→cookie 抽取，獨立開票。

## Testing Decisions

- 單測（JVM）：cookies.txt 校驗（好／壞／空）、wrapFailure 不洩漏 cookie 內容
  （錯誤訊息禁出現 token 樣字串）、加密存取 round-trip（Robolectric 或 instrumented，
  視現有测试 infra 選，Seam：`CookieStore` 介面）。
- Connected：用「需登入才看得到的測試素材」驗 resolve→download 全環
  （素材由擁有者提供可測連結；若無穩定素材，降級為「帶錯 cookie 照樣報可重試錯誤」）。
- e2e（Maestro）：設定頁貼上→儲存→狀態顯示→清除，全 UI 操作，不碰真 token
  （用假 cookies.txt 字串，斷言只到「已設定」狀態＋原生收到呼叫）。
- 安全複查：grep 全 repo 確認無 `Log.*cookie`、無測試檔夾帶真 token。

## Out of Scope

- WebView/App 內登入頁（第二期）。
- Bilibili／X（擴站另開票）。
- 帳號密碼登入（永遠不做：App 不碰密碼，只吃 session cookie）。
- cookie 自動續期／有效期預檢。
- 多帳號／多 profile。

## Further Notes

- 這反轉了舊 spec「登入手段不做」的 scope，舊文案（README「不做登入」、
  ticket 07 註記）要在實作 ticket 內同步更新。
- yt-dlp 的 `--cookies` 對三站通用，第一期的管線設計要留 `extractor→cookie` 映射口，
  擴站時只加站不改管線。
- 合規維持：只抓使用者有權看的內容；本功能不改變「不規避 DRM」紅線
  （DRM 片照樣下不了，這是下載器限制不是 bug）。
