import { createContext, useContext, type MutableRefObject } from 'react';
import type {
  EpisodeResponse,
  PlaybackUpdateRequest,
  RoomClosedEvent,
  RoomResponse,
  SyncAction,
} from '../models';

/** Authoritative target for VideoPlayer.syncTo, derived centrally from whichever of (join
 *  snapshot, reconnect resync, room:playback event) most recently passed the revision gate -
 *  both WatchRoom.tsx and WatchRoomMiniPlayer.tsx just forward this to their viewer player ref,
 *  instead of each computing their own live-position math (which is how they used to disagree). */
export interface WatchRoomSyncTarget {
  positionSeconds: number;
  playing: boolean;
  playbackRate: number;
  action: SyncAction;
  /** REST snapshots are authoritative for the host too (initial load, reconnect and rollback).
   *  Normal WebSocket echoes stay viewer-only because the host player already performed them. */
  applyToHost?: boolean;
}

/** Continuously-updated local echo of the host's own player state (position/playing/rate),
 *  never sent to the server - see VideoPlayer's onLocalTimeUpdate. Read by whichever host
 *  VideoPlayer mounts next (full room page <-> mini player) via restoreHostState, so switching
 *  between them doesn't lose position/playing/rate or replay PLAY at position 0. */
export interface HostLocalSnapshot {
  positionSeconds: number;
  playing: boolean;
  playbackRate: number;
}

export interface WatchRoomSessionContextValue {
  room: RoomResponse | null;
  episodes: EpisodeResponse[];
  videoSrc: string | null;
  videoError: string | null;
  loading: boolean;
  errorMessage: string | null;
  changingEpisode: boolean;
  syncTarget: WatchRoomSyncTarget | null;
  closedEvent: RoomClosedEvent | null;
  hostLocalSnapshotRef: MutableRefObject<HostLocalSnapshot>;
  openRoom: (roomId: string, inviteCode?: string) => Promise<void>;
  changeEpisode: (episodeId: string) => Promise<void>;
  /** Serializes with itself (HEARTBEAT is dropped while a PLAY/PAUSE/SEEK/CHANGE_EPISODE is
   *  pending). Critical failures are handled here with a toast and authoritative REST resync;
   *  the returned promise resolves after that recovery finishes. */
  sendPlayback: (request: PlaybackUpdateRequest) => Promise<void>;
  refreshVideo: () => void;
  leaveSession: () => Promise<void>;
  closeSession: () => Promise<void>;
}

export const WatchRoomSessionContext = createContext<WatchRoomSessionContextValue | null>(null);

export const useWatchRoomSession = (): WatchRoomSessionContextValue => {
  const context = useContext(WatchRoomSessionContext);
  if (!context) throw new Error('useWatchRoomSession must be used inside WatchRoomSessionProvider');
  return context;
};
