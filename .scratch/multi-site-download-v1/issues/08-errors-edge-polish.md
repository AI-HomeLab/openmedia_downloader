# 08 — 錯誤收尾與驗收矩陣

**What to build:** 第一版的錯誤與邊界一次收完：無效連結、私人/刪除影片、
站方改版、斷網、儲存失敗，每種都有人類看得懂的中文案與正確重試行為；
三站 × 三形態 × API 29/33/35 驗收矩陣全綠，即 spec 的 DoD。

**Blocked by:** 07 — X 與 Bilibili 逐站驗收.

**Status:** ready-for-agent

- [ ] 六碼錯誤每碼都有對應 UI 文案與重試/放棄行為（EXTRACT 含站方改版提示）
- [ ] 驗收矩陣（三站 × 單影片/mp3/播放清單 × API 29/33/35）全綠並有記錄
  （先行證據：DownloadChain 真鏈在 API 29 + 35 emulator 已綠；33 待跑）
- [ ] `pnpm test:all` 綠 + `pnpm build:apk` 產物有效
- [ ] spec 與 issues 狀態收尾，README 對應段落已同步（含支援範圍聲明）

## 前票缺口（03 帶入，本票補驗）

- [x] 真合併路徑實機驗證（09 overlay 後已通，不再需要「可抓媒體的環境」假設）：
  `YoutubeSplitMergeTest`（134+139 分段抓→ffmpeg 合併→MediaExtractor 有 audio 軌）綠；
  Maestro `e2e/download-youtube-merge.yaml`（解析→360p mp4 無聲→下載→完成）綠。
  剩餘：合併「失敗」分支（POSTPROCESS＋留原檔＋「完成（未合併）」UI）仍待一次人為失敗注入驗收。
- [ ] 同高不同容器選項已保留（去重鍵＝高度＋容器）；本票驗收時確認清單
  與 resolve 回傳一致（選項外不提供不存在的畫質）。
- [ ] 存檔顯示名稱去 `dl-` 前綴（現狀：直存/轉檔沿用 staging 檔名 `dl-<標題>.ext`，
  只有合併成功是乾淨標題；e2e 斷言已按現狀 pin 住，改名時同步改 `scripts/e2e.sh`）。
- [ ] FGS 通知早退：`Stop FGS timeout` 在啟動後約 3 秒殺 service record
  （單下＋整批皆然，與 extras 無關的雙 intent 為系統正常行為）；
  worker 執行緒存活所以下載照走（20+ 次實證），但通知列進度會提前消失、
  行程優先權下降。manifest 的 dataSync 宣告＋權限都已齊；
  若要根治，試 `startForeground(id, notif, FOREGROUND_SERVICE_TYPE_DATA_SYNC)`
  三參數版並重測 resolve→download→cancel→retry 全流程。
