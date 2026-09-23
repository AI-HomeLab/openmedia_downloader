import { Capacitor, registerPlugin } from '@capacitor/core';

/**
 * 自訂 YtDlp Plugin 的 TS 定義 — 全 repo source of truth（見 rules §5）。
 * format 用 opaque index：UI 只顯示 label，原樣傳回，不解讀 id。
 */
export type DownloadKind = 'video' | 'audio';

export interface ProgressEvent {
  percent: number;
  etaSeconds: number;
  speedBps: number;
  line: string;
}

export interface QualityOption {
  index: number;
  label: string;
  sizeBytes: number;
  hasAudio: boolean;
}

export interface ResolveResult {
  title: string;
  durationSec: number;
  options: QualityOption[];
}

export interface PlaylistItem {
  videoId: string;
  title: string;
  url: string;
  durationSec: number;
}

export interface PlaylistResult {
  playlistId: string;
  title: string;
  itemCount: number;
  totalDurationSec: number;
  items: PlaylistItem[];
}

export interface BatchProgressEvent extends ProgressEvent {
  index: number;
  total: number;
  itemTitle: string;
}

export interface BatchFailure {
  index: number;
  videoId: string;
  title: string;
  code: string;
  message: string;
}

export interface BatchResult {
  succeeded: number;
  total: number;
  failed: BatchFailure[];
}

/** 整批畫質梯（UI 選單＋逐項覆寫共用；下載時映射為 maxHeight 政策）。 */
export const BATCH_HEIGHTS = [144, 240, 360, 480, 720, 1080] as const;

export interface YtDlpPlugin {
  download(options: {
    url: string;
    kind: DownloadKind;
    format?: string;
    formatIndex?: number;
  }): Promise<{ fileUri: string; fileName: string; merged: boolean; code?: string }>;
  cancel(): Promise<void>;
  getStatus(): Promise<{ state: string }>;
  resolve(options: { url: string; kind: DownloadKind }): Promise<ResolveResult>;
  resolvePlaylist(options: { url: string }): Promise<PlaylistResult>;
  downloadBatch(options: {
    url: string;
    kind: DownloadKind;
    maxHeight: number;
    /** JSON 字串（bridge 只收字串；呼叫方先 JSON.stringify）。 */
    overrides: string;
  }): Promise<BatchResult>;
  openFile(options: { uri: string }): Promise<void>;
  addListener(
    eventName: 'progress',
    cb: (e: ProgressEvent) => void,
  ): Promise<{ remove: () => void }> & { remove: () => void };
  addListener(
    eventName: 'batchProgress',
    cb: (e: BatchProgressEvent) => void,
  ): Promise<{ remove: () => void }> & { remove: () => void };
}

const NativeYtDlp = registerPlugin<YtDlpPlugin>('YtDlp');

/** 瀏覽器 `pnpm dev` 用的 mock：假進度跑完即 done，原生行為以實機為準。 */
const mockListeners = new Set<(e: ProgressEvent) => void>();
function mockEmit(e: ProgressEvent) {
  mockListeners.forEach((cb) => cb(e));
}

const MockYtdlp = {
  async download({ url }: { url: string }) {
    if (!/^https?:\/\//.test(url)) throw new Error('INVALID_URL');
    let percent = 0;
    await new Promise<void>((resolve) => {
      const t = setInterval(() => {
        percent += 25;
        mockEmit({ percent, etaSeconds: 1, speedBps: 500_000, line: '' });
        if (percent >= 100) {
          clearInterval(t);
          resolve();
        }
      }, 50);
    });
    return { fileUri: 'mock://downloads/video.mp4', fileName: 'video.mp4', merged: true };
  },
  async cancel() {},
  async getStatus() {
    return { state: 'idle' };
  },
  async resolve() {
    return {
      title: 'Mock 影片',
      durationSec: 60,
      options: [
        { index: 0, label: '1080p mp4', sizeBytes: 10_000_000, hasAudio: false },
        { index: 1, label: '720p mp4', sizeBytes: 5_000_000, hasAudio: true },
      ],
    };
  },
  async resolvePlaylist() {
    return {
      playlistId: 'PLmock',
      title: 'Mock 清單',
      itemCount: 2,
      totalDurationSec: 120,
      items: [
        { videoId: 'mock1', title: 'Mock 第一項', url: 'https://example.com/1', durationSec: 60 },
        { videoId: 'mock2', title: 'Mock 第二項', url: 'https://example.com/2', durationSec: 60 },
      ],
    };
  },
  async downloadBatch() {
    return { succeeded: 0, total: 0, failed: [] };
  },
  async openFile() {},
  addListener(_event: string, cb: (e: ProgressEvent) => void) {
    mockListeners.add(cb);
    const handle = {
      remove: () => {
        mockListeners.delete(cb);
      },
    };
    return Object.assign(Promise.resolve(handle), handle);
  },
};

export const YtDlp: YtDlpPlugin = Capacitor.isNativePlatform()
  ? NativeYtDlp
  // mock 不掛顯式型別：Turbopack 解析不了 object-literal overload，靠 cast 收斂；
  // 真介面漂移由 pnpm build 的型別檢查在 NativeYtDlp 側擋下。
  : (MockYtdlp as unknown as YtDlpPlugin);
