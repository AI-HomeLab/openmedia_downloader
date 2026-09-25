'use client';

import { useEffect, useReducer, useRef, useState } from 'react';
import {
  YtDlp,
  type BatchChoice,
  type BatchResult,
  type CookieSiteStatus,
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
  // 單片統一解析：一次解析拿影片畫質清單，音檔走 bestaudio（免切換）。
  // 下載時才選邊：startedKind 記住這次按的是哪顆鈕（重試／完成文案用）。
  const startedKind = useRef<DownloadKind>('video');
  const [ui, dispatch] = useReducer(reducer, initialUiState);
  const [title, setTitle] = useState('');
  const [options, setOptions] = useState<QualityOption[] | null>(null);
  const [picked, setPicked] = useState(0);
  const [merged, setMerged] = useState(true);
  const [doneCode, setDoneCode] = useState('');
  // 整批（ticket 06，三選項）：playlist 掃描結果＋選擇＋進度＋結果；null＝單片模式。
  const [playlist, setPlaylist] = useState<PlaylistResult | null>(null);
  const [batchChoice, setBatchChoice] = useState<BatchChoice>('capped1080');
  const [batchProg, setBatchProg] = useState<{
    index: number;
    total: number;
    itemTitle: string;
    percent: number;
  } | null>(null);
  const [batchDone, setBatchDone] = useState<BatchResult | null>(null);
  // Cookie 登入（cookie-login/01）：三站狀態＋各站貼上框（不存 state 外）。
  const [cookieSites, setCookieSites] = useState<CookieSiteStatus[] | null>(null);
  const [cookieInputs, setCookieInputs] = useState<Record<string, string>>({});
  const [cookieMsg, setCookieMsg] = useState('');
  // 解析序號：連點時只有最後一次有權寫狀態（防晚到洗掉 downloading/done）。
  const resolveSeq = useRef(0);

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

  // Cookie 狀態：掛載時讀一次，之後每次存取操作後重讀。
  useEffect(() => {
    let alive = true;
    YtDlp.getCookieStatus()
      .then((r) => {
        if (alive) setCookieSites(r.sites);
      })
      .catch(() => {});
    return () => {
      alive = false;
    };
  }, []);

  async function refreshCookieStatus() {
    try {
      const r = await YtDlp.getCookieStatus();
      setCookieSites(r.sites);
    } catch {
      // 讀不到就保留舊畫面，不炸。
    }
  }

  async function saveCookieSite(extractor: string) {
    setCookieMsg('');
    try {
      const r = await YtDlp.saveCookie({
        extractor,
        text: cookieInputs[extractor] ?? '',
      });
      setCookieInputs((o) => {
        const next = { ...o };
        delete next[extractor];
        return next;
      });
      setCookieMsg(`已設定（${r.domains} 個網域）`);
      await refreshCookieStatus();
    } catch (e) {
      setCookieMsg(e instanceof Error ? e.message : '儲存失敗');
    }
  }

  async function clearCookieSite(extractor: string) {
    setCookieMsg('');
    try {
      await YtDlp.clearCookie({ extractor });
      await refreshCookieStatus();
    } catch (e) {
      setCookieMsg(e instanceof Error ? e.message : '清除失敗');
    }
  }

  async function toggleCookieSite(extractor: string, enabled: boolean) {
    setCookieMsg('');
    try {
      await YtDlp.setCookieEnabled({ extractor, enabled });
      await refreshCookieStatus();
    } catch (e) {
      setCookieMsg(e instanceof Error ? e.message : '切換失敗');
    }
  }

  const busy =
    ui.state === 'resolving' ||
    ui.state === 'downloading' ||
    (playlist !== null && batchProg !== null && batchDone === null);

  // 結果／進度落在選項清單下方時，把「該區塊」捲到可視區中央
  // （不是捲到底——到底會把結果本身推出可視區，a11y 樹就看不到它）。
  useEffect(() => {
    if (ui.state === 'downloading' || ui.state === 'done' || ui.state === 'error') {
      const el = document.querySelector('.progress, .error, .done-block');
      el?.scrollIntoView({ block: 'center' });
    }
  }, [ui.state]);

  // 整批同理（狀態在 batchProg/batchDone，與 ui reducer 分開追）。
  // 注意只追布林翻轉：percent 每段都變，直接追 batchProg 會每 tick 捲一次，
  // 把取消按鈕捲跑（tap 座標 stale）。
  const batchRunning = batchProg !== null && batchDone === null;
  useEffect(() => {
    if (batchRunning || batchDone !== null) {
      const el = document.querySelector('.progress, .done-block');
      el?.scrollIntoView({ block: 'center' });
    }
  }, [batchRunning, batchDone]);
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
    await singleResolve(url);
  }

  /** 單片解析固定走 audio 寬鬆校驗（直連 mp3 沒有 video 格式也要能進；
   * 有畫質清單才是影片頁，UI 據此決定給哪些按鈕）。 */
  async function singleResolve(urlArg: string) {
    // 連點/重試疊加時，只有最後一次解析有權寫狀態，晚到的直接丟掉
    //（否則會把 downloading/done 洗回 idle，檔案下了但 UI 永遠等不到）。
    const my = ++resolveSeq.current;
    dispatch({ type: 'start' });
    try {
      const r = await YtDlp.resolve({ url: urlArg, kind: 'audio' });
      if (my !== resolveSeq.current) return;
      setTitle(r.title);
      setOptions(r.options);
      setPicked(0);
      // 回到 idle 等選畫質：用 reset 後保留 url 輸入（state 機不管選單）
      dispatch({ type: 'reset' });
    } catch (e) {
      if (my !== resolveSeq.current) return;
      const code = (e as { code?: string })?.code;
      const message = e instanceof Error ? e.message : 'UNKNOWN';
      dispatch({ type: 'error', error: code ? `${code}：${message}` : message });
    }
  }

  /** 清單掃描：flat 條目→批次設定畫面（還沒下載，不耗流量）。 */
  async function resolvePlaylist() {
    const my = ++resolveSeq.current;
    dispatch({ type: 'start' });
    try {
      const r = await YtDlp.resolvePlaylist({ url });
      if (my !== resolveSeq.current) return;
      setPlaylist(r);
      setOptions(null);
      setTitle('');
      setBatchChoice('capped1080');
      setBatchProg(null);
      setBatchDone(null);
      dispatch({ type: 'reset' });
    } catch (e) {
      if (my !== resolveSeq.current) return;
      const code = (e as { code?: string })?.code;
      const message = e instanceof Error ? e.message : 'UNKNOWN';
      dispatch({ type: 'error', error: code ? `${code}：${message}` : message });
    }
  }

  /** 整批下載：三選項映射為 kind＋maxHeight（逐項覆寫已拔掉，一律 "{}"）。 */
  async function startBatch() {
    if (playlist === null) return;
    const kind: DownloadKind = batchChoice === 'audio' ? 'audio' : 'video';
    // best＝不設上限（4320 實務上封頂）；capped1080 維持既有預設政策。
    const maxHeight = batchChoice === 'best' ? 4320 : 1080;
    setBatchProg({ index: 0, total: playlist.items.length, itemTitle: '', percent: -1 });
    setBatchDone(null);
    try {
      const r = await YtDlp.downloadBatch({
        url,
        kind,
        maxHeight,
        // bridge 只收字串：原生 parseOverrides 解回（現 UI 不送覆寫，固定空物件）。
        overrides: '{}',
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

  /** 失敗項重試：回到單片流程走完整 resolve→下載（解析後手選影片／音檔）。 */
  async function retryItem(itemUrl: string) {
    exitBatch();
    setUrl(itemUrl);
    await singleResolve(itemUrl);
  }

  function exitBatch() {
    setPlaylist(null);
    setBatchProg(null);
    setBatchDone(null);
  }

  async function start(kindArg: DownloadKind) {
    startedKind.current = kindArg;
    dispatch({ type: 'start' });
    try {
      // 音檔模式不帶 formatIndex（走 bestaudio）；只有影片畫質清單才帶 index。
      const r = await YtDlp.download(
        kindArg === 'audio' ? { url, kind: kindArg } : { url, kind: kindArg, formatIndex: picked },
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
          {(options ?? []).length > 0 ? (
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
            ))
          ) : (
            <p>此連結只有音檔</p>
          )}
          <div className="row cta">
            {(options ?? []).length > 0 && (
              <button onClick={() => start('video')}>下載影片</button>
            )}
            <button onClick={() => start('audio')}>下載音檔</button>
            <button onClick={resetAll}>重選</button>
          </div>
        </>
      )}
      {playlist !== null && batchProg === null && batchDone === null && (
        <BatchConfig
          playlist={playlist}
          batchChoice={batchChoice}
          setBatchChoice={setBatchChoice}
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
            <button onClick={resolved ? () => start(startedKind.current) : resolve}>重試</button>
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
        <div className="done-block">
          <p>{merged ? '完成' : startedKind.current === 'audio' ? '完成（未轉檔）' : '完成（未合併）'}：{ui.fileName}{!merged && doneCode ? `（${doneCode}）` : ''}</p>
          <button onClick={() => YtDlp.openFile({ uri: ui.fileUri })}>開啟</button>
        </div>
      )}
      <h2>Cookie 登入</h2>
      <p>從瀏覽器匯出 cookies.txt 貼上，登入牆內容才能抓。各站獨立開關。</p>
      <div className="row">
        <button
          onClick={async () => {
            setCookieMsg('');
            for (const site of cookieSites ?? []) {
              try {
                await YtDlp.clearCookie({ extractor: site.extractor });
              } catch {
                // 單站失敗不擋其他站。
              }
            }
            setCookieInputs({});
            await refreshCookieStatus();
            setCookieMsg('已全部清除');
          }}
          disabled={busy}
        >
          全部清除
        </button>
      </div>
      {(cookieSites ?? []).map((site) => (
        <div key={site.extractor} style={{ marginBottom: 12 }}>
          <div className="row">
            <span style={{ flex: 1 }}>
              {site.displayName}：
              {site.has ? `已設定（${site.domains} 個網域）` : '未設定'}
            </span>
            <button
              onClick={() => toggleCookieSite(site.extractor, !site.enabled)}
              disabled={!site.has || busy}
              aria-pressed={site.enabled}
            >
              {site.enabled ? '停用' : '啟用'}
            </button>
          </div>
          <textarea
            aria-label={`${site.displayName} cookies`}
            placeholder="貼上 cookies.txt 全文"
            rows={3}
            autoCapitalize="off"
            autoCorrect="off"
            spellCheck={false}
            value={cookieInputs[site.extractor] ?? ''}
            disabled={busy}
            onChange={(e) =>
              setCookieInputs((o) => ({ ...o, [site.extractor]: e.target.value }))
            }
            style={{ width: '100%' }}
          />
          <div className="row">
            <button onClick={() => saveCookieSite(site.extractor)} disabled={busy}>
              {site.has ? '覆蓋儲存' : '儲存'}
            </button>
            <button
              onClick={() => clearCookieSite(site.extractor)}
              disabled={!site.has || busy}
            >
              清除
            </button>
          </div>
        </div>
      ))}
      {cookieMsg !== '' && <p>{cookieMsg}</p>}
    </main>
  );
}
