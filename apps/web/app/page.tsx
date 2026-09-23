'use client';

import { useEffect, useReducer, useState } from 'react';
import {
  YtDlp,
  BATCH_HEIGHTS,
  type BatchResult,
  type DownloadKind,
  type PlaylistResult,
  type QualityOption,
} from '../src/lib/ytdlp';
import { initialUiState, reducer } from '../src/lib/downloadState';

function fmtDur(sec: number): string {
  if (sec < 0) return '長度未知';
  const h = Math.floor(sec / 3600);
  const m = Math.floor((sec % 3600) / 60);
  const s = Math.floor(sec % 60);
  const mm = h > 0 ? String(m).padStart(2, '0') : String(m);
  return `${h > 0 ? h + ':' : ''}${mm}:${String(s).padStart(2, '0')}`;
}

function fmtSize(bytes: number): string {
  if (bytes <= 0) return '大小未知';
  const mb = bytes / 1024 / 1024;
  return mb >= 1000 ? `${(mb / 1024).toFixed(1)} GB` : `${mb.toFixed(1)} MB`;
}

function fmtSpeed(bps: number): string {
  const kb = bps / 1024;
  return kb >= 1024 ? `${(kb / 1024).toFixed(1)} MB/s` : `${kb.toFixed(0)} KB/s`;
}

