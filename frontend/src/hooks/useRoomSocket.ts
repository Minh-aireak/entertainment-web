import { useEffect, useRef } from 'react';
import { useWebSocket } from '../contexts/WebSocketContext';
import type {
  RoomClosedEvent,
  RoomMessageResponse,
  RoomParticipantChangedEvent,
  RoomPlaybackChangedEvent,
} from '../models';

const ROOM_PLAYBACK_EVENT = 'room:playback';
const ROOM_PARTICIPANTS_EVENT = 'room:participants';
const ROOM_MESSAGE_EVENT = 'room:message';
const ROOM_CLOSED_EVENT = 'room:closed';
const WATCH_ROOM_PRESENCE_HEARTBEAT_MS = 30_000;

export interface RoomSocketCallbacks {
  onPlayback?: (event: RoomPlaybackChangedEvent) => void;
  onParticipants?: (event: RoomParticipantChangedEvent) => void;
  onMessage?: (message: RoomMessageResponse) => void;
  onClosed?: (event: RoomClosedEvent) => void;
  /** Fired after every join-watch-room past the first one for the current roomId - i.e. on a WebSocket
   *  reconnect (isConnected flipping back to true), not on the initial join. The caller is
   *  expected to refetch an authoritative snapshot (roomService.getRoom) here, since a broadcast
   *  missed while disconnected (e.g. a host PAUSE) would otherwise never be recovered - the
   *  socket layer has no message backlog/replay. */
  onReconnected?: () => void;
}

/**
 * Joins/leaves a token-protected watch-together channel on the app-wide WebSocket connection.
 * The dedicated join-watch-room protocol keeps it isolated from the generic join-room channels
 * used by chat and comments. Playback control and chat
 * messages are still sent via REST (roomService); every viewer, including the actor, only
 * applies the change once the corresponding broadcast lands here.
 *
 * `token` (watch-together only - chat/comment callers omit it) is a short-lived room-service-
 * issued subscription token (RoomResponse.wsToken) proving the caller may join this specific
 * room; socket-service requires and verifies it before admitting the join.
 */
export const useRoomSocket = (
  roomId: string | undefined,
  enabled: boolean,
  callbacks: RoomSocketCallbacks,
  token?: string,
) => {
  const { send, subscribe, isConnected } = useWebSocket();

  const onPlaybackRef = useRef(callbacks.onPlayback);
  const onParticipantsRef = useRef(callbacks.onParticipants);
  const onMessageRef = useRef(callbacks.onMessage);
  const onClosedRef = useRef(callbacks.onClosed);
  const onReconnectedRef = useRef(callbacks.onReconnected);
  onPlaybackRef.current = callbacks.onPlayback;
  onParticipantsRef.current = callbacks.onParticipants;
  onMessageRef.current = callbacks.onMessage;
  onClosedRef.current = callbacks.onClosed;
  onReconnectedRef.current = callbacks.onReconnected;

  // Tracks whether this roomId has already completed a join once, so the very first join isn't
  // mistaken for a reconnect - reset whenever roomId changes.
  const hasJoinedOnceRef = useRef(false);
  const joinedCurrentConnectionRef = useRef(false);
  useEffect(() => {
    hasJoinedOnceRef.current = false;
    joinedCurrentConnectionRef.current = false;
  }, [roomId]);

  // Declared before the join-watch-room effect below so subscriptions are registered first on every
  // render - a room:playback broadcast that lands right after joining is never missed waiting
  // for this effect to run.
  useEffect(() => {
    if (!enabled || !roomId) return;

    const unsubscribers = [
      subscribe(ROOM_PLAYBACK_EVENT, (data: RoomPlaybackChangedEvent) => {
        if (data?.roomId !== roomId) return;
        onPlaybackRef.current?.(data);
      }),
      subscribe(ROOM_PARTICIPANTS_EVENT, (data: RoomParticipantChangedEvent) => {
        if (data?.roomId !== roomId) return;
        onParticipantsRef.current?.(data);
      }),
      subscribe(ROOM_MESSAGE_EVENT, (data: RoomMessageResponse) => {
        if (data?.roomId !== roomId) return;
        onMessageRef.current?.(data);
      }),
      subscribe(ROOM_CLOSED_EVENT, (data: RoomClosedEvent) => {
        if (data?.roomId !== roomId) return;
        onClosedRef.current?.(data);
      }),
    ];

    return () => unsubscribers.forEach((unsubscribe) => unsubscribe());
  }, [enabled, roomId, subscribe]);

  useEffect(() => {
    if (!enabled || !roomId || !isConnected) {
      if (!isConnected) joinedCurrentConnectionRef.current = false;
      return;
    }

    if (!token) return;
    const isReconnect = hasJoinedOnceRef.current && !joinedCurrentConnectionRef.current;
    send({ type: 'join-watch-room', roomId, token });

    if (isReconnect) {
      onReconnectedRef.current?.();
    }
    hasJoinedOnceRef.current = true;
    joinedCurrentConnectionRef.current = true;
    const presenceTimer = window.setInterval(() => {
      send({ type: 'watch-room-heartbeat', roomId });
    }, WATCH_ROOM_PRESENCE_HEARTBEAT_MS);

    return () => {
      window.clearInterval(presenceTimer);
      send({ type: 'leave-watch-room', roomId });
    };
  }, [enabled, roomId, isConnected, send, token]);
};
