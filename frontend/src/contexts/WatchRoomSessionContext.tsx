import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import toast from 'react-hot-toast';
import { fileService } from '../api/fileService';
import { filmService } from '../api/filmService';
import { roomService } from '../api/roomService';
import { useRoomSocket } from '../hooks/useRoomSocket';
import type {
  EpisodeResponse,
  PlaybackUpdateRequest,
  RoomClosedEvent,
  RoomPlaybackChangedEvent,
  RoomResponse,
} from '../models';
import {
  WatchRoomSessionContext,
  type HostLocalSnapshot,
  type WatchRoomSessionContextValue,
  type WatchRoomSyncTarget,
} from './watchRoomSessionContextValue';
import { useTranslation } from 'react-i18next';

const livePosition = (event: RoomPlaybackChangedEvent): number => {
  if (!event.playing) return event.positionSeconds;
  const elapsed = (Date.now() - new Date(event.at).getTime()) / 1000;
  return Math.max(0, event.positionSeconds + elapsed * event.playbackRate);
};

const DEFAULT_HOST_SNAPSHOT: HostLocalSnapshot = { positionSeconds: 0, playing: false, playbackRate: 1 };
// Sentinel below any real playbackRevision (which starts at 0) - ensures the very first snapshot
// applied for a room, even one at revision 0, is never mistaken for a stale replay.
const NO_REVISION_APPLIED = -1;

