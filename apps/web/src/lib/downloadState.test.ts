import { describe, expect, it } from 'vitest';
import { initialUiState, reducer } from './downloadState';

describe('download state machine', () => {
  it('idle → resolving → downloading → done', () => {
    let s = reducer(initialUiState, { type: 'start' });
    expect(s.state).toBe('resolving');
    s = reducer(s, { type: 'progress', percent: 42, etaSeconds: 10, speed: 1000 });
    expect(s.state).toBe('downloading');
    expect(s.percent).toBe(42);
    s = reducer(s, { type: 'done', fileUri: 'u', fileName: 'f' });
    expect(s.state).toBe('done');
  });

  it('cancel wins over late done/error', () => {
    let s = reducer(initialUiState, { type: 'start' });
    s = reducer(s, { type: 'cancel' });
    expect(s.state).toBe('cancelled');
    s = reducer(s, { type: 'done', fileUri: 'u', fileName: 'f' });
    expect(s.state).toBe('cancelled');
    s = reducer(s, { type: 'error', error: 'x' });
    expect(s.state).toBe('cancelled');
  });

  it('progress ignored when idle', () => {
    const s = reducer(initialUiState, { type: 'progress', percent: 99, etaSeconds: 0, speed: -1 });
    expect(s.state).toBe('idle');
  });
});
