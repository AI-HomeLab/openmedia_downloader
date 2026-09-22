# 07 — 錯誤收尾與驗收矩陣

**What to build:** 第一版的錯誤與邊界一次收完：無效連結、私人/刪除影片、
站方改版、斷網、儲存失敗，每種都有人類看得懂的中文案與正確重試行為；
三站 × 三形態 × API 29/33/35 驗收矩陣全綠，即 spec 的 DoD。

**Blocked by:** 06 — X 與 Bilibili 逐站驗收.

**Status:** ready-for-agent

- [ ] 六碼錯誤每碼都有對應 UI 文案與重試/放棄行為（EXTRACT 含站方改版提示）
- [ ] 驗收矩陣（三站 × 單影片/mp3/播放清單 × API 29/33/35）全綠並有記錄
- [ ] `pnpm test:all` 綠 + `pnpm build:apk` 產物有效
- [ ] spec 與 issues 狀態收尾，README 對應段落已同步（含支援範圍聲明）
