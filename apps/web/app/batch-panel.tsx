'use client';

import {
  BATCH_HEIGHTS,
  type BatchResult,
  type DownloadKind,
  type PlaylistResult,
} from '../src/lib/ytdlp';

export function fmtDur(sec: number): string {
  if (sec < 0) return '長度未知';
  const h = Math.floor(sec / 3600);
  const m = Math.floor((sec % 3600) / 60);
  const s = Math.floor(sec % 60);
  const mm = h > 0 ? String(m).padStart(2, '0') : String(m);
  return `${h > 0 ? h + ':' : ''}${mm}:${String(s).padStart(2, '0')}`;
}

export interface BatchProgress {
  index: number;
  total: number;
  itemTitle: string;
  percent: number;
}

interface BatchPanelProps {
  playlist: PlaylistResult;
  batchKind: DownloadKind;
  setBatchKind: (k: DownloadKind) => void;
  batchMax: number;
  setBatchMax: (h: number) => void;
  overrides: Record<string, number>;
  setOverrides: (f: (o: Record<string, number>) => Record<string, number>) => void;
  busy: boolean;
  onStartBatch: () => void;
  onExitAndReset: () => void;
}

/** 整批設定畫面（ticket 12 由 page.tsx 純搬移）：掃描結果＋mp4/mp3＋政策＋逐項覆寫。 */
export function BatchConfig(p: BatchPanelProps) {
  const { playlist } = p;
  return (
    <>
      <p>
        {playlist.title}（共 {playlist.items.length} 項
        {playlist.totalDurationSec > 0 ? `・${fmtDur(playlist.totalDurationSec)}` : ''}）
      </p>
      <div className="row">
        <button
          onClick={() => p.setBatchKind('video')}
          disabled={p.busy}
          aria-pressed={p.batchKind === 'video'}
        >
          mp4 影片
        </button>
        <button
          onClick={() => p.setBatchKind('audio')}
          disabled={p.busy}
          aria-pressed={p.batchKind === 'audio'}
        >
          mp3 音檔
        </button>
        {p.batchKind === 'video' && (
          <label>
            預設畫質
            <select
              value={p.batchMax}
              disabled={p.busy}
              onChange={(e) => p.setBatchMax(Number(e.target.value))}
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
          {p.batchKind === 'video' && (
            <select
              aria-label={`${item.title}畫質`}
              value={p.overrides[item.videoId] ?? -1}
              disabled={p.busy}
              onChange={(e) => {
                const v = Number(e.target.value);
                p.setOverrides((o) => {
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
        <button onClick={p.onStartBatch} disabled={p.busy}>下載整批</button>
        <button onClick={p.onExitAndReset}>重選</button>
      </div>
    </>
  );
}

/** 整批進度（第 N/M 項＋當項進度）。 */
export function BatchProgressView({ prog }: { prog: BatchProgress }) {
  return (
    <>
      <div className="progress">
        <div style={{ width: `${Math.max(0, prog.percent)}%` }} />
      </div>
      <p>
        第 {prog.index + 1}/{prog.total} 項・{prog.itemTitle}
        {prog.percent >= 0 ? `・${prog.percent.toFixed(0)}%` : ''}
      </p>
    </>
  );
}

interface BatchDoneProps {
  batchDone: BatchResult;
  playlist: PlaylistResult;
  onRetryItem: (url: string) => void;
  onClear: () => void;
}

/** 整批結果（成功數＋失敗列＋逐項重試）。 */
export function BatchDoneView(p: BatchDoneProps) {
  return (
    <div className="done-block">
      <p>
        整批完成 {p.batchDone.succeeded}/{p.batchDone.total} 項
      </p>
      {p.batchDone.failed.map((f) => {
        const itemUrl = p.playlist.items[f.index]?.url ?? '';
        return (
          <div key={f.index} style={{ display: 'flex', gap: 8, minHeight: 44 }}>
            <span style={{ flex: 1 }}>
              {f.title}：失敗（{f.code}）
            </span>
            {itemUrl !== '' && (
              <button onClick={() => p.onRetryItem(itemUrl)}>重試此項</button>
            )}
          </div>
        );
      })}
      <div className="row">
        <button onClick={p.onClear}>清除</button>
      </div>
    </div>
  );
}
