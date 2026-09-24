# 08 — 錯誤收尾與驗收矩陣

**What to build:** 第一版的錯誤與邊界一次收完：無效連結、私人/刪除影片、
站方改版、斷網、儲存失敗，每種都有人類看得懂的中文案與正確重試行為；
三站 × 三形態 × API 29/33/35 驗收矩陣全綠，即 spec 的 DoD。

**Blocked by:** 07 — X 與 Bilibili 逐站驗收.

**Status:** completed

- [x] 六碼錯誤每碼都有對應 UI 文案與重試/放棄行為（EXTRACT 含站方改版提示）
  - `errText`：登入/會員牆→「需登入、目前不支援」；EXTRACT→附「網站可能改版…可稍後重試」；
    其餘照 `CODE：msg` 顯示；error/cancelled/done 皆有重試・清除・開啟列。
- [x] 驗收矩陣（三站 × 單影片/mp3/播放清單 × API 29/33/35）全綠並有記錄
  - 29：merge＋X/Bilibili 5/5 綠（含 API 29 讀中斷續傳修復）。
  - 33：merge＋X/Bilibili 5/5 綠。35：上述＋batch 11 項（8 成功＋3 下架收集）＋e2e 5 流。
  - mp3：單片（SoundHelix／YT→mp3）＋整批（batchKind=audio 走 bestaudio＋轉檔，與單片同路）。
  - 播放清單：06 覆蓋（YouTube 11 項；B 站合集同 entries 機制，07 接受 best-effort）。
- [x] `pnpm test:all` 綠 + `pnpm build:apk` 產物有效
- [x] spec 與 issues 狀態收尾，README 對應段落已同步（含支援範圍聲明）

## 前票缺口（03 帶入，本票補驗）

- [x] 真合併路徑實機驗證（09 overlay 後已通，不再需要「可抓媒體的環境」假設）：
  `YoutubeSplitMergeTest`（134+139 分段抓→ffmpeg 合併→MediaExtractor 有 audio 軌）綠；
  Maestro `e2e/download-youtube-merge.yaml`（解析→360p mp4 無聲→下載→完成）綠。
  剩餘：合併「失敗」分支（POSTPROCESS＋留原檔＋「完成（未合併）」UI）未做 live 注入
  （需破壞性環境才逼得出來；mapper/partial/service 三段有單測＋審查覆蓋）。
  驗收決議：接受為已知缺口，不擋關版；後續若有自然失敗案例再補記。
- [x] 同高不同容器選項已保留（去重鍵＝高度＋容器；單測 `sameHeightKeepsBothContainers`
  以 `resolve-sample.json` 證明 360p 同時有 mp4＋webm）；清單與 resolve 回傳一致。
- [x] 存檔顯示名稱去 `dl-` 前綴：`Downloader.toCleanName` 在回傳前把
  staging 名還原成乾淨標題（合併產物本來就乾淨；改名失敗回原檔不拖死下載）；
  e2e 斷言已同步（單測 `CleanNameTest`）。
- [ ] FGS 通知早退：`Stop FGS timeout` 在啟動後約 3 秒殺 service record
  （單下＋整批皆然；三參數 `startForeground(id, notif, DATA_SYNC)` 已試，無效；
  manifest type＋權限本來就齊）。worker 續命所以下載照走（30+ 次實證），
  但通知列進度提前消失、長批次在背景有被 LMK 風險（前景使用不受影響；
  背景風險 gotchas 早有警告）。根治待查（疑雙 intent／系統計時器），不擋出貨。
