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

export interface RoomSocketCallbacks {
  onPlayback?: (event: RoomPlaybackChangedEvent) => void;
  onParticipants?: (event: RoomParticipantChangedEvent) => void;
  onMessage?: (message: RoomMessageResponse) => void;
  onClosed?: (event: RoomClosedEvent) => void;
}

/**
 * Joins/leaves the realtime room for a watch-together roomId on the WebSocket connection
 * already established app-wide (WebSocketContext) - the same generic "join-room"/"leave-room"
 * room engine chat and comments use (see useCommentSocket.ts). Playback control and chat
 * messages are still sent via REST (roomService); every viewer, including the actor, only
 * applies the change once the corresponding broadcast lands here.
 */
export const useRoomSocket = (roomId: string | undefined, enabled: boolean, callbacks: RoomSocketCallbacks) => {
  const { send, subscribe, isConnected } = useWebSocket();

  const onPlaybackRef = useRef(callbacks.onPlayback);
  const onParticipantsRef = useRef(callbacks.onParticipants);
  const onMessageRef = useRef(callbacks.onMessage);
  const onClosedRef = useRef(callbacks.onClosed);
  onPlaybackRef.current = callbacks.onPlayback;
  onParticipantsRef.current = callbacks.onParticipants;
  onMessageRef.current = callbacks.onMessage;
  onClosedRef.current = callbacks.onClosed;

  useEffect(() => {
    if (!enabled || !roomId || !isConnected) return;

    send({ type: 'join-room', roomId });

    return () => {
      send({ type: 'leave-room', roomId });
    };
  }, [enabled, roomId, isConnected, send]);

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
};
