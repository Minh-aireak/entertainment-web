import { createContext, useContext } from 'react';

export type EpisodeUploadStatus = 'uploading' | 'saving' | 'success' | 'error';

export interface EpisodeUploadInput {
  mode: 'create' | 'edit';
  episodeId?: string;
  filmId: string;
  seasonNumber: number;
  episodeNumber: number;
  title: string;
  file: File;
  durationSeconds: number;
  formattedDuration: string;
}

export interface EpisodeUploadJob extends EpisodeUploadInput {
  durationMinutes: number;
  progress: number;
  status: EpisodeUploadStatus;
  error?: string;
}

export interface EpisodeUploadContextValue {
  job: EpisodeUploadJob | null;
  isBusy: boolean;
  startUpload: (input: EpisodeUploadInput) => Promise<void>;
  clearJob: () => void;
}

export const EpisodeUploadContext = createContext<EpisodeUploadContextValue | null>(null);

export const useEpisodeUpload = (): EpisodeUploadContextValue => {
  const context = useContext(EpisodeUploadContext);
  if (!context) {
    throw new Error('useEpisodeUpload must be used within EpisodeUploadProvider');
  }
  return context;
};
