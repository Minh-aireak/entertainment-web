import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
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
import { WatchRoomSessionContext, type WatchRoomSessionContextValue } from './watchRoomSessionContextValue';

const livePosition = (event: RoomPlaybackChangedEvent): number => {
  if (!event.playing) return event.positionSeconds;
  const elapsed = (Date.now() - new Date(event.at).getTime()) / 1000;
  return Math.max(0, event.positionSeconds + elapsed * event.playbackRate);
};

export const WatchRoomSessionProvider: React.FC<React.PropsWithChildren> = ({ children }) => {
  const [room, setRoom] = useState<RoomResponse | null>(null);
  const [episodes, setEpisodes] = useState<EpisodeResponse[]>([]);
  const [videoSrc, setVideoSrc] = useState<string | null>(null);
  const [videoError, setVideoError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [changingEpisode, setChangingEpisode] = useState(false);
  const [videoRefreshKey, setVideoRefreshKey] = useState(0);
  const [playbackEvent, setPlaybackEvent] = useState<RoomPlaybackChangedEvent | null>(null);
  const [closedEvent, setClosedEvent] = useState<RoomClosedEvent | null>(null);

  const roomRef = useRef<RoomResponse | null>(null);
  const requestIdRef = useRef(0);

  useEffect(() => {
    roomRef.current = room;
  }, [room]);

  const clearSessionState = useCallback(() => {
    requestIdRef.current += 1;
    roomRef.current = null;
    setRoom(null);
    setEpisodes([]);
    setVideoSrc(null);
    setVideoError(null);
    setLoading(false);
    setErrorMessage(null);
    setChangingEpisode(false);
    setPlaybackEvent(null);
    setClosedEvent(null);
  }, []);

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
    try {
      const response = await roomService.joinRoom(roomId, { inviteCode });
      if (requestIdRef.current !== requestId) return;
      roomRef.current = response.result;
      setRoom(response.result);
    } catch (error: unknown) {
      if (requestIdRef.current !== requestId) return;
      const apiError = error as { response?: { data?: { message?: string } } };
      setErrorMessage(apiError.response?.data?.message || 'Không thể vào phòng này.');
    } finally {
      if (requestIdRef.current === requestId) setLoading(false);
    }
  }, []);

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
      const timer = window.setTimeout(() => setVideoError('Tập phim này chưa có video.'), 0);
      return () => window.clearTimeout(timer);
    }

    let cancelled = false;
    fileService.getFileInfo(episode.videoFileId)
      .then((response) => {
        if (!cancelled) setVideoSrc(response.result.url);
      })
      .catch(() => {
        if (!cancelled) setVideoError('Không thể tải video, vui lòng thử lại sau.');
      });
    return () => { cancelled = true; };
  }, [episodes, room?.episodeId, videoRefreshKey]);

  useRoomSocket(room?.id, room?.status === 'ACTIVE', {
    onPlayback: (event) => {
      if (event.episodeId && event.episodeId !== roomRef.current?.episodeId) {
        setVideoSrc(null);
        setVideoError(null);
      }
      setRoom((current) => {
        if (!current) return current;
        const updated: RoomResponse = {
          ...current,
          episodeId: event.episodeId ?? current.episodeId,
          playing: event.playing,
          positionSeconds: livePosition(event),
          playbackRate: event.playbackRate,
          lastActionAt: event.at,
        };
        roomRef.current = updated;
        return updated;
      });
      setPlaybackEvent(event);
    },
    onClosed: (event) => {
      setClosedEvent(event);
      setVideoSrc(null);
      setRoom((current) => current ? { ...current, status: 'CLOSED', playing: false } : current);
    },
  });

  const sendPlayback = useCallback((request: PlaybackUpdateRequest) => {
    const activeRoom = roomRef.current;
    if (!activeRoom?.host || activeRoom.status !== 'ACTIVE') return;
    void roomService.updatePlayback(activeRoom.id, request).catch(() => {});
  }, []);

  const changeEpisode = useCallback(async (episodeId: string) => {
    const activeRoom = roomRef.current;
    if (!activeRoom?.host || !episodeId || episodeId === activeRoom.episodeId) return;
    setChangingEpisode(true);
    try {
      await roomService.updatePlayback(activeRoom.id, { action: 'CHANGE_EPISODE', episodeId });
    } finally {
      setChangingEpisode(false);
    }
  }, []);

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
    playbackEvent,
    closedEvent,
    openRoom,
    changeEpisode,
    sendPlayback,
    refreshVideo,
    leaveSession,
    closeSession,
  }), [
    changeEpisode, changingEpisode, closeSession, closedEvent, episodes, errorMessage,
    leaveSession, loading, openRoom, playbackEvent,
    refreshVideo, room, sendPlayback, videoError, videoSrc,
  ]);

  return <WatchRoomSessionContext.Provider value={value}>{children}</WatchRoomSessionContext.Provider>;
};
