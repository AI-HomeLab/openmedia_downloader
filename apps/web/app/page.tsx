'use client';

import { useEffect, useReducer, useState } from 'react';
import { YtDlp, type DownloadKind, type QualityOption } from '../src/lib/ytdlp';
import { initialUiState, reducer } from '../src/lib/downloadState';

function fmtSize(bytes: number): string {
  if (bytes <= 0) return '大小未知';
  const mb = bytes / 1024 / 1024;
  return mb >= 1000 ? `${(mb / 1024).toFixed(1)} GB` : `${mb.toFixed(1)} MB`;
}

export default function Home() {
  const [url, setUrl] = useState('');
  const [kind, setKind] = useState<DownloadKind>('video');
  const [ui, dispatch] = useReducer(reducer, initialUiState);
  const [title, setTitle] = useState('');
  const [options, setOptions] = useState<QualityOption[] | null>(null);
  const [picked, setPicked] = useState(0);
  const [merged, setMerged] = useState(true);

  // 進度走 event，不 polling；卸載時清 listener（見 gotchas）。
  // mounted 旗標防「卸載先於 addListener resolve」的殘留訂閱。
  useEffect(() => {
    let alive = true;
    let handle: { remove: () => void } | undefined;
    YtDlp.addListener('progress', (e) => {
      dispatch({ type: 'progress', percent: e.percent, etaSeconds: e.etaSeconds });
    }).then((h) => {
      if (alive) handle = h;
      else h.remove();
    });
    return () => {
      alive = false;
      handle?.remove();
    };
  }, []);

  const busy = ui.state === 'resolving' || ui.state === 'downloading';
  // null = 還沒解析；空陣列 = 解析過但無可用畫質（照樣顯示標題與狀態）
  const resolved = options !== null;

  async function resolve() {
    dispatch({ type: 'start' });
    try {
      const r = await YtDlp.resolve({ url });
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

  async function start() {
    dispatch({ type: 'start' });
    try {
      // 音檔模式不帶 formatIndex（走 bestaudio）；只有影片畫質清單才帶 index。
      const r = await YtDlp.download(
        kind === 'audio' ? { url, kind } : { url, kind, formatIndex: picked },
      );
      setMerged(r.merged);
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
      {!resolved && !busy && ui.state !== 'error' && (
        <div className="row">
          <button onClick={resolve}>解析</button>
        </div>
      )}
      {resolved && !busy && (
        <>
          <p>{title}</p>
          {(options ?? []).map((o) => (
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
          <div className="row">
            <button onClick={start}>下載</button>
            <button onClick={resetAll}>重選</button>
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
            {ui.percent.toFixed(0)}%・ETA {ui.etaSeconds}s
          </p>
        </>
      )}
      {ui.state === 'error' && <p className="error">失敗：{ui.error}</p>}
      {ui.state === 'done' && (
        <>
          <p>{merged ? '完成' : '完成（未合併）'}：{ui.fileName}</p>
          <button onClick={() => YtDlp.openFile({ uri: ui.fileUri })}>開啟</button>
        </>
      )}
    </main>
  );
}
