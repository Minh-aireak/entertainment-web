import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useSelector, useDispatch } from 'react-redux';
import { setFilmAggregateData, setFilmLoading, setNowPlayingFilms, type RootState } from '../../store';
import { Alert, Box, Typography, Button, IconButton, useTheme, alpha, Container, Skeleton, Paper } from '@mui/material';
import { MovieFilter, PlayArrow, Refresh, Star } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { filmService } from '../../api/filmService';
import { useNavigate } from 'react-router-dom';
import FilmRow from '../../components/Film/FilmRow';

const HERO_ROTATE_MS = 7000;

const FilmHome: React.FC = React.memo(() => {
  const { t } = useTranslation();
  const dispatch = useDispatch();
  const { aggregateData: data, nowPlayingFilms, loading } = useSelector((state: RootState) => state.film);
  const navigate = useNavigate();
  const theme = useTheme();
  const [heroIndex, setHeroIndex] = useState(0);
  const [error, setError] = useState<string | null>(null);

  const fetchData = useCallback(async (force = false) => {
    if (!data || force) {
      dispatch(setFilmLoading(true));
    }
    setError(null);
    try {
      const [aggregateRes, nowPlayingRes] = await Promise.all([
        filmService.getAggregateFilms(),
        filmService.getNowPlayingFilms(),
      ]);
      dispatch(setFilmAggregateData(aggregateRes.result));
      dispatch(setNowPlayingFilms(nowPlayingRes.result));
    } catch (error) {
      console.error('Failed to fetch films:', error);
      setError('Không thể tải dữ liệu phim. Vui lòng thử lại.');
    } finally {
      dispatch(setFilmLoading(false));
    }
  }, [dispatch, data]);

  useEffect(() => {
    if (!data || !nowPlayingFilms) {
      fetchData();
    } else {
      fetchData(false); // Background update
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const heroFilms = useMemo(() => data?.topHotFilms.data.slice(0, 5) ?? [], [data]);

  useEffect(() => {
    setHeroIndex(0);
  }, [heroFilms.length]);

  useEffect(() => {
    if (heroFilms.length < 2) return;
    const interval = setInterval(() => {
      setHeroIndex((prev) => (prev + 1) % heroFilms.length);
    }, HERO_ROTATE_MS);
    return () => clearInterval(interval);
  }, [heroFilms.length]);

  const featuredFilm = heroFilms[heroIndex];

  return (
    <Box sx={{ bgcolor: 'background.default', minHeight: '100vh', pb: 8 }}>
      {error && (
        <Container maxWidth="xl" sx={{ pt: 3 }}>
          <Alert severity="error" action={<Button color="inherit" size="small" startIcon={<Refresh />} onClick={() => fetchData(true)}>Thử lại</Button>}>
            {error}
          </Alert>
        </Container>
      )}
      {/* Hero Section */}
      {loading && !data ? (
        <Skeleton
          variant="rounded"
          sx={{ height: '70vh', width: '100%', mb: 6, borderRadius: { xs: 0, md: 4 }, mt: { xs: 0, md: 2 } }}
        />
      ) : (
        featuredFilm ? (
          <Box
            sx={{
              height: '70vh',
              width: '100%',
              position: 'relative',
              mb: 6,
              overflow: 'hidden',
              borderRadius: { xs: 0, md: 4 },
              mt: { xs: 0, md: 2 },
            }}
          >
            {heroFilms.map((film, index) => (
              <Box
                key={film.id}
                component="img"
                src={film.thumbnailUrl}
                sx={{
                  position: 'absolute',
                  inset: 0,
                  width: '100%',
                  height: '100%',
                  objectFit: 'cover',
                  opacity: index === heroIndex ? 1 : 0,
                  transition: 'opacity 1s ease-in-out',
                }}
              />
            ))}
            <Box
              sx={{
                position: 'absolute',
                top: 0,
                left: 0,
                right: 0,
                bottom: 0,
                background: `linear-gradient(to right, ${alpha(theme.palette.background.default, 0.9)} 0%, ${alpha(theme.palette.background.default, 0.4)} 50%, transparent 100%), linear-gradient(to top, ${theme.palette.background.default} 0%, transparent 30%)`,
                display: 'flex',
                flexDirection: 'column',
                justifyContent: 'center',
                px: { xs: 4, md: 8 },
              }}
            >
              <Typography variant="overline" sx={{ color: 'primary.main', fontWeight: 800, letterSpacing: 2, mb: 1 }}>
                {t('featuredMovie')}
              </Typography>
              <Typography
                variant="h1"
                sx={{
                  color: 'white',
                  fontWeight: 900,
                  mb: 2,
                  maxWidth: '600px',
                  fontSize: { xs: '2.5rem', md: '4rem' },
                  lineHeight: 1.1,
                }}
              >
                {featuredFilm.title}
              </Typography>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 4 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                  <Star sx={{ color: '#FFD700' }} />
                  <Typography sx={{ color: 'white', fontWeight: 700 }}>{featuredFilm.averageRating.toFixed(1)}</Typography>
                </Box>
                <Typography sx={{ color: 'rgba(255,255,255,0.7)' }}>
                  {featuredFilm.episodeCount > 0 ? `${featuredFilm.episodeCount} Episodes` : 'Movie'} •{' '}
                  {new Date(featuredFilm.lastUpdate).getFullYear()}
                </Typography>
              </Box>
              <Box sx={{ display: 'flex', gap: 2, mb: 4 }}>
                <Button
                  variant="contained"
                  size="large"
                  startIcon={<PlayArrow />}
                  onClick={() => navigate(`/film/${featuredFilm.id}`)}
                  sx={{ bgcolor: 'primary.main', px: 4, py: 1.5, fontSize: '1.1rem', borderRadius: 2 }}
                >
                  {t('watchNow')}
                </Button>
                <Button
                  variant="outlined"
                  size="large"
                  sx={{
                    borderColor: 'white',
                    color: 'white',
                    px: 4,
                    py: 1.5,
                    fontSize: '1.1rem',
                    borderRadius: 2,
                    '&:hover': { borderColor: 'white', bgcolor: 'rgba(255,255,255,0.1)' },
                  }}
                  onClick={() => navigate(`/film/${featuredFilm.id}`)}
                >
                  {t('moreInfo')}
                </Button>
              </Box>

              {heroFilms.length > 1 && (
                <Box sx={{ display: 'flex', gap: 1 }}>
                  {heroFilms.map((film, index) => (
                    <IconButton
                      key={film.id}
                      size="small"
                      onClick={() => setHeroIndex(index)}
                      sx={{ p: 0.5 }}
                      aria-label={film.title}
                    >
                      <Box
                        sx={{
                          width: index === heroIndex ? 24 : 8,
                          height: 8,
                          borderRadius: 4,
                          bgcolor: index === heroIndex ? 'primary.main' : 'rgba(255,255,255,0.3)',
                          transition: 'all 0.3s ease',
                        }}
                      />
                    </IconButton>
                  ))}
                </Box>
              )}
            </Box>
          </Box>
        ) : !loading && (
          <Container maxWidth="xl" sx={{ py: 6 }}>
            <Paper sx={{ minHeight: 360, borderRadius: 4, display: 'grid', placeItems: 'center', textAlign: 'center', p: 4, bgcolor: 'rgba(255,255,255,0.03)' }}>
              <Box>
                <MovieFilter sx={{ fontSize: 72, color: 'text.disabled', mb: 2 }} />
                <Typography variant="h5" sx={{ fontWeight: 800, mb: 1 }}>Kho phim đang được cập nhật</Typography>
                <Typography color="text.secondary">Các bộ phim mới sẽ xuất hiện tại đây khi quản trị viên thêm dữ liệu.</Typography>
              </Box>
            </Paper>
          </Container>
        )
      )}

      <Container maxWidth="xl">
        <FilmRow title={t('nowPlaying')} films={nowPlayingFilms?.data || []} loading={loading && !nowPlayingFilms} onSeeAll={() => navigate('/film/trending')} />
        <FilmRow title={t('topHotFilms')} films={data?.topHotFilms.data || []} loading={loading && !data} onSeeAll={() => navigate('/film/trending')} />
        <FilmRow title={t('latestUpdates')} films={data?.latestFilms.data || []} loading={loading && !data} />
      </Container>
    </Box>
  );
});

FilmHome.displayName = 'FilmHome';

export default FilmHome;
