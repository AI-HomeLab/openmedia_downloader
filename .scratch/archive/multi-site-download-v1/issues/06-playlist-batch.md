# 05 — 播放清單批次下載

**What to build:** 貼 YouTube 播放清單或 B 站合集連結，App 逐項下載；
用戶看得到「第 N 項 / 共 M 項」整批進度與當項進度；單項失敗只重試該項，
不整批重來，不打包 ZIP。

**Blocked by:** 05 — 免費下載管線（解析＋分段抓＋合併）.

**Status:** completed

- [x] 批次逐項 resolve→下載走通，整批進度與當項進度同時可見
- [x] 單項失敗可單獨重試；取消整批行為明確（停在當項，不留半成品無交代）
- [x] 大量項目有數量/大小提示（做之前先估上限並在 UI 擋）

## 驗收記錄（emulator omd-35，API 35）
- 單測 27/27（PlaylistResult/BatchPick/BatchOverrides＋既有）。
- connected `BatchDownloadTest`：Google 測試清單 11 項，8 成功＋3 下架項 EXTRACT
  收集後繼續，`succeeded+failed==total`，逐項進度有串流。
- Maestro `e2e/download-playlist.yaml`：掃描→Cake 條目→下載整批→取消→
  「已完成 N/11」交代，全綠。完整整批由 connected 覆蓋（e2e 只到取消）。
- `pnpm e2e` 5/5＋檔案落地斷言全綠；review 雙軸（standards 1 major 已修＋spec 7/7）。
- 已知：FGS 通知約 3 秒早退（單下亦然，08 追蹤）；取消走 error 樣式帶 N/M（刻意）。

## Decisions（user 已定）
- 先掃描（flat：id/標題/時長，不拿格式）→ 批次設定畫面 → 才下載。
- 整批輸出二選一：mp4 影片 / mp3 音檔。
- 畫質政策：預設 1080p（或該項最高可用≤1080p）＋音質最高（bestaudio）；
  每項可手動覆寫（預設/144/240/360/480/720/1080），下載時逐項套用。
- 上限 50 項（flat 只知道數量/時長；超過拒絕並提示）。
- 取消：停在當項（當項半成品刪除），回報已完成 N/M。
- 單項失敗：記下 {index,code,message} 繼續下一項；結束後逐項重試走既有單下路徑。

- [ ] 批次逐項 resolve→下載走通，整批進度與當項進度同時可見
- [ ] 單項失敗可單獨重試；取消整批行為明確（停在當項，不留半成品無交代）
- [ ] 大量項目有數量/大小提示（做之前先估上限並在 UI 擋）
