# 01 — yt-dlp download 真鏈打通

**What to build:** 把 yt-dlp-android 接進 App，打通整條原生鏈：
Chaquopy 單例 init → 下載一個真實小檔 → 進度回傳 → 檔案落地。
Python/Chaquopy 只初始化一次。這一票沒有 UI，驗的是「地基是活的」，
後面所有票都站在它上面。

> 發現（已跟決策者確認）：library 2.0.2 沒有 resolve API（只回 exit code，
> dump 類參數會被靜默丟掉）。結構化 resolve（標題+格式清單）移至 03，
> 到時以自建解析模組或新版 library API 一次做。
> ResolveResult/VideoFormat/ResolveException 已寫好留給 03 用，不在本票提交。

**Blocked by:** None — can start immediately.

**Status:** completed

- [x] 真實小檔下載成功並落地（非 mock；emulator API 35 跑過，1MB mp4 + 進度回傳）
- [x] 下載進度有回傳（progress/eta），main thread 有 guard（Looper 檢查，非僅文件）
- [x] Chaquopy/Python 重複呼叫只初始化一次（`initOnce` 單例，測試覆蓋重複呼叫）
- [x] 失敗收斂為分級碼（ErrorMapper：NETWORK/EXTRACT/STORAGE/UNKNOWN，JVM 單測覆蓋；
  CANCELLED 接 02 的 cancel，POSTPROCESS 接 03 的 FFmpeg）
- [x] `pnpm android:test` 綠；APK 102MB（AAR 落地如預期），aapt 有效
- [x] 版本 pin：Maven Central `dev.ffmpegkit-maintained:yt-dlp-android:2.0.2`，不 fork 不改內部

驗收記錄：emulator omd-35（API 35 x86_64，KVM）；
YouTube 機房 IP 吃 403 → EXTRACT（免費版無 TLS impersonation 的已知限制，02/06 追蹤）。
