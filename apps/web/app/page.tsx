'use client';

import { useEffect, useReducer, useState } from 'react';
import {
  YtDlp,
  type BatchResult,
  type DownloadKind,
  type PlaylistResult,
  type QualityOption,
} from '../src/lib/ytdlp';
import { initialUiState, reducer } from '../src/lib/downloadState';
import { BatchConfig, BatchDoneView, BatchProgressView } from './batch-panel';

function fmtSize(bytes: number): string {
  if (bytes <= 0) return '大小未知';
  const mb = bytes / 1024 / 1024;
  return mb >= 1000 ? `${(mb / 1024).toFixed(1)} GB` : `${mb.toFixed(1)} MB`;
}

function fmtSpeed(bps: number): string {
  const kb = bps / 1024;
  return kb >= 1024 ? `${(kb / 1024).toFixed(1)} MB/s` : `${kb.toFixed(0)} KB/s`;
}

/** 需登入/會員牆的中文案（ticket 07）：原生報 EXTRACT＋英文訊息，UI 在此翻成中文。 */
function errText(uiError: string): string {
  if (/登入|登录|login|會員|会员|付費|付费/i.test(uiError)) {
    return `需登入、目前不支援（${uiError}）`;
  }
  // EXTRACT 多半是站方改版或內容下架：給使用者下一步（ticket 08）。
  if (/^EXTRACT/i.test(uiError)) {
    return `${uiError}（網站可能改版或影片已下架，可稍後重試）`;
  }
  return uiError;
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
        inputMode="url"
        autoCapitalize="off"
        autoCorrect="off"
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
            </label>
          ))}
          <div className="row cta">
            <button onClick={start}>下載</button>
            <button onClick={resetAll}>重選</button>
          </div>
        </>
      )}
      {playlist !== null && batchProg === null && batchDone === null && (
        <BatchConfig
          playlist={playlist}
          batchKind={batchKind}
          setBatchKind={setBatchKind}
          batchMax={batchMax}
          setBatchMax={setBatchMax}
          overrides={overrides}
          setOverrides={setOverrides}
          busy={busy}
          onStartBatch={startBatch}
          onExitAndReset={() => { exitBatch(); resetAll(); }}
        />
      )}
      {batchProg !== null && <BatchProgressView prog={batchProg} />}
      {batchDone !== null && playlist !== null && (
        <BatchDoneView
          batchDone={batchDone}
          playlist={playlist}
          onRetryItem={retryItem}
          onClear={() => { exitBatch(); resetAll(); }}
        />
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
      {ui.state === 'error' && <p className="error">失敗：{errText(ui.error)}</p>}
      {ui.state === 'done' && (
        <>
          <p>{merged ? '完成' : kind === 'audio' ? '完成（未轉檔）' : '完成（未合併）'}：{ui.fileName}{!merged && doneCode ? `（${doneCode}）` : ''}</p>
          <button onClick={() => YtDlp.openFile({ uri: ui.fileUri })}>開啟</button>
        </>
      )}
    </main>
  );
}
