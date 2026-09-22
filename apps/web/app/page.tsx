'use client';

import { useEffect, useReducer, useState } from 'react';
import { YtDlp, type DownloadKind } from '../src/lib/ytdlp';
import { initialUiState, reducer } from '../src/lib/downloadState';

export default function Home() {
  const [url, setUrl] = useState('');
  const [kind, setKind] = useState<DownloadKind>('video');
  const [ui, dispatch] = useReducer(reducer, initialUiState);

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

  async function start() {
    dispatch({ type: 'start' });
    try {
      const r = await YtDlp.download({ url, kind });
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
      <div className="row">
        {!busy && !(ui.state === 'error' || ui.state === 'done' || ui.state === 'cancelled') && (
          <button onClick={start}>下載</button>
        )}
        {busy && <button onClick={cancel}>取消</button>}
        {(ui.state === 'error' || ui.state === 'done' || ui.state === 'cancelled') && (
          <>
            <button onClick={start}>重試</button>
            <button onClick={() => dispatch({ type: 'reset' })}>清除</button>
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
          <p>完成：{ui.fileName}</p>
          <button onClick={() => YtDlp.openFile({ uri: ui.fileUri })}>開啟</button>
        </>
      )}
    </main>
  );
}
