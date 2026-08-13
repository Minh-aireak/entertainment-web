import React, { useEffect, useRef, useState } from 'react';
import {
  alpha,
  Box,
  Typography,
  Container,
  Grid,
  Skeleton,
  Button,
  Alert,
  useTheme,
} from '@mui/material';
import { History, Refresh, Search } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { filmService } from '../../api/filmService';
import type { FilmSummaryResponse, WatchProgressResponse } from '../../models';
import { useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import FilmCard from '../../components/Film/FilmCard';
import ContinueWatchingCard from '../../components/Film/ContinueWatchingCard';
import { clearPersistedState, usePersistedState } from '../../hooks/usePersistedState';

const LIBRARY_CACHE_KEY = 'filmLibrary:films';

// Registered once at module load (not tied to this component's mount state) so the
// cached library list is dropped as soon as a follow/unfollow happens anywhere in the
// app (e.g. from FilmDetail), even if the Library page isn't mounted at the time.
if (typeof window !== 'undefined') {
  window.addEventListener('sidebar-counts:refresh', () => {
    clearPersistedState(LIBRARY_CACHE_KEY);
  });
}

const FilmLibrary: React.FC = React.memo(() => {
  const { t } = useTranslation();
  const theme = useTheme();
  const [films, setFilms] = usePersistedState<FilmSummaryResponse[]>(LIBRARY_CACHE_KEY, []);
  const [loading, setLoading] = useState(films.length === 0);
  const [error, setError] = useState<string | null>(null);
  const skipInitialFetchRef = useRef(films.length > 0);
  const navigate = useNavigate();

  const [continueWatching, setContinueWatching] = useState<WatchProgressResponse[]>([]);
  const [continueWatchingLoading, setContinueWatchingLoading] = useState(true);

  const fetchLibrary = async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await filmService.getMyFollowedFilms();
      setFilms(response.result ?? []);
    } catch (error) {
      console.error('Failed to fetch library:', error);
      setError(t('libraryLoadFailed'));
    } finally {
      setLoading(false);
    }
  };

  const fetchContinueWatching = async () => {
    setContinueWatchingLoading(true);
    try {
      const response = await filmService.getMyContinueWatching();
      setContinueWatching(response.result ?? []);
    } catch (error) {
      console.error('Failed to fetch continue watching:', error);
      toast.error(t('continueWatchingLoadFailed'));
    } finally {
      setContinueWatchingLoading(false);
    }
  };

  useEffect(() => {
    if (skipInitialFetchRef.current) {
      skipInitialFetchRef.current = false;
    } else {
      fetchLibrary();
    }
    fetchContinueWatching();
  }, []);

  const handleRemove = async (film: FilmSummaryResponse) => {
    try {
      await filmService.processFollowAction(film.id, true);
      setFilms((prev) => prev.filter((f) => f.id !== film.id));
      window.dispatchEvent(new Event('sidebar-counts:refresh'));
      toast.success(t('removedFromLibrary'));
    } catch {
      toast.error(t('removeFromLibraryFailed'));
    }
  };

  const handleRemoveContinueWatching = async (progress: WatchProgressResponse) => {
    try {
      await filmService.removeWatchProgress(progress.filmId);
      setContinueWatching((prev) => prev.filter((p) => p.filmId !== progress.filmId));
      toast.success(t('removedFromContinueWatching'));
    } catch {
      toast.error(t('removeFromContinueWatchingFailed'));
    }
  };

  return (
    <Container maxWidth="xl" sx={{ py: 6 }}>
      <Box sx={{ mb: 6, display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end' }}>
        <Box>
          <Typography variant="h3" sx={{ fontWeight: 900, mb: 1 }}>{t('myLibrary')}</Typography>
          <Typography variant="body1" color="text.secondary">{t('savedMovies')}</Typography>
        </Box>
        <Typography variant="h6" sx={{ color: 'primary.main', fontWeight: 700 }}>
          {t('showingFilms', { count: films.length })}
        </Typography>
      </Box>

      {(continueWatchingLoading || continueWatching.length > 0) && (
        <Box sx={{ mb: 6 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 3 }}>
            <History sx={{ color: 'primary.main' }} />
            <Typography variant="h5" sx={{ fontWeight: 800 }}>{t('continueWatching')}</Typography>
          </Box>
          {continueWatchingLoading ? (
            <Grid container spacing={3}>
              {Array.from(new Array(4)).map((_, index) => (
                <Grid size={{ xs: 12, sm: 6, md: 4, lg: 3 }} key={index}>
                  <Skeleton variant="rectangular" sx={{ aspectRatio: '2 / 3', borderRadius: 3 }} />
                  <Skeleton variant="text" sx={{ mt: 2 }} />
                  <Skeleton variant="text" width="60%" />
                </Grid>
              ))}
            </Grid>
          ) : (
            <Grid container spacing={3}>
              {continueWatching.map((progress) => (
                <Grid size={{ xs: 12, sm: 6, md: 4, lg: 3 }} key={progress.filmId}>
                  <ContinueWatchingCard progress={progress} onRemove={handleRemoveContinueWatching} />
                </Grid>
              ))}
            </Grid>
          )}
        </Box>
      )}

      {!loading && films.length > 0 && (
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 3 }}>
          <Search sx={{ color: 'primary.main' }} />
          <Typography variant="h5" sx={{ fontWeight: 800 }}>{t('followedFilmsHeading')}</Typography>
        </Box>
      )}

      {error && (
        <Alert
          severity="error"
          action={<Button color="inherit" size="small" startIcon={<Refresh />} onClick={fetchLibrary}>{t('retry')}</Button>}
          sx={{ mb: 3 }}
        >
          {error}
        </Alert>
      )}

      {loading ? (
        <Grid container spacing={3}>
          {Array.from(new Array(8)).map((_, index) => (
            <Grid size={{ xs: 12, sm: 6, md: 4, lg: 3 }} key={index}>
              <Skeleton variant="rectangular" sx={{ aspectRatio: '2 / 3', borderRadius: 3 }} />
              <Skeleton variant="text" sx={{ mt: 2 }} />
              <Skeleton variant="text" width="60%" />
            </Grid>
          ))}
        </Grid>
      ) : films.length === 0 ? (
        <Box
          sx={{
            textAlign: 'center',
            py: 12,
            bgcolor: alpha(theme.palette.text.primary, 0.02),
            borderRadius: 8,
            border: '2px dashed',
            borderColor: alpha(theme.palette.text.primary, 0.05),
          }}
        >
          <Search sx={{ fontSize: 80, color: alpha(theme.palette.text.primary, 0.1), mb: 2 }} />
          <Typography variant="h5" sx={{ mb: 1, fontWeight: 700 }}>{t('libraryEmptyTitle')}</Typography>
          <Typography variant="body1" color="text.secondary" sx={{ mb: 4 }}>
            {t('libraryEmptyDescription')}
          </Typography>
          <Button
            variant="contained"
            size="large"
            onClick={() => navigate('/film')}
            sx={{ borderRadius: 2, px: 4 }}
          >
            {t('exploreFilms')}
          </Button>
        </Box>
      ) : (
        <Grid container spacing={3}>
          {films.map((film) => (
            <Grid size={{ xs: 12, sm: 6, md: 4, lg: 3 }} key={film.id}>
              <FilmCard film={film} onRemove={handleRemove} />
            </Grid>
          ))}
        </Grid>
      )}
    </Container>
  );
});

FilmLibrary.displayName = 'FilmLibrary';

export default FilmLibrary;