export default function Home() {
  const [url, setUrl] = useState('');
  const [kind, setKind] = useState<DownloadKind>('video');
  const [ui, dispatch] = useReducer(reducer, initialUiState);
  const [title, setTitle] = useState('');
  const [options, setOptions] = useState<QualityOption[] | null>(null);
  const [picked, setPicked] = useState(0);
  const [merged, setMerged] = useState(true);
  const [doneCode, setDoneCode] = useState('');
  // 整批（ticket 06）：playlist 掃描結果＋設定＋進度＋結果；null＝單片模式。
  const [playlist, setPlaylist] = useState<PlaylistResult | null>(null);
  const [batchKind, setBatchKind] = useState<DownloadKind>('video');
  const [batchMax, setBatchMax] = useState<number>(1080);
  const [overrides, setOverrides] = useState<Record<string, number>>({});
  const [batchProg, setBatchProg] = useState<{
    index: number;
    total: number;
    itemTitle: string;
    percent: number;
  } | null>(null);
  const [batchDone, setBatchDone] = useState<BatchResult | null>(null);

  // 進度走 event，不 polling；卸載時清 listener（見 gotchas）。
  // mounted 旗標防「卸載先於 addListener resolve」的殘留訂閱。
  useEffect(() => {
    let alive = true;
    let handle: { remove: () => void } | undefined;
    YtDlp.addListener('progress', (e) => {
      dispatch({
        type: 'progress',
        percent: e.percent,
        etaSeconds: e.etaSeconds,
        speed: e.speedBps,
      });
    }).then((h) => {
      if (alive) handle = h;
      else h.remove();
    });
    return () => {
      alive = false;
      handle?.remove();
    };
  }, []);
  // 整批進度走 batchProgress 事件（單下走 progress，兩條線互不干擾）。
  useEffect(() => {
    let alive = true;
    let handle: { remove: () => void } | undefined;
    YtDlp.addListener('batchProgress', (e) => {
      if (!alive) return;
      setBatchProg({
        index: e.index,
        total: e.total,
        itemTitle: e.itemTitle,
        percent: e.percent,
      });
    }).then((h) => {
      if (alive) handle = h;
      else h.remove();
    });
    return () => {
      alive = false;
      handle?.remove();
    };
  }, []);

  const busy =
    ui.state === 'resolving' ||
    ui.state === 'downloading' ||
    (playlist !== null && batchProg !== null && batchDone === null);

  // 結果（完成/失敗）落在選項清單下方：出現時捲到底；downloading 不捲，
  // 避免 tap 確認期間 layout 跳動（automation 會等到 timeout）。
  // CTA 本體是 sticky bottom bar，常駐可視區。
  useEffect(() => {
    if (ui.state === 'done' || ui.state === 'error') {
      window.scrollTo({ top: document.body.scrollHeight });
    }
  }, [ui.state]);
  // null = 還沒解析；空陣列 = 解析過但無可用畫質（照樣顯示標題與狀態）
  const resolved = options !== null;

  /** 清單網址才走掃描（playlist / list= / 合集）；單片保持原快速路徑。 */
  function looksLikePlaylist(u: string): boolean {
    return /[?&]list=|\/playlist|medialist|collection/i.test(u);
  }

  async function resolve() {
    if (looksLikePlaylist(url)) {
      await resolvePlaylist();
      return;
    }
    await singleResolve(url, kind);
  }

  async function singleResolve(urlArg: string, kindArg: DownloadKind) {
    dispatch({ type: 'start' });
    try {
      const r = await YtDlp.resolve({ url: urlArg, kind: kindArg });
      setTitle(r.title);
      setOptions(r.options);
      setPicked(0);
      // 回到 idle 等選畫質：用 reset 後保留 url 輸入（state 機不管選單）
      dispatch({ type: 'reset' });
    } catch (e) {
      const code = (e as { code?: string })?.code;
      const message = e instanceof Error ? e.message : 'UNKNOWN';
      dispatch({ type: 'error', error: code ? `${code}：${message}` : message });
    }
  }

  /** 清單掃描：flat 條目→批次設定畫面（還沒下載，不耗流量）。 */
  async function resolvePlaylist() {
    dispatch({ type: 'start' });
    try {
      const r = await YtDlp.resolvePlaylist({ url });
      setPlaylist(r);
      setOptions(null);
      setTitle('');
      setBatchKind(kind);
      setBatchMax(1080);
      setOverrides({});
      setBatchProg(null);
      setBatchDone(null);
      dispatch({ type: 'reset' });
    } catch (e) {
      const code = (e as { code?: string })?.code;
      const message = e instanceof Error ? e.message : 'UNKNOWN';
      dispatch({ type: 'error', error: code ? `${code}：${message}` : message });
    }
  }

  /** 整批下載：政策（預設 1080p＋最佳音質）＋逐項覆寫一次送原生。 */
  async function startBatch() {
    if (playlist === null) return;
    setBatchProg({ index: 0, total: playlist.items.length, itemTitle: '', percent: -1 });
    setBatchDone(null);
    try {
      const r = await YtDlp.downloadBatch({
        url,
        kind: batchKind,
        maxHeight: batchMax,
        // bridge 只收字串：Record 在此序列化，原生 parseOverrides 解回。
        overrides: JSON.stringify(overrides),
      });
      setBatchProg(null);
      setBatchDone(r);
    } catch (e) {
      const code = (e as { code?: string })?.code;
      const message = e instanceof Error ? e.message : 'UNKNOWN';
      setBatchProg(null);
      dispatch({ type: 'error', error: code ? `${code}：${message}` : message });
    }
  }

  /** 失敗項重試：回到單片流程走完整 resolve→下載（既有路徑）。 */
  async function retryItem(itemUrl: string) {
    exitBatch();
    setKind(batchKind);
    setUrl(itemUrl);
    await singleResolve(itemUrl, batchKind);
  }

  function exitBatch() {
    setPlaylist(null);
    setBatchProg(null);
    setBatchDone(null);
    setOverrides({});
  }

  async function start() {
    dispatch({ type: 'start' });
    try {
      // 音檔模式不帶 formatIndex（走 bestaudio）；只有影片畫質清單才帶 index。
      const r = await YtDlp.download(
        kind === 'audio' ? { url, kind } : { url, kind, formatIndex: picked },
      );
      setMerged(r.merged);
      setDoneCode(r.code ?? '');
      dispatch({ type: 'done', fileUri: r.fileUri, fileName: r.fileName });
    } catch (e) {
      // Capacitor reject 帶 code（六碼之一）；INVALID_URL/BUSY 是呼叫端契約錯，不在六碼內。
      const code = (e as { code?: string })?.code;
      const message = e instanceof Error ? e.message : 'UNKNOWN';
      dispatch({ type: 'error', error: code ? `${code}：${message}` : message });
    }
  }

  async function cancel() {
    await YtDlp.cancel(); // 原生冪等，連點安全
    if (playlist !== null) {
      // 整批取消：回到設定畫面；原生隨後的 CANCELLED 拒絕會帶「已完成 N/M」交代。
      setBatchProg(null);
      dispatch({ type: 'reset' });
      return;
    }
    dispatch({ type: 'cancel' });
  }

  function resetAll() {
    setOptions(null);
    setTitle('');
    dispatch({ type: 'reset' });
  }

  return (
    <main>
      <h1>OpenMedia</h1>
      <div className="row">
        <button onClick={() => setKind('video')} disabled={busy} aria-pressed={kind === 'video'}>
          影片
        </button>
        <button onClick={() => setKind('audio')} disabled={busy} aria-pressed={kind === 'audio'}>
          音檔
        </button>
      </div>
      <input
        type="text"
        placeholder="貼上影片連結"
        value={url}
        disabled={busy}
        onChange={(e) => setUrl(e.target.value)}
      />
      {!resolved && !busy && ui.state !== 'error' && playlist === null && (
        <div className="row">
          <button onClick={resolve}>解析</button>
        </div>
      )}
      {resolved && !busy && (
        <>
          <p>{title}</p>
          {kind === 'video' &&
            (options ?? []).map((o) => (
            <label key={o.index} style={{ display: 'block', minHeight: 44 }}>
              <input
                type="radio"
                name="quality"
                checked={picked === o.index}
                onChange={() => setPicked(o.index)}
              />
              {o.label}・{fmtSize(o.sizeBytes)}
              {!o.hasAudio && '（無聲，需合併）'}
            </label>
          ))}
          <div className="row cta">
            <button onClick={start}>下載</button>
            <button onClick={resetAll}>重選</button>
          </div>
        </>
      )}
      {playlist !== null && batchProg === null && batchDone === null && (
        <>
          <p>
            {playlist.title}（共 {playlist.items.length} 項
            {playlist.totalDurationSec > 0 ? `・${fmtDur(playlist.totalDurationSec)}` : ''}）
          </p>
          <div className="row">
            <button
              onClick={() => setBatchKind('video')}
              disabled={busy}
              aria-pressed={batchKind === 'video'}
            >
              mp4 影片
            </button>
            <button
              onClick={() => setBatchKind('audio')}
              disabled={busy}
              aria-pressed={batchKind === 'audio'}
            >
              mp3 音檔
            </button>
            {batchKind === 'video' && (
              <label>
                預設畫質
                <select
                  value={batchMax}
                  disabled={busy}
                  onChange={(e) => setBatchMax(Number(e.target.value))}
                >
                  {BATCH_HEIGHTS.map((h) => (
                    <option key={h} value={h}>
                      {h}p（或更低）
                    </option>
                  ))}
                </select>
              </label>
            )}
          </div>
          {playlist.items.map((item) => (
            <div key={item.videoId} style={{ display: 'flex', gap: 8, minHeight: 44 }}>
              <span style={{ flex: 1 }}>
                {item.title}・{fmtDur(item.durationSec)}
              </span>
              {batchKind === 'video' && (
                <select
                  aria-label={`${item.title}畫質`}
                  value={overrides[item.videoId] ?? -1}
                  disabled={busy}
                  onChange={(e) => {
                    const v = Number(e.target.value);
                    setOverrides((o) => {
                      const next = { ...o };
                      if (v < 0) delete next[item.videoId];
                      else next[item.videoId] = v;
                      return next;
                    });
                  }}
                >
                  <option value={-1}>預設</option>
                  {BATCH_HEIGHTS.map((h) => (
                    <option key={h} value={h}>
                      {h}p
                    </option>
                  ))}
                </select>
              )}
            </div>
          ))}
          <div className="row cta">
            <button onClick={startBatch} disabled={busy}>下載整批</button>
            <button onClick={() => { exitBatch(); resetAll(); }}>重選</button>
          </div>
        </>
      )}
      {batchProg !== null && (
        <>
          <div className="progress">
            <div style={{ width: `${Math.max(0, batchProg.percent)}%` }} />
          </div>
          <p>
            第 {batchProg.index + 1}/{batchProg.total} 項・{batchProg.itemTitle}
            {batchProg.percent >= 0 ? `・${batchProg.percent.toFixed(0)}%` : ''}
          </p>
        </>
      )}
      {batchDone !== null && playlist !== null && (
        <>
          <p>
            整批完成 {batchDone.succeeded}/{batchDone.total} 項
          </p>
          {batchDone.failed.map((f) => {
            const itemUrl = playlist.items[f.index]?.url ?? '';
            return (
              <div key={f.index} style={{ display: 'flex', gap: 8, minHeight: 44 }}>
                <span style={{ flex: 1 }}>
                  {f.title}：失敗（{f.code}）
                </span>
                {itemUrl !== '' && (
                  <button onClick={() => retryItem(itemUrl)}>重試此項</button>
                )}
              </div>
            );
          })}
          <div className="row">
            <button onClick={() => { exitBatch(); resetAll(); }}>清除</button>
          </div>
        </>
      )}
      <div className="row">
        {busy && <button onClick={cancel}>取消</button>}
        {(ui.state === 'error' || ui.state === 'done' || ui.state === 'cancelled') && (
          <>
            <button onClick={resolved ? start : resolve}>重試</button>
            <button onClick={resetAll}>清除</button>
          </>
        )}
      </div>
      {(ui.state === 'resolving' || ui.state === 'downloading') && (
        <>
          <div className="progress">
            <div style={{ width: `${ui.percent}%` }} />
          </div>
          <p>
            {ui.percent >= 0 ? `${ui.percent.toFixed(0)}%・` : ''}ETA {ui.etaSeconds}s
            {ui.speedBps > 0 ? `・${fmtSpeed(ui.speedBps)}` : ''}
          </p>
        </>
      )}
      {ui.state === 'error' && <p className="error">失敗：{ui.error}</p>}
      {ui.state === 'done' && (
        <>
          <p>{merged ? '完成' : kind === 'audio' ? '完成（未轉檔）' : '完成（未合併）'}：{ui.fileName}{!merged && doneCode ? `（${doneCode}）` : ''}</p>
          <button onClick={() => YtDlp.openFile({ uri: ui.fileUri })}>開啟</button>
        </>
      )}
    </main>
  );
}
