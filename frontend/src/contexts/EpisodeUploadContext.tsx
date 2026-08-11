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

const errorMessage = (error: unknown): string =>
  error instanceof Error ? error.message : 'Có lỗi xảy ra trong quá trình upload tập phim';

export const EpisodeUploadProvider: React.FC<React.PropsWithChildren> = ({ children }) => {
  const [job, setJob] = useState<EpisodeUploadJob | null>(null);
  const busyRef = useRef(false);

  const startUpload = useCallback(async (input: EpisodeUploadInput) => {
    if (busyRef.current) {
      toast.error('Một tập phim khác đang được upload. Vui lòng chờ tác vụ hiện tại hoàn tất.');
      return;
    }

    busyRef.current = true;
    const durationMinutes = Math.floor(input.durationSeconds / 60);
    setJob({ ...input, durationMinutes, progress: 0, status: 'uploading' });
    toast.loading('Đang upload video...', { id: 'episode-upload' });

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
        input.mode === 'edit' ? 'Đang cập nhật tập phim...' : 'Đang tạo tập phim...',
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
        throw new Error(response.message || 'Không thể lưu thông tin tập phim');
      }

      setJob((current) => current ? { ...current, progress: 100, status: 'success' } : current);
      toast.success(
        input.mode === 'edit' ? 'Cập nhật tập phim thành công' : 'Thêm tập phim thành công',
        { id: 'episode-upload' },
      );
    } catch (error: unknown) {
      const message = errorMessage(error);
      setJob((current) => current ? { ...current, status: 'error', error: message } : current);
      toast.error(message, { id: 'episode-upload' });
    } finally {
      busyRef.current = false;
    }
  }, []);

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
