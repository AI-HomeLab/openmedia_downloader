import { Capacitor, registerPlugin } from '@capacitor/core';

/**
 * 自訂 YtDlp Plugin 的 TS 定義 — 全 repo source of truth（見 rules §5）。
 * format 用 opaque index：UI 只顯示 label，原樣傳回，不解讀 id。
 */
export type DownloadKind = 'video' | 'audio';

export interface ProgressEvent {
  percent: number;
  etaSeconds: number;
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
  openFile(options: { uri: string }): Promise<void>;
  addListener(
    eventName: 'progress',
    cb: (e: ProgressEvent) => void,
  ): Promise<{ remove: () => void }> & { remove: () => void };
}

const NativeYtDlp = registerPlugin<YtDlpPlugin>('YtDlp');

/** 瀏覽器 `pnpm dev` 用的 mock：假進度跑完即 done，原生行為以實機為準。 */
const mockListeners = new Set<(e: ProgressEvent) => void>();
function mockEmit(e: ProgressEvent) {
  mockListeners.forEach((cb) => cb(e));
}

const MockYtdlp: YtDlpPlugin = {
  async download({ url }) {
    if (!/^https?:\/\//.test(url)) throw new Error('INVALID_URL');
    let percent = 0;
    await new Promise<void>((resolve) => {
      const t = setInterval(() => {
        percent += 25;
        mockEmit({ percent, etaSeconds: 1, line: '' });
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
  async openFile() {},
  addListener(_event, cb) {
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
  : MockYtdlp;
