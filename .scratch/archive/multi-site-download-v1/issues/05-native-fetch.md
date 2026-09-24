# 05 — 免費下載管線（解析＋分段抓＋合併）

**What to build:** 把下載的「最後一公里」換成免付費管線，全程零付費元件：
yt-dlp 只做解析拿直連 → Android 原生分段抓（1MB 一段，逐段要）→
需合併時 ffmpeg-kit 合併 → MediaStore。進度/取消/重試/通知列語意與舊路一致，
上層（Plugin/UI）介面不變。

> 背景（已實證）：`YtDlp.execute` 整包抓在 Android 上吃媒體 403；
> player client 五連敗；但小 Range 逐段要全 206（`NativeFetchProbeTest` 綠，
> 單段連續 8MB）。擋的是「一次要全部」，不是 IP 也不是帳號。

**Blocked by:** 04 — 音檔 mp3 模式.

**Status:** completed

- [x] 同一 URL：解析拿直連 → 分段抓 → 拼回完整檔（大小/可播驗證）
- [x] 分離式影音：兩路分段抓 → ffmpeg-kit 合併 → 單一可播檔
  （合併機械以同檔雙路實證；YouTube 真媒體併單見缺口）
- [x] 進度/速度/ETA 照段回報；取消丟棄半成品；重試從頭來（不斷點續傳）
  （速度欄端到端打通，02 的 deferred 順手關掉）
- [x] 分段上限與單段大小有定值（1MB，`CHUNK_SIZE`；未知總長時 percent=-1 不定態）
- [x] 舊 `YtDlp.execute` 下載路徑退役（`Downloader` 改寫；`YtDlpEngine.init` 保留給直譯器）

驗收記錄：emulator omd-35。connected 10/10、單測 15/15、web typecheck+vitest 綠。
已知缺口：驗收當下 YouTube 媒體 15 檔全 403（含稍早 206 過的 2160p——站方動態擋），
真媒體合併單留待可抓環境補驗（併入 08 矩陣）；`merged:false` UI 同。
附帶修：分段 HTTP 碼顯式分級（403/429→EXTRACT，其餘→NETWORK）；
檔名消毒；取消旗直傳 fetcher（每段檢查＋回呼拋出雙保險）。
