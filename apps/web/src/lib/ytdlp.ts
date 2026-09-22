import { Capacitor, registerPlugin } from '@capacitor/core';

/**
 * 自訂 YtDlp Plugin 的 TS 定義 — 全 repo source of truth（見 rules §5）。
 * resolve() 留給 03（結構化解析），現在呼叫一律被拒。
 */
export type DownloadKind = 'video' | 'audio';

export interface ProgressEvent {
  percent: number;
  etaSeconds: number;
  line: string;
}

export interface YtDlpPlugin {
  download(options: {
    url: string;
    kind: DownloadKind;
    format?: string;
  }): Promise<{ fileUri: string; fileName: string }>;
  cancel(): Promise<void>;
  getStatus(): Promise<{ state: string }>;
  resolve(options: { url: string }): Promise<never>;
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
    return { fileUri: 'mock://downloads/video.mp4', fileName: 'video.mp4' };
  },
  async cancel() {},
  async getStatus() {
    return { state: 'idle' };
  },
  async resolve() {
    throw new Error('resolve 移至 03 實作');
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
