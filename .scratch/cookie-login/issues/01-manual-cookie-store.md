# 01 — 手動貼 cookie（三站存取＋UI，不下載）

**What to build:** 設定區出現三站 cookie 欄（YouTube／Bilibili／X）：
每站貼上 cookies.txt 全文→校驗檔頭與條目→加密存→顯示已設定（含網域數）→
可關閉、可清除。先不接下載管線。

**Blocked by:** None — can start immediately.

**Status:** completed

- [x] `CookieStore`（EncryptedSharedPreferences，key＝extractor：youtube／bilibili／twitter）：
  save／load／clear（含開關重置）／has＋開關；建庫失敗報 STORAGE（UI 留舊畫面＋訊息）
- [x] 校驗：Netscape 檔頭＋至少一行有效條目，不合格 UI 擋下並說明（單測 7 例）
- [x] UI：三站各狀態、貼上框、儲存、清除、開關； bridge 走 background＋saveCall
  （首建 KeyStore 數百 ms，不塞 bridge thread）
- [x] e2e：reject 路徑綠（Maestro 送不進換行/tab，存檔路徑由單測＋connected 覆蓋，
  誠實缺口）；grep 無真 token、無 Log 殘留；備份排除 cookies.xml
- [x] review findings 全修（F1 background／F2 STORAGE／F3 CRLF／N1 pin 表／
  N2 備份／N4 textarea focus＋toggle 回饋＋clear 語意）
- [x] 註：`_common` 解析雙 tap 已刪除——它會造成重複 resolve 洗掉 done（實測），
  由 `resolveSeq` 防線接手；API 29 e2e 不跑全流（CI 只在 35 跑 e2e），無回歸風險
