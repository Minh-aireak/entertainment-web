import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useParams, useNavigate, Link as RouterLink } from 'react-router-dom';
import {
  Box,
  Typography,
  Container,
  Paper,
  CircularProgress,
  Rating,
  Divider,
  Breadcrumbs,
  Link,
  alpha,
  useTheme,
  Tabs,
  Tab,
} from '@mui/material';
import { ArrowBack, Star } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { filmService } from '../../api/filmService';
import { fileService } from '../../api/fileService';
import type { EpisodeResponse, FilmDetailResponse, WatchProgressResponse } from '../../models';
import toast from 'react-hot-toast';
import CommentSection from '../../components/Comment/CommentSection';
import VideoPlayer from '../../components/Film/VideoPlayer';
import EpisodePicker from '../../components/Film/EpisodePicker';

const VIETSUB_STRIP_REGEX = /\s*(\||\-|\(|\[)?\s*(Việt\s*Sub|VIETSUB|Vietsub|Viet\s*Sub)\s*(\]|\))?\s*$/i;

// How often watch position is persisted for "Continue Watching" while playing - frequent enough
// to survive a crash/tab-close without losing much progress, infrequent enough not to hammer the
// backend (native `timeupdate` fires several times a second).
const WATCH_PROGRESS_REPORT_INTERVAL_MS = 20000;

const stripVietSub = (title: string) => title?.replace?.(VIETSUB_STRIP_REGEX, '')?.trim() ?? title;

