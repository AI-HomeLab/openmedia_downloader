# 02 — 單影片下載最小閉環

**What to build:** 用戶能走完一次完整下載：貼 YouTube 連結 → 下載 →
通知列看得到進度 → 檔案進系統 Downloads → 完成頁可直接開啟。
中途可取消（連點也不 crash），失敗可重試。UI 陽春沒關係，能走完就行。

**Blocked by:** 01 — yt-dlp resolve 真鏈打通.

**Status:** ready-for-agent

- [ ] resolve → downloading → done 全狀態在 UI 看得到（含進度%、速度、ETA）
- [ ] 背景（切 App / 關螢幕）下載繼續跑，通知列有進度
- [ ] 取消冪等：連點取消、完成後再取消都不 crash
- [ ] 失敗態明確且可一鍵重試；檔案落在系統 Downloads
- [ ] `pnpm build:apk` 產物在實機或 emulator 走通一次全流程
