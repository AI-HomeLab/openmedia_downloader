# 10 — 刪除過期探針測試

**What to build:** 刪掉結論已被取代的早期探針測試，只留現行管線的驗收測試，
避免後人誤讀過時結論。

**Blocked by:** 無.

**Status:** ready-for-agent

- [ ] 刪除 `NativeFetchProbeTest.java`（「原生抓全滅」結論已被分段管線＋overlay 推翻）
- [ ] 檢查 `QualityDownloadTest.java`、`AudioDownloadTest.java`、`ServiceAudioTest.java`、
  `PlayerClientProbeTest.java`（若存在）：只留仍在描述現行行為的，過期的同刪
- [ ] `pnpm test:all` 綠（確認沒刪到現行覆蓋）
