/** 下載狀態機（見 rules §4）：idle → resolving → downloading → done | error | cancelled */
export type DownloadState =
  | 'idle'
  | 'resolving'
  | 'downloading'
  | 'postprocessing'
  | 'done'
  | 'error'
  | 'cancelled';

export interface UiState {
  state: DownloadState;
  percent: number;
  etaSeconds: number;
  fileUri: string;
  fileName: string;
  error: string;
}

export const initialUiState: UiState = {
  state: 'idle',
  percent: 0,
  etaSeconds: 0,
  fileUri: '',
  fileName: '',
  error: '',
};

export type Action =
  | { type: 'start' }
  | { type: 'progress'; percent: number; etaSeconds: number }
  | { type: 'done'; fileUri: string; fileName: string }
  | { type: 'error'; error: string }
  | { type: 'cancel' }
  | { type: 'reset' };

export function reducer(s: UiState, a: Action): UiState {
  switch (a.type) {
    case 'start':
      return { ...initialUiState, state: 'resolving' };
    case 'progress':
      if (s.state !== 'resolving' && s.state !== 'downloading') return s;
      return { ...s, state: 'downloading', percent: a.percent, etaSeconds: a.etaSeconds };
    case 'done':
    case 'error':
      if (s.state === 'cancelled') return s;
      if (a.type === 'done') {
        return { ...s, state: 'done', percent: 100, fileUri: a.fileUri, fileName: a.fileName };
      }
      return { ...s, state: 'error', error: a.error };
    case 'cancel':
      return { ...s, state: 'cancelled' };
    case 'reset':
      return initialUiState;
  }
}