const FilmWatch: React.FC = () => {
  const { t } = useTranslation();
  const { id, episodeId } = useParams<{ id: string; episodeId: string }>();
  const navigate = useNavigate();
  const theme = useTheme();

  const [data, setData] = useState<FilmDetailResponse | null>(null);
  const [episodes, setEpisodes] = useState<EpisodeResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [userRating, setUserRating] = useState<number | null>(null);
  const [activeTab, setActiveTab] = useState(0);
  const [videoSrc, setVideoSrc] = useState<string | null>(null);
  const [videoError, setVideoError] = useState<string | null>(null);

  const lastProgressReportRef = useRef(0);
  const latestProgressRef = useRef<{ positionSeconds: number; durationSeconds: number } | null>(null);
  const [savedProgress, setSavedProgress] = useState<WatchProgressResponse | null>(null);

  useEffect(() => {
    if (!id) return;
    let cancelled = false;
    filmService
      .getWatchProgress(id)
      .then((response) => {
        if (!cancelled) setSavedProgress(response.result ?? null);
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [id]);

  useEffect(() => {
    const fetchDetail = async () => {
      if (!id) return;
      setLoading(true);
      try {
        const [detailResponse, episodesResponse] = await Promise.all([
          filmService.getFilmAggregate(id),
          filmService.getEpisodesByFilm(id),
        ]);
        setData(detailResponse.result);
        setUserRating(detailResponse.result.userRating || null);
        setEpisodes(episodesResponse.result ?? []);
      } catch (error) {
        console.error('Failed to fetch film details:', error);
        toast.error(t('filmLoadFailed'));
      } finally {
        setLoading(false);
      }
    };
    fetchDetail();
  }, [id, t]);

  const sortedEpisodesNatural = useMemo(() => {
    return [...episodes].sort((a, b) =>
      a.seasonNumber === b.seasonNumber
        ? a.episodeNumber - b.episodeNumber
        : a.seasonNumber - b.seasonNumber,
    );
  }, [episodes]);

  useEffect(() => {
    if (loading || !episodes.length || !id) return;
    const exists = episodes.some((episode) => episode.id === episodeId);
    if (!exists) {
      const fallback = sortedEpisodesNatural[0];
      navigate(`/film/${id}/watch/${fallback.id}`, { replace: true });
    }
  }, [loading, episodes, episodeId, id, navigate, sortedEpisodesNatural]);

  // Bucket B2 private nên FileInfo.url là presigned GET có hạn dùng - phải resolve lại mỗi khi
  // đổi tập/vào lại trang thay vì dùng episode.videoFileId (chỉ là file ID) trực tiếp làm src.
  useEffect(() => {
    if (loading || !episodes.length) return;
    const episode = episodes.find((e) => e.id === episodeId) ?? episodes[0];

    setVideoSrc(null);
    setVideoError(null);

    if (!episode.videoFileId) {
      setVideoError(t('episodeVideoMissing'));
      return;
    }

    let cancelled = false;
    fileService
      .getFileInfo(episode.videoFileId)
      .then((response) => {
        if (!cancelled) setVideoSrc(response.result.url);
      })
      .catch(() => {
        if (!cancelled) setVideoError(t('videoLoadFailed'));
      });

    return () => {
      cancelled = true;
    };
  }, [loading, episodes, episodeId, t]);

  // Presigned URL hết hạn sau 1h hoặc B2 trả lỗi khi mất mạng giữa chừng - VideoPlayer tự phát
  // hiện qua sự kiện `error`/khi mạng có lại nhưng vẫn đứng, rồi gọi lại đây để lấy URL mới thay
  // vì chịu chết với URL cũ.
  const handleStalledError = useCallback(() => {
    if (!episodes.length) return;
    const episode = episodes.find((e) => e.id === episodeId) ?? episodes[0];
    if (!episode.videoFileId) return;
    fileService
      .getFileInfo(episode.videoFileId)
      .then((response) => setVideoSrc(response.result.url))
      .catch(() => {
        toast.error(t('reconnectingVideo'));
      });
  }, [episodes, episodeId, t]);

  // Persists to film_service's watch-progress endpoint so "Continue Watching" (Xem & Yêu thích)
  // can resume here later. Throttled to WATCH_PROGRESS_REPORT_INTERVAL_MS unless `force` (used to
  // flush the last known position on unmount/episode change below).
  const reportProgress = useCallback((positionSeconds: number, durationSeconds: number, force = false) => {
    if (!id || !episodes.length || !Number.isFinite(durationSeconds) || durationSeconds <= 0) return;
    const now = Date.now();
    if (!force && now - lastProgressReportRef.current < WATCH_PROGRESS_REPORT_INTERVAL_MS) return;
    lastProgressReportRef.current = now;
    const episode = episodes.find((e) => e.id === episodeId) ?? episodes[0];
    filmService
      .upsertWatchProgress({
        filmId: id,
        episodeId: episode.id,
        positionSeconds: Math.floor(positionSeconds),
        durationSeconds: Math.floor(durationSeconds),
      })
      .catch(() => {});
  }, [id, episodes, episodeId]);

  const handleLocalTimeUpdate = useCallback(
    (state: { positionSeconds: number; durationSeconds: number }) => {
      latestProgressRef.current = { positionSeconds: state.positionSeconds, durationSeconds: state.durationSeconds };
      reportProgress(state.positionSeconds, state.durationSeconds);
    },
    [reportProgress],
  );

  // Flush the last known position once when leaving this episode (switching episodes remounts
  // VideoPlayer via its `key`, so this covers both a real page unmount and an episode change).
  useEffect(() => {
    return () => {
      const latest = latestProgressRef.current;
      if (latest) reportProgress(latest.positionSeconds, latest.durationSeconds, true);
    };
  }, [episodeId, reportProgress]);

  const handleRating = async (newValue: number | null) => {
    if (!id || newValue === null) return;
    try {
      await filmService.rateFilm(id, newValue);
      setUserRating(newValue);
      toast.success(t('ratingUpdated'));
    } catch {
      toast.error(t('ratingUpdateFailed'));
    }
  };

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '80vh' }}>
        <CircularProgress color="primary" />
      </Box>
    );
  }

  if (!data || episodes.length === 0) {
    return (
      <Container sx={{ mt: 4 }}>
        <Typography variant="h5">{t('noEpisodeToWatch')}</Typography>
        <Link component={RouterLink} to={`/film/${id}`}>{t('backToFilm')}</Link>
      </Container>
    );
  }

  const { film } = data;
  const currentEpisode = episodes.find((episode) => episode.id === episodeId) ?? sortedEpisodesNatural[0];
  const currentEpisodeIndex = sortedEpisodesNatural.findIndex((episode) => episode.id === currentEpisode.id);
  const nextEpisode = currentEpisodeIndex >= 0 ? sortedEpisodesNatural[currentEpisodeIndex + 1] : undefined;

  return (
    <Box sx={{ bgcolor: 'background.default', minHeight: '100vh' }}>
      <Box sx={{ bgcolor: '#000' }}>
        <Container maxWidth="lg" sx={{ py: 1 }}>
          <Breadcrumbs sx={{ color: 'text.secondary' }}>
            <Link component={RouterLink} to={`/film/${id}`} underline="hover" color="inherit" sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
              <ArrowBack fontSize="small" /> {film.title}
            </Link>
            <Typography color="text.primary">
              {t('seasonEpisodeLabel', { season: currentEpisode.seasonNumber, episode: currentEpisode.episodeNumber })}
            </Typography>
          </Breadcrumbs>
        </Container>

        <Box sx={{ width: '100%', bgcolor: '#000', py: 2 }}>
          {videoError ? (
            <Box sx={{ maxWidth: 1120, mx: 'auto', aspectRatio: '16 / 9', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <Typography color="text.secondary">{videoError}</Typography>
            </Box>
          ) : videoSrc ? (
            <VideoPlayer
              key={currentEpisode.id}
              src={videoSrc}
              title={`${t('episodeLabel', { episode: currentEpisode.episodeNumber })} - ${stripVietSub(currentEpisode.title)}`}
              onNextEpisode={nextEpisode ? () => navigate(`/film/${id}/watch/${nextEpisode.id}`) : undefined}
              hasNextEpisode={!!nextEpisode}
              onStalledError={handleStalledError}
              onLocalTimeUpdate={handleLocalTimeUpdate}
              initialPositionSeconds={
                savedProgress?.episodeId === currentEpisode.id && savedProgress.positionSeconds > 5
                  ? savedProgress.positionSeconds
                  : undefined
              }
            />
          ) : (
            <Box sx={{ maxWidth: 1120, mx: 'auto', aspectRatio: '16 / 9', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <CircularProgress color="primary" />
            </Box>
          )}
        </Box>
      </Box>

      <Container maxWidth="lg" sx={{ py: 4 }}>
        <Box sx={{ mb: 4 }}>
          <Typography variant="h5" sx={{ fontWeight: 800 }}>{stripVietSub(currentEpisode.title)}</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
            {t('episodeMetadata', { season: currentEpisode.seasonNumber, episode: currentEpisode.episodeNumber, minutes: currentEpisode.durationMinutes || '--' })}
          </Typography>
        </Box>

        <Box sx={{ mb: 5 }}>
          <EpisodePicker
            episodes={episodes}
            currentEpisodeId={currentEpisode.id}
            onSelect={(episode) => navigate(`/film/${id}/watch/${episode.id}`)}
            title={t('chooseEpisode')}
            formatTitle={stripVietSub}
            maxGridHeight={360}
          />
        </Box>

        <Paper sx={{ borderRadius: 3, bgcolor: alpha(theme.palette.text.primary, 0.03), border: '1px solid', borderColor: alpha(theme.palette.text.primary, 0.05) }}>
          <Tabs
            value={activeTab}
            onChange={(_, value) => setActiveTab(value)}
            sx={{ px: 2, borderBottom: '1px solid', borderColor: 'divider' }}
          >
            <Tab label={t('commentsLabel')} />
            <Tab label={t('reviews')} />
          </Tabs>

          <Box sx={{ p: 3 }}>
            {activeTab === 0 && <CommentSection sourceId={id || ''} open />}

            {activeTab === 1 && (
              <Box>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 3 }}>
                  <Star sx={{ color: '#FFD700' }} />
                  <Typography sx={{ fontWeight: 700, fontSize: '1.4rem' }}>{film.averageRating.toFixed(1)}</Typography>
                  <Typography variant="body2" color="text.secondary">({t('ratingCount', { count: film.ratingCount })})</Typography>
                </Box>
                <Divider sx={{ mb: 3 }} />
                <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 1 }}>{t('yourRating')}</Typography>
                <Rating
                  name="watch-user-rating"
                  value={userRating}
                  precision={1}
                  size="large"
                  onChange={(_, newValue) => handleRating(newValue)}
                  emptyIcon={<Star style={{ opacity: 0.2, color: 'inherit' }} fontSize="inherit" />}
                />
                <Typography variant="body2" sx={{ mt: 1, color: 'text.secondary' }}>
                  {userRating ? `${t('yourRating')} ${userRating}/5` : t('rateThisFilm')}
                </Typography>
              </Box>
            )}
          </Box>
        </Paper>
      </Container>
    </Box>
  );
};

export default FilmWatch;
