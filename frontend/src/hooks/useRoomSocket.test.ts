import { renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { useWebSocket } from '../contexts/WebSocketContext';
import { useRoomSocket } from './useRoomSocket';

vi.mock('../contexts/WebSocketContext', () => ({ useWebSocket: vi.fn() }));

describe('useRoomSocket', () => {
  afterEach(() => vi.clearAllMocks());

  it('uses the token-protected watch-room protocol and leaves the same namespace', () => {
    const send = vi.fn();
    const subscribe = vi.fn().mockReturnValue(vi.fn());
    vi.mocked(useWebSocket).mockReturnValue({ isConnected: true, send, subscribe });

    const { unmount } = renderHook(() => useRoomSocket('room-1', true, {}, 'signed-token'));

    expect(send).toHaveBeenCalledWith({
      type: 'join-watch-room',
      roomId: 'room-1',
      token: 'signed-token',
    });

    unmount();
    expect(send).toHaveBeenCalledWith({ type: 'leave-watch-room', roomId: 'room-1' });
  });

  it('does not attempt to join a watch room without a subscription token', () => {
    const send = vi.fn();
    const subscribe = vi.fn().mockReturnValue(vi.fn());
    vi.mocked(useWebSocket).mockReturnValue({ isConnected: true, send, subscribe });

    renderHook(() => useRoomSocket('room-1', true, {}));

    expect(send).not.toHaveBeenCalled();
  });
});
