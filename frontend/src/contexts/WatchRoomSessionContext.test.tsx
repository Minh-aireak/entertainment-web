import React from 'react';
import { act, renderHook, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { WatchRoomSessionProvider } from './WatchRoomSessionContext';
import { useWatchRoomSession } from './watchRoomSessionContextValue';
import { roomService } from '../api/roomService';
import { useRoomSocket, type RoomSocketCallbacks } from '../hooks/useRoomSocket';
import toast from 'react-hot-toast';
import type { RoomPlaybackChangedEvent, RoomResponse } from '../models';

vi.mock('../api/roomService', () => ({
  roomService: {
    joinRoom: vi.fn(),
    getRoom: vi.fn(),
    updatePlayback: vi.fn(),
    leaveRoom: vi.fn(),
    closeRoom: vi.fn(),
  },
}));

vi.mock('../api/filmService', () => ({
  filmService: { getEpisodesByFilm: vi.fn().mockResolvedValue({ code: 0, result: [] }) },
}));

vi.mock('../api/fileService', () => ({
  fileService: { getFileInfo: vi.fn().mockResolvedValue({ code: 0, result: { url: 'blob:video' } }) },
}));

vi.mock('../hooks/useRoomSocket', () => ({
  useRoomSocket: vi.fn(),
}));

vi.mock('react-hot-toast', () => ({
  default: { error: vi.fn(), success: vi.fn() },
}));

const baseRoom = (overrides: Partial<RoomResponse> = {}): RoomResponse => ({
  id: 'room-1',
  name: 'Room',
  hostUserId: 'host-1',
  host: true,
  participant: true,
  filmId: 'film-1',
  publicRoom: false,
  inviteCode: 'CODE1',
  status: 'ACTIVE',
  playing: false,
  positionSeconds: 0,
  playbackRate: 1,
  playbackRevision: 0,
  participantCount: 1,
  maxParticipants: 10,
  ...overrides,
});

const basePlaybackEvent = (overrides: Partial<RoomPlaybackChangedEvent> = {}): RoomPlaybackChangedEvent => ({
  roomId: 'room-1',
  action: 'PAUSE',
  playing: false,
  positionSeconds: 10,
  playbackRate: 1,
  actorUserId: 'host-1',
  at: new Date().toISOString(),
  playbackRevision: 1,
  ...overrides,
});

const wrapper = ({ children }: { children: React.ReactNode }) => (
  <WatchRoomSessionProvider>{children}</WatchRoomSessionProvider>
);

const latestSocketCallbacks = (): RoomSocketCallbacks => {
  const calls = vi.mocked(useRoomSocket).mock.calls;
  return calls[calls.length - 1][2];
};

describe('WatchRoomSessionContext', () => {
  beforeEach(() => {
    vi.mocked(roomService.leaveRoom).mockResolvedValue({ code: 0, result: undefined });
    vi.mocked(roomService.closeRoom).mockResolvedValue({ code: 0, result: undefined });
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  const openRoom = async (room: RoomResponse) => {
    vi.mocked(roomService.joinRoom).mockResolvedValue({ code: 0, result: room });
    const { result } = renderHook(() => useWatchRoomSession(), { wrapper });
    await act(async () => {
      await result.current.openRoom(room.id);
    });
    return result;
  };

  it('ignores a room:playback event whose revision is not newer than the last one applied', async () => {
    const result = await openRoom(baseRoom({ playbackRevision: 5, playing: true, positionSeconds: 20 }));

    act(() => {
      latestSocketCallbacks().onPlayback?.(basePlaybackEvent({ playbackRevision: 5, playing: false, positionSeconds: 999 }));
    });

    expect(result.current.room?.playing).toBe(true);
    expect(result.current.room?.playbackRevision).toBe(5);
  });

  it('applies a room:playback event with a strictly newer revision', async () => {
    const result = await openRoom(baseRoom({ playbackRevision: 5, playing: true, positionSeconds: 20 }));

    act(() => {
      latestSocketCallbacks().onPlayback?.(basePlaybackEvent({ playbackRevision: 6, playing: false, positionSeconds: 42 }));
    });

    expect(result.current.room?.playing).toBe(false);
    expect(result.current.room?.playbackRevision).toBe(6);
    expect(result.current.syncTarget).toEqual(expect.objectContaining({ action: 'PAUSE', playing: false }));
  });

  it('refetches and applies an authoritative snapshot on reconnect, recovering a missed PAUSE', async () => {
    const result = await openRoom(baseRoom({ playbackRevision: 3, playing: true, positionSeconds: 5 }));

    // Simulates: host paused while this viewer was disconnected - the WS event was never
    // received, so only a reconnect resync can recover it.
    vi.mocked(roomService.getRoom).mockResolvedValue({
      code: 0,
      result: baseRoom({ playbackRevision: 4, playing: false, positionSeconds: 30 }),
    });

    await act(async () => {
      await latestSocketCallbacks().onReconnected?.();
    });

    expect(roomService.getRoom).toHaveBeenCalledWith('room-1');
    expect(result.current.room?.playing).toBe(false);
    expect(result.current.room?.positionSeconds).toBe(30);
    expect(result.current.syncTarget).toEqual(expect.objectContaining({ action: 'JOIN', playing: false, positionSeconds: 30 }));
  });

  it('reconnect resync does not roll back state a newer WS event already applied', async () => {
    const result = await openRoom(baseRoom({ playbackRevision: 3 }));

    act(() => {
      latestSocketCallbacks().onPlayback?.(basePlaybackEvent({ playbackRevision: 5, playing: true, positionSeconds: 50 }));
    });

    vi.mocked(roomService.getRoom).mockResolvedValue({
      code: 0,
      result: baseRoom({ playbackRevision: 4, playing: false, positionSeconds: 1 }),
    });
    await act(async () => {
      await latestSocketCallbacks().onReconnected?.();
    });

    expect(result.current.room?.playbackRevision).toBe(5);
    expect(result.current.room?.playing).toBe(true);
  });

  it('sendPlayback failure surfaces a toast and rolls the room back to server state', async () => {
    const result = await openRoom(baseRoom({ playbackRevision: 1, playing: true }));

    vi.mocked(roomService.updatePlayback).mockRejectedValue(new Error('network error'));
    vi.mocked(roomService.getRoom).mockResolvedValue({
      code: 0,
      result: baseRoom({ playbackRevision: 2, playing: false, positionSeconds: 7 }),
    });

    await act(async () => {
      await result.current.sendPlayback({ action: 'PAUSE', positionSeconds: 7 });
    });

    expect(toast.error).toHaveBeenCalled();
    expect(roomService.getRoom).toHaveBeenCalledWith('room-1');
    expect(result.current.room?.playing).toBe(false);
    expect(result.current.room?.positionSeconds).toBe(7);
    expect(result.current.syncTarget).toEqual(expect.objectContaining({
      action: 'JOIN',
      applyToHost: true,
      playing: false,
      positionSeconds: 7,
    }));
  });

  it('drops a HEARTBEAT while a critical command is still in flight', async () => {
    const result = await openRoom(baseRoom({ playbackRevision: 1 }));

    let resolvePause!: () => void;
    vi.mocked(roomService.updatePlayback).mockImplementation((_roomId, request) => {
      if (request.action === 'PAUSE') {
        return new Promise((resolve) => {
          resolvePause = () => resolve({ code: 0, result: undefined });
        });
      }
      return Promise.resolve({ code: 0, result: undefined });
    });

    let pauseSettled = false;
    act(() => {
      void result.current.sendPlayback({ action: 'PAUSE', positionSeconds: 5 }).then(() => { pauseSettled = true; });
    });

    await act(async () => {
      await result.current.sendPlayback({ action: 'HEARTBEAT', positionSeconds: 6 });
    });

    expect(roomService.updatePlayback).toHaveBeenCalledTimes(1);
    expect(roomService.updatePlayback).toHaveBeenCalledWith('room-1', expect.objectContaining({ action: 'PAUSE' }));

    await act(async () => {
      resolvePause();
    });
    await waitFor(() => expect(pauseSettled).toBe(true));
  });

  it('serializes a second critical command after the first one settles instead of interleaving', async () => {
    const result = await openRoom(baseRoom({ playbackRevision: 1 }));
    vi.mocked(roomService.updatePlayback).mockResolvedValue({ code: 0, result: undefined });

    await act(async () => {
      await Promise.all([
        result.current.sendPlayback({ action: 'PAUSE', positionSeconds: 1 }),
        result.current.sendPlayback({ action: 'PLAY', positionSeconds: 2 }),
      ]);
    });

    expect(roomService.updatePlayback).toHaveBeenCalledTimes(2);
    const [firstCall, secondCall] = vi.mocked(roomService.updatePlayback).mock.calls;
    expect(firstCall[1].action).toBe('PAUSE');
    expect(secondCall[1].action).toBe('PLAY');
  });

  it('keeps dropping HEARTBEAT while a second queued critical command is still pending', async () => {
    const result = await openRoom(baseRoom({ playbackRevision: 1 }));
    let resolvePause!: () => void;
    let resolvePlay!: () => void;
    vi.mocked(roomService.updatePlayback).mockImplementation((_roomId, request) => {
      if (request.action === 'PAUSE') {
        return new Promise((resolve) => { resolvePause = () => resolve({ code: 0, result: undefined }); });
      }
      if (request.action === 'PLAY') {
        return new Promise((resolve) => { resolvePlay = () => resolve({ code: 0, result: undefined }); });
      }
      return Promise.resolve({ code: 0, result: undefined });
    });

    act(() => {
      void result.current.sendPlayback({ action: 'PAUSE', positionSeconds: 1 });
      void result.current.sendPlayback({ action: 'PLAY', positionSeconds: 2 });
    });
    await waitFor(() => expect(roomService.updatePlayback).toHaveBeenCalledTimes(1));

    await act(async () => { resolvePause(); });
    await waitFor(() => expect(roomService.updatePlayback).toHaveBeenCalledTimes(2));

    await act(async () => {
      await result.current.sendPlayback({ action: 'HEARTBEAT', positionSeconds: 3 });
    });
    expect(roomService.updatePlayback).toHaveBeenCalledTimes(2);

    await act(async () => { resolvePlay(); });
  });
});
