# 09 — yt-dlp 疊加升級（wheel overlay）：2026.06.09 → 2026.08.19

Status: completed（spike 已驗證，轉正合併）

## Problem
- yt-dlp-android 2.0.2 內建 yt-dlp 2026.06.09（由 AAR 內 requirements-common.imy 取出 version.pyc 確認）。
- YouTube 進入 SABR/PO-token 世代後，舊版 mint 的 googlevideo 直連全 403：
  同一條 device-URL 在 host curl 也是 403，同格式新版 URL 回 206（參數名相同、sig 不同世代）。
- Maven Central 只有 2.0.2，無新版 AAR 可升；rules 要求升級開獨立 ticket（即本檔）。

## Decision
- 不 fork AAR、不套 Chaquopy Gradle plugin（一個 app 只能用在一個 module，會跟 AAR 打架）。
- assets 自帶官方 wheel（pure Python，可 zipimport）：`android/app/src/main/assets/ytdlp/yt_dlp-2026.8.19-py3-none-any.whl`（3.1MB）。
- `YtDlpEngine.initOnce` 在 `YtDlp.init` 後把 wheel 拷到 `filesDir/ytdlp_overlay/` 並 `sys.path.insert(0)`，
  蓋掉 AAR 內建版。檔名含版本號，升級即換檔＋改 `OVERLAY_WHEEL`；舊版殘留自動清。
- APK +3.1MB（已壓縮）；首次啟動拷一次，之後跳過。

## Verification
- connected `YoutubeSplitMergeTest`：版本斷言 2026.08.19＋YouTube 134+139 分段抓＋ffmpeg 合併＋MediaExtractor audio 軌，綠。
- Maestro `e2e/download-youtube-merge.yaml`：解析→選 360p mp4 無聲→下載→完成，綠（3/3 e2e 全綠）。
- 直接 mp4／mp3 舊 flows 不受影響（同次 `pnpm e2e` 全綠）。

## Revert
- 刪 assets wheel＋`installOverlay` 呼叫即回到 AAR 內建版（YouTube 直連會再 403，X/Bilibili/直連不受影響）。

## Follow-up
- 上游出新版 yt-dlp-android 時重估：若內建 yt-dlp ≥ overlay 版，刪 overlay（本 ticket 即驗收標準）。
- overlay 版號 bump 流程：換 wheel＋改常數＋重跑本節三條驗證。
