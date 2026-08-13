import React, { useCallback, useMemo, useRef, useState } from 'react';
import toast from 'react-hot-toast';
import { fileService } from '../api/fileService';
import { filmService } from '../api/filmService';
import {
  EpisodeUploadContext,
  type EpisodeUploadContextValue,
  type EpisodeUploadInput,
  type EpisodeUploadJob,
} from './episodeUploadContextValue';
import { useTranslation } from 'react-i18next';

export const EpisodeUploadProvider: React.FC<React.PropsWithChildren> = ({ children }) => {
  const { t } = useTranslation();
  const [job, setJob] = useState<EpisodeUploadJob | null>(null);
  const busyRef = useRef(false);

  const startUpload = useCallback(async (input: EpisodeUploadInput) => {
    if (busyRef.current) {
      toast.error(t('uploadInProgress'));
      return;
    }

    busyRef.current = true;
    const durationMinutes = Math.floor(input.durationSeconds / 60);
    setJob({ ...input, durationMinutes, progress: 0, status: 'uploading' });
    toast.loading(t('uploadingVideo'), { id: 'episode-upload' });

    try {
      const uploadResult = await fileService.uploadFileDirect(
        input.file,
        (progress) => {
          setJob((current) => current ? { ...current, progress } : current);
        },
        { enableHls: true, durationSeconds: input.durationSeconds },
      );

      setJob((current) => current ? { ...current, progress: 100, status: 'saving' } : current);
      toast.loading(
        input.mode === 'edit' ? t('updatingEpisode') : t('creatingEpisode'),
        { id: 'episode-upload' },
      );

      const request = {
        seasonNumber: input.seasonNumber,
        episodeNumber: input.episodeNumber,
        title: input.title,
        videoFileId: uploadResult.id,
        durationMinutes: Math.floor((uploadResult.duration ?? input.durationSeconds) / 60),
        filmId: input.filmId,
      };

      const response = input.mode === 'edit' && input.episodeId
        ? await filmService.updateEpisode(input.episodeId, request)
        : await filmService.createEpisode(request);

      if (response.code !== 1000) {
        throw new Error(response.message || t('episodeSaveFailed'));
      }

      setJob((current) => current ? { ...current, progress: 100, status: 'success' } : current);
      toast.success(
        input.mode === 'edit' ? t('episodeUpdated') : t('episodeCreated'),
        { id: 'episode-upload' },
      );
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : t('episodeUploadError');
      setJob((current) => current ? { ...current, status: 'error', error: message } : current);
      toast.error(message, { id: 'episode-upload' });
    } finally {
      busyRef.current = false;
    }
  }, [t]);

  const clearJob = useCallback(() => {
    if (!busyRef.current) setJob(null);
  }, []);

  const value = useMemo<EpisodeUploadContextValue>(() => ({
    job,
    isBusy: job?.status === 'uploading' || job?.status === 'saving',
    startUpload,
    clearJob,
  }), [clearJob, job, startUpload]);

  return <EpisodeUploadContext.Provider value={value}>{children}</EpisodeUploadContext.Provider>;
};
