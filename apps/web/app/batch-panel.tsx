'use client';

import {
  type BatchChoice,
  type BatchResult,
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
  batchChoice: BatchChoice;
  setBatchChoice: (c: BatchChoice) => void;
  busy: boolean;
  onStartBatch: () => void;
  onExitAndReset: () => void;
}

const CHOICES: { value: BatchChoice; label: string }[] = [
  { value: 'best', label: '下載全部影片（最高品質）' },
  { value: 'capped1080', label: '下載全部影片（最高 1080p）' },
  { value: 'audio', label: '下載全部音檔（最高音質）' },
];

/** 整批設定畫面：掃描結果＋三選項（逐項覆寫已拔掉，簡單即正義）。 */
export function BatchConfig(p: BatchPanelProps) {
  const { playlist } = p;
  return (
    <>
      <p>
        {playlist.title}（共 {playlist.items.length} 項
        {playlist.totalDurationSec > 0 ? `・${fmtDur(playlist.totalDurationSec)}` : ''}）
      </p>
      {CHOICES.map((c) => (
        <label key={c.value} style={{ display: 'block', minHeight: 44 }}>
          <input
            type="radio"
            name="batchChoice"
            checked={p.batchChoice === c.value}
            disabled={p.busy}
            onChange={() => p.setBatchChoice(c.value)}
          />
          {c.label}
        </label>
      ))}
      {playlist.items.map((item) => (
        <div key={item.videoId} style={{ display: 'flex', gap: 8, minHeight: 44 }}>
          <span style={{ flex: 1 }}>
            {item.title}・{fmtDur(item.durationSec)}
          </span>
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
