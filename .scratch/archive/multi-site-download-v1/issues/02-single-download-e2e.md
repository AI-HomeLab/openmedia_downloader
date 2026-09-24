# 02 — 單影片下載最小閉環

**What to build:** 用戶能走完一次完整下載：貼連結 → 下載 →
通知列看得到進度 → 檔案進系統 Downloads → 完成頁可直接開啟。
中途可取消（連點也不 crash），失敗可重試。UI 陽春沒關係，能走完就行。

**Blocked by:** 01 — yt-dlp download 真鏈打通.

**Status:** completed

- [x] resolve → downloading → done 全狀態在 UI 看得到（含進度%、速度、ETA）
  （resolving 目前只做 URL 檢查；結構化 resolve 是 03）
- [x] 背景（切 App / 關螢幕）下載繼續跑，通知列有進度（foreground service + dataSync 類型）
- [x] 取消冪等：連點取消、完成後再取消都不 crash；
  修過一個真 bug：取消與飛行中執行緒打架會照存檔（cancelFlag 清太早），已改為下次啟動才清
- [x] 失敗態明確且可一鍵重試（INVALID_URL 實機驗過）；檔案落在系統 Downloads（MediaStore）
- [x] `pnpm build:apk` 全鏈走通；emulator API 35 實機走通（1MB/30MB 真檔、開啟播片、取消、中斷皆驗）
- [x] 音檔 toggle 以 bestaudio 先行；轉 mp3 是 04 的事

驗收記錄：emulator omd-35（API 35 x86_64，KVM）；
YouTube 機房 IP 403 沿用 01 結論，真站行為 06 驗。
本票 Plugin 採 app-local `registerPlugin` 過渡；03 做結構化解析時評估是否搬到 `plugins/ytdlp/`。

review 決議（defer，有理由）：
- 速度欄位不端到端：library 回呼只有 (progress, eta, line)，無速度；要做需自計 bytes，03+ 評估。
- INVALID_URL/BUSY 用 UNKNOWN：呼叫端契約錯，不屬下載六碼（六碼描述下載失敗）。
- 單 flight BUSY、Downloads/OpenMedia 子目錄：刻意設計（一次一單、保持 Downloads 整齊）。
- API 29/33 證據：留給 07 矩陣；本票只認 API 35 emulator。
