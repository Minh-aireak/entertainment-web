import { createContext, useContext } from 'react';
import type {
  EpisodeResponse,
  PlaybackUpdateRequest,
  RoomClosedEvent,
  RoomPlaybackChangedEvent,
  RoomResponse,
} from '../models';

export interface WatchRoomSessionContextValue {
  room: RoomResponse | null;
  episodes: EpisodeResponse[];
  videoSrc: string | null;
  videoError: string | null;
  loading: boolean;
  errorMessage: string | null;
  changingEpisode: boolean;
  playbackEvent: RoomPlaybackChangedEvent | null;
  closedEvent: RoomClosedEvent | null;
  openRoom: (roomId: string, inviteCode?: string) => Promise<void>;
  changeEpisode: (episodeId: string) => Promise<void>;
  sendPlayback: (request: PlaybackUpdateRequest) => void;
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
