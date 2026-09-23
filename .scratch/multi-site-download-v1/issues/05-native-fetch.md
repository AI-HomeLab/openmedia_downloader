# 05 — 免費下載管線（解析＋分段抓＋合併）

**What to build:** 把下載的「最後一公里」換成免付費管線，全程零付費元件：
yt-dlp 只做解析拿直連 → Android 原生分段抓（1MB 一段，逐段要）→
需合併時 ffmpeg-kit 合併 → MediaStore。進度/取消/重試/通知列語意與舊路一致，
上層（Plugin/UI）介面不變。

> 背景（已實證）：`YtDlp.execute` 整包抓在 Android 上吃媒體 403；
> player client 五連敗；但小 Range 逐段要全 206（`NativeFetchProbeTest` 綠，
> 單段連續 8MB）。擋的是「一次要全部」，不是 IP 也不是帳號。

**Blocked by:** 04 — 音檔 mp3 模式.

**Status:** ready-for-agent

- [ ] 同一 URL：解析拿直連 → 分段抓 → 拼回完整檔（大小/可播驗證）
- [ ] 分離式影音：兩路分段抓 → ffmpeg-kit 合併 → 單一可播檔
- [ ] 進度/速度/ETA 照段回報；取消丟棄半成品；重試從頭來（不斷點續傳）
- [ ] 分段上限與單段大小有定值（預設 1MB 起跳，實測再調），行為寫進票據
- [ ] 舊 `YtDlp.execute` 下載路徑退役（resolve 直連模組保留），無付費依賴殘留