export const WatchRoomSessionProvider: React.FC<React.PropsWithChildren> = ({ children }) => {
  const { t } = useTranslation();
  const [room, setRoom] = useState<RoomResponse | null>(null);
  const [episodes, setEpisodes] = useState<EpisodeResponse[]>([]);
  const [videoSrc, setVideoSrc] = useState<string | null>(null);
  const [videoError, setVideoError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [changingEpisode, setChangingEpisode] = useState(false);
  const [videoRefreshKey, setVideoRefreshKey] = useState(0);
  const [syncTarget, setSyncTarget] = useState<WatchRoomSyncTarget | null>(null);
  const [closedEvent, setClosedEvent] = useState<RoomClosedEvent | null>(null);

  const roomRef = useRef<RoomResponse | null>(null);
  const requestIdRef = useRef(0);
  const appliedRevisionRef = useRef(NO_REVISION_APPLIED);
  const hostLocalSnapshotRef = useRef<HostLocalSnapshot>(DEFAULT_HOST_SNAPSHOT);
  // At most one updatePlayback request in flight at a time; a HEARTBEAT is dropped outright
  // (not queued) while a PLAY/PAUSE/SEEK/CHANGE_EPISODE is pending, so a stale heartbeat can
  // never overtake or immediately follow a command it was already stale relative to.
  const pendingCriticalCountRef = useRef(0);
  const pendingPromiseRef = useRef<Promise<void>>(Promise.resolve());

  useEffect(() => {
    roomRef.current = room;
  }, [room]);

  const clearSessionState = useCallback(() => {
    requestIdRef.current += 1;
    roomRef.current = null;
    appliedRevisionRef.current = NO_REVISION_APPLIED;
    hostLocalSnapshotRef.current = DEFAULT_HOST_SNAPSHOT;
    pendingCriticalCountRef.current = 0;
    pendingPromiseRef.current = Promise.resolve();
    setRoom(null);
    setEpisodes([]);
    setVideoSrc(null);
    setVideoError(null);
    setLoading(false);
    setErrorMessage(null);
    setChangingEpisode(false);
    setSyncTarget(null);
    setClosedEvent(null);
  }, []);

  /** The one place RoomResponse snapshots (initial join, reconnect resync, post-failure
   *  rollback) get applied. Ignores a snapshot older than the last revision already applied -
   *  guards against a resync racing an already-newer room:playback event - but treats an
   *  equal-or-newer revision as authoritative, always syncing hard. */
  const applySnapshot = useCallback((snapshot: RoomResponse) => {
    if (snapshot.playbackRevision < appliedRevisionRef.current) return;
    appliedRevisionRef.current = snapshot.playbackRevision;
    roomRef.current = snapshot;
    if (snapshot.host) {
      hostLocalSnapshotRef.current = {
        positionSeconds: snapshot.positionSeconds,
        playing: snapshot.playing,
        playbackRate: snapshot.playbackRate,
      };
    }
    setRoom(snapshot);
    setSyncTarget({
      positionSeconds: snapshot.positionSeconds,
      playing: snapshot.playing,
      playbackRate: snapshot.playbackRate,
      action: 'JOIN',
      applyToHost: true,
    });
  }, []);

  /** The one place room:playback WS events get applied. Ignores an event whose revision isn't
   *  strictly newer than the last one applied - the frontend-side half of the anti-lost-update
   *  guarantee (the backend half is the playbackRevision compare-and-set in RoomService). */
  const applyPlaybackEvent = useCallback((event: RoomPlaybackChangedEvent) => {
    if (event.playbackRevision <= appliedRevisionRef.current) return;
    appliedRevisionRef.current = event.playbackRevision;
    const eventPosition = livePosition(event);
    const syncHost = event.action === 'CHANGE_EPISODE';
    if (syncHost && roomRef.current?.host) {
      hostLocalSnapshotRef.current = {
        positionSeconds: eventPosition,
        playing: event.playing,
        playbackRate: event.playbackRate,
      };
    }

    setRoom((current) => {
      if (!current) return current;
      const updated: RoomResponse = {
        ...current,
        episodeId: event.episodeId ?? current.episodeId,
        playing: event.playing,
        positionSeconds: eventPosition,
        playbackRate: event.playbackRate,
        lastActionAt: event.at,
        playbackRevision: event.playbackRevision,
      };
      roomRef.current = updated;
      return updated;
    });

    setSyncTarget({
      positionSeconds: eventPosition,
      playing: event.playing,
      playbackRate: event.playbackRate,
      action: event.action,
      applyToHost: syncHost,
    });
  }, []);

  /** Refetches the authoritative RoomResponse and applies it as a hard sync. Used both for
   *  WebSocket reconnect resync (a PAUSE broadcast missed while disconnected must still be
   *  picked up) and for rolling a failed updatePlayback back to server truth. Best-effort: a
   *  failure here just leaves the room on its last-known state for the next event/action to fix. */
  const resyncFromServer = useCallback(async () => {
    const activeRoom = roomRef.current;
    if (!activeRoom) return;
    try {
      const response = await roomService.getRoom(activeRoom.id);
      applySnapshot(response.result);
    } catch {
      // best-effort, see doc comment above
    }
  }, [applySnapshot]);

  const openRoom = useCallback(async (roomId: string, inviteCode?: string) => {
    if (roomRef.current?.id === roomId && roomRef.current.status === 'ACTIVE') {
      setLoading(false);
      setErrorMessage(null);
      return;
    }

    const previous = roomRef.current;
    if (previous && previous.id !== roomId && !previous.host) {
      void roomService.leaveRoom(previous.id).catch(() => {});
    }

    const requestId = ++requestIdRef.current;
    setLoading(true);
    setErrorMessage(null);
    setClosedEvent(null);
    setEpisodes([]);
    setVideoSrc(null);
    setVideoError(null);
    appliedRevisionRef.current = NO_REVISION_APPLIED;
    hostLocalSnapshotRef.current = DEFAULT_HOST_SNAPSHOT;
    try {
      const response = await roomService.joinRoom(roomId, { inviteCode });
      if (requestIdRef.current !== requestId) return;
      applySnapshot(response.result);
    } catch (error: unknown) {
      if (requestIdRef.current !== requestId) return;
      const apiError = error as { response?: { data?: { message?: string } } };
      setErrorMessage(apiError.response?.data?.message || t('roomJoinFailed'));
    } finally {
      if (requestIdRef.current === requestId) setLoading(false);
    }
  }, [t, applySnapshot]);

  useEffect(() => {
    if (!room?.filmId) return;
    let cancelled = false;
    filmService.getEpisodesByFilm(room.filmId)
      .then((response) => {
        if (!cancelled) setEpisodes(response.result ?? []);
      })
      .catch(() => {
        if (!cancelled) setEpisodes([]);
      });
    return () => { cancelled = true; };
  }, [room?.filmId]);

  useEffect(() => {
    if (!room?.episodeId) return;
    const episode = episodes.find((item) => item.id === room.episodeId);
    if (!episode) return;
    if (!episode.videoFileId) {
      const timer = window.setTimeout(() => setVideoError(t('episodeHasNoVideo')), 0);
      return () => window.clearTimeout(timer);
    }

    let cancelled = false;
    fileService.getFileInfo(episode.videoFileId)
      .then((response) => {
        if (!cancelled) setVideoSrc(response.result.url);
      })
      .catch(() => {
        if (!cancelled) setVideoError(t('videoLoadFailed'));
      });
    return () => { cancelled = true; };
  }, [episodes, room?.episodeId, videoRefreshKey, t]);

  useRoomSocket(room?.id, room?.status === 'ACTIVE', {
    onPlayback: (event) => {
      if (event.episodeId && event.episodeId !== roomRef.current?.episodeId) {
        setVideoSrc(null);
        setVideoError(null);
      }
      applyPlaybackEvent(event);
    },
    onClosed: (event) => {
      setClosedEvent(event);
      setVideoSrc(null);
      setRoom((current) => current ? { ...current, status: 'CLOSED', playing: false } : current);
    },
    // Missing a PAUSE while disconnected must not leave a viewer playing forever - refetch the
    // authoritative snapshot as soon as the socket comes back, before relying on the next event.
    // Typed as () => void (useRoomSocket doesn't await it), but still returns the real promise
    // at runtime - useRoomSocket fires it without awaiting, same as production; tests may await
    // it directly for determinism.
    onReconnected: resyncFromServer,
  }, room?.wsToken);

  const sendPlayback = useCallback((request: PlaybackUpdateRequest): Promise<void> => {
    const activeRoom = roomRef.current;
    if (!activeRoom?.host || activeRoom.status !== 'ACTIVE') return Promise.resolve();

    const isCritical = request.action !== 'HEARTBEAT';
    if (!isCritical && pendingCriticalCountRef.current > 0) {
      // Drop this heartbeat tick outright rather than queue it - by the time a pending
      // PLAY/PAUSE/SEEK/CHANGE_EPISODE finishes, this position reading is already stale.
      return Promise.resolve();
    }
    if (isCritical) pendingCriticalCountRef.current += 1;

    // Never rejects - a failure is fully handled here (toast + rollback), not by the caller.
    // Callers that need "did this settle" semantics (changeEpisode's try/finally) still work
    // fine against an always-resolving promise; nothing downstream needs the rejection reason.
    const run = async () => {
      try {
        await roomService.updatePlayback(activeRoom.id, request);
      } catch {
        if (isCritical) {
          toast.error(t('playbackUpdateFailed'));
          await resyncFromServer();
        }
      } finally {
        if (isCritical) {
          pendingCriticalCountRef.current = Math.max(0, pendingCriticalCountRef.current - 1);
        }
      }
    };

    const next = pendingPromiseRef.current.then(run);
    pendingPromiseRef.current = next;
    return next;
  }, [t, resyncFromServer]);

  const changeEpisode = useCallback(async (episodeId: string) => {
    const activeRoom = roomRef.current;
    if (!activeRoom?.host || !episodeId || episodeId === activeRoom.episodeId) return;
    setChangingEpisode(true);
    try {
      await sendPlayback({ action: 'CHANGE_EPISODE', episodeId });
    } finally {
      setChangingEpisode(false);
    }
  }, [sendPlayback]);

  const refreshVideo = useCallback(() => setVideoRefreshKey((current) => current + 1), []);

  const leaveSession = useCallback(async () => {
    const activeRoom = roomRef.current;
    if (activeRoom && !activeRoom.host) await roomService.leaveRoom(activeRoom.id);
    clearSessionState();
  }, [clearSessionState]);

  const closeSession = useCallback(async () => {
    const activeRoom = roomRef.current;
    if (activeRoom) {
      if (activeRoom.host) await roomService.closeRoom(activeRoom.id);
      else await roomService.leaveRoom(activeRoom.id);
    }
    clearSessionState();
  }, [clearSessionState]);

  const value = useMemo<WatchRoomSessionContextValue>(() => ({
    room,
    episodes,
    videoSrc,
    videoError,
    loading,
    errorMessage,
    changingEpisode,
    syncTarget,
    closedEvent,
    hostLocalSnapshotRef,
    openRoom,
    changeEpisode,
    sendPlayback,
    refreshVideo,
    leaveSession,
    closeSession,
  }), [
    changeEpisode, changingEpisode, closeSession, closedEvent, episodes, errorMessage,
    leaveSession, loading, openRoom, syncTarget,
    refreshVideo, room, sendPlayback, videoError, videoSrc,
  ]);

  return <WatchRoomSessionContext.Provider value={value}>{children}</WatchRoomSessionContext.Provider>;
};
