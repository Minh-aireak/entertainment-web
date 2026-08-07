import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Alert, Box, Typography, Button, useTheme, alpha, Container, Skeleton, Paper, IconButton } from '@mui/material';
import { MovieFilter, PlayArrow, Refresh, Star } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { filmService } from '../../api/filmService';
import type { FilmSummaryResponse } from '../../models';
import { useNavigate } from 'react-router-dom';
import FilmShowcaseSection from '../../components/Film/FilmShowcaseSection';
import { usePersistedState } from '../../hooks/usePersistedState';

const isInteractiveTarget = (target: EventTarget | null) =>
  target instanceof HTMLElement && !!target.closest('button, a, [role="button"]');

// Ảnh nền carousel là ảnh poster admin upload (thường độ phân giải thấp, ~300-500px), không được
// resize ở file-service - banner càng cao thì object-fit:cover càng phải phóng to ảnh nhiều hơn và
// càng bị vỡ nét. Giữ chiều cao vừa phải + giới hạn maxHeight để hạn chế hiện tượng này.
const HERO_HEIGHT = { xs: '46vh', sm: '50vh', md: '54vh' };
const HERO_MAX_HEIGHT = 640;

const HERO_ROTATE_MS = 4000;
const HERO_TRANSITION_MS = 700;
const HERO_DRAG_THRESHOLD_PCT = 0.2;
const HERO_DRAG_THRESHOLD_PX = 50;
const HERO_SWIPE_VELOCITY_MS = 300;
const HERO_SWIPE_VELOCITY_PX = 30;

const FilmHome: React.FC = React.memo(() => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const theme = useTheme();

  const [heroFilms, setHeroFilms] = usePersistedState<FilmSummaryResponse[]>('filmHome:heroFilms', []);
  const [heroLoading, setHeroLoading] = useState(heroFilms.length === 0);
  const skipInitialHeroFetchRef = useRef(heroFilms.length > 0);
  const [heroIndex, setHeroIndex] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [trackOffset, setTrackOffset] = useState(0);
  const [isDragging, setIsDragging] = useState(false);
  const [isTransitioning, setIsTransitioning] = useState(false);
  const [isJumping, setIsJumping] = useState(false);

  const containerRef = useRef<HTMLDivElement>(null);
  const dragStartX = useRef(0);
  const dragStartTranslate = useRef(0);
  const dragStartTime = useRef(0);
  const autoRotateTimer = useRef<ReturnType<typeof setInterval> | null>(null);
  const pendingResumeTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const listenersAttached = useRef(false);
  const rafId = useRef<number | null>(null);
  const touchDragActive = useRef(false);

  const slidesToRender = useMemo(() => {
    if (heroFilms.length < 2) return heroFilms;
    return [heroFilms[heroFilms.length - 1], ...heroFilms, heroFilms[0]];
  }, [heroFilms]);

  const displayIndex = heroFilms.length >= 2 ? heroIndex + 1 : 0;

  const fetchHero = useCallback(async () => {
    setHeroLoading(true);
    setError(null);
    try {
      const res = await filmService.getTopRatedFilms(5);
      setHeroFilms(res.result);
    } catch (err) {
      console.error('Failed to fetch top rated films:', err);
      setError('Không thể tải dữ liệu phim. Vui lòng thử lại.');
    } finally {
      setHeroLoading(false);
    }
  }, [setHeroFilms]);

  useEffect(() => {
    if (skipInitialHeroFetchRef.current) {
      skipInitialHeroFetchRef.current = false;
      return;
    }
    fetchHero();
  }, [fetchHero]);

  useEffect(() => {
    setHeroIndex(0);
  }, [heroFilms.length]);

  const clearPendingResume = useCallback(() => {
    if (pendingResumeTimer.current) {
      clearTimeout(pendingResumeTimer.current);
      pendingResumeTimer.current = null;
    }
  }, []);

  const pauseAutoRotate = useCallback(() => {
    if (autoRotateTimer.current) {
      clearInterval(autoRotateTimer.current);
      autoRotateTimer.current = null;
    }
    clearPendingResume();
  }, [clearPendingResume]);

  const startAutoRotate = useCallback(() => {
    pauseAutoRotate();
    if (heroFilms.length < 2) return;
    autoRotateTimer.current = setInterval(() => {
      setIsTransitioning(true);
      setHeroIndex((prev) => prev + 1);
    }, HERO_ROTATE_MS);
  }, [heroFilms.length, pauseAutoRotate]);

  const resumeAutoRotateSoon = useCallback(() => {
    clearPendingResume();
    pendingResumeTimer.current = setTimeout(() => {
      startAutoRotate();
    }, HERO_TRANSITION_MS + 20);
  }, [clearPendingResume, startAutoRotate]);

  useEffect(() => {
    startAutoRotate();
    return () => {
      pauseAutoRotate();
      clearPendingResume();
    };
  }, [startAutoRotate, pauseAutoRotate, clearPendingResume]);

  const getContainerWidth = useCallback(() => {
    return containerRef.current?.clientWidth ?? 0;
  }, []);

  const computeBaseTranslate = useCallback(() => {
    const width = getContainerWidth();
    return -displayIndex * width;
  }, [displayIndex, getContainerWidth]);

  const currentTranslate = useMemo(() => {
    return computeBaseTranslate() + trackOffset;
  }, [computeBaseTranslate, trackOffset]);

  const activeDotIndex = useMemo(() => {
    if (heroFilms.length < 2) return 0;
    if (isDragging) {
      const width = getContainerWidth() || 1;
      const effectiveIndex = Math.round((-(dragStartTranslate.current + trackOffset) / width) - 1);
      const clamped =
        ((effectiveIndex % heroFilms.length) + heroFilms.length) % heroFilms.length;
      return clamped;
    }
    return ((heroIndex % heroFilms.length) + heroFilms.length) % heroFilms.length;
  }, [heroFilms.length, isDragging, trackOffset, heroIndex, getContainerWidth]);

  const handleJumpAfterTransition = useCallback(() => {
    if (heroFilms.length < 2) return;
    setIsJumping(true);
    setIsDragging(false);
    setIsTransitioning(false);
    requestAnimationFrame(() => {
      setTrackOffset(0);
      if (heroIndex >= heroFilms.length) {
        setHeroIndex(heroIndex % heroFilms.length);
      } else if (heroIndex < 0) {
        setHeroIndex((heroIndex % heroFilms.length + heroFilms.length) % heroFilms.length);
      }
      requestAnimationFrame(() => {
        setIsJumping(false);
        resumeAutoRotateSoon();
      });
    });
  }, [heroFilms.length, heroIndex, resumeAutoRotateSoon]);

  const handleTransitionEnd = useCallback(() => {
    if (isJumping) return;
    setIsTransitioning(false);
    if (heroIndex >= heroFilms.length || heroIndex < 0) {
      handleJumpAfterTransition();
    } else {
      resumeAutoRotateSoon();
    }
  }, [isJumping, heroFilms.length, heroIndex, handleJumpAfterTransition, resumeAutoRotateSoon]);

  const setDelta = useCallback((next: number) => {
    const width = getContainerWidth() || 1;
    const threshold = Math.max(width * HERO_DRAG_THRESHOLD_PCT, HERO_DRAG_THRESHOLD_PX);
    const duration = performance.now() - dragStartTime.current;
    const isFastSwipe = duration < HERO_SWIPE_VELOCITY_MS && Math.abs(next - dragStartTranslate.current) > HERO_SWIPE_VELOCITY_PX;
    const effectiveDelta = next - dragStartTranslate.current;

    let nextHeroIndex = heroIndex;
    let shouldAdvance = false;

    if (effectiveDelta < -threshold || (isFastSwipe && effectiveDelta < -HERO_SWIPE_VELOCITY_PX)) {
      nextHeroIndex = heroIndex + 1;
      shouldAdvance = true;
    } else if (effectiveDelta > threshold || (isFastSwipe && effectiveDelta > HERO_SWIPE_VELOCITY_PX)) {
      nextHeroIndex = heroIndex - 1;
      shouldAdvance = true;
    }

    setIsDragging(false);
    setIsTransitioning(true);
    setTrackOffset(0);

    if (shouldAdvance) {
      setHeroIndex(nextHeroIndex);
    }
    resumeAutoRotateSoon();
  }, [heroIndex, getContainerWidth, resumeAutoRotateSoon]);

  const onGlobalMouseMove = useCallback((e: MouseEvent) => {
    if (rafId.current !== null) cancelAnimationFrame(rafId.current);
    rafId.current = requestAnimationFrame(() => {
      const deltaX = e.clientX - dragStartX.current;
      setTrackOffset(deltaX);
      rafId.current = null;
    });
  }, []);

  const onGlobalMouseUp = useCallback((e: MouseEvent) => {
    if (!listenersAttached.current) return;
    listenersAttached.current = false;
    window.removeEventListener('mousemove', onGlobalMouseMove);
    window.removeEventListener('mouseup', onGlobalMouseUp);
    if (rafId.current !== null) {
      cancelAnimationFrame(rafId.current);
      rafId.current = null;
    }
    const deltaX = e.clientX - dragStartX.current;
    setDelta(dragStartTranslate.current + deltaX);
  }, [onGlobalMouseMove, setDelta]);

  const startDrag = useCallback((clientX: number) => {
    if (heroFilms.length < 2) return;
    pauseAutoRotate();
    clearPendingResume();
    const width = getContainerWidth();
    dragStartX.current = clientX;
    dragStartTranslate.current = -displayIndex * width;
    dragStartTime.current = performance.now();
    setIsDragging(true);
    setIsTransitioning(false);
    setTrackOffset(0);
  }, [heroFilms.length, displayIndex, getContainerWidth, pauseAutoRotate, clearPendingResume]);

  const handleMouseDown = useCallback((e: React.MouseEvent) => {
    if (e.button !== 0) return;
    if (isInteractiveTarget(e.target)) return;
    startDrag(e.clientX);
    if (listenersAttached.current) return;
    listenersAttached.current = true;
    window.addEventListener('mousemove', onGlobalMouseMove, { passive: true });
    window.addEventListener('mouseup', onGlobalMouseUp, { passive: true });
    e.preventDefault();
  }, [startDrag, onGlobalMouseMove, onGlobalMouseUp]);

  const goToHeroIndex = useCallback((index: number) => {
    if (heroFilms.length < 2) return;
    pauseAutoRotate();
    clearPendingResume();
    setIsTransitioning(true);
    setHeroIndex(index);
    resumeAutoRotateSoon();
  }, [heroFilms.length, pauseAutoRotate, clearPendingResume, resumeAutoRotateSoon]);

  useEffect(() => {
    const el = containerRef.current;
    if (!el || heroFilms.length < 2) return;

    const onNativeTouchStart = (e: TouchEvent) => {
      if (isInteractiveTarget(e.target)) {
        touchDragActive.current = false;
        return;
      }
      const touch = e.touches[0];
      if (!touch) return;
      touchDragActive.current = true;
      startDrag(touch.clientX);
    };

    const onNativeTouchMove = (e: TouchEvent) => {
      if (!touchDragActive.current) return;
      const touch = e.touches[0];
      if (touch) {
        const deltaX = touch.clientX - dragStartX.current;
        if (Math.abs(deltaX) > 4 && e.cancelable) {
          try { e.preventDefault(); } catch { /* no-op */ }
        }
      }
      if (rafId.current !== null) cancelAnimationFrame(rafId.current);
      rafId.current = requestAnimationFrame(() => {
        const t = e.touches[0];
        if (!t) return;
        const deltaX = t.clientX - dragStartX.current;
        setTrackOffset(deltaX);
        rafId.current = null;
      });
    };

    const onNativeTouchEnd = (e: TouchEvent) => {
      if (!touchDragActive.current) return;
      touchDragActive.current = false;
      if (rafId.current !== null) {
        cancelAnimationFrame(rafId.current);
        rafId.current = null;
      }
      let finalX = dragStartX.current;
      const lastTouch = e.changedTouches[0];
      if (lastTouch) finalX = lastTouch.clientX;
      const deltaX = finalX - dragStartX.current;
      setDelta(dragStartTranslate.current + deltaX);
    };

    el.addEventListener('touchstart', onNativeTouchStart, { passive: true });
    el.addEventListener('touchmove', onNativeTouchMove, { passive: false });
    el.addEventListener('touchend', onNativeTouchEnd, { passive: true });
    el.addEventListener('touchcancel', onNativeTouchEnd, { passive: true });

    return () => {
      el.removeEventListener('touchstart', onNativeTouchStart);
      el.removeEventListener('touchmove', onNativeTouchMove);
      el.removeEventListener('touchend', onNativeTouchEnd);
      el.removeEventListener('touchcancel', onNativeTouchEnd);
    };
  }, [heroFilms.length, startDrag, setDelta]);

  const effectiveHeroIndex = heroFilms.length > 0
    ? ((heroIndex % heroFilms.length) + heroFilms.length) % heroFilms.length
    : 0;
  const featuredFilm = heroFilms[effectiveHeroIndex];

  return (
    <Box sx={{ bgcolor: 'background.default', minHeight: '100vh', pb: 8 }}>
      {error && (
        <Container maxWidth="xl" sx={{ pt: 3 }}>
          <Alert severity="error" action={<Button color="inherit" size="small" startIcon={<Refresh />} onClick={() => fetchHero()}>Thử lại</Button>}>
            {error}
          </Alert>
        </Container>
      )}

      {heroLoading && heroFilms.length === 0 ? (
        <Skeleton
          variant="rounded"
          sx={{ height: HERO_HEIGHT, maxHeight: HERO_MAX_HEIGHT, width: '100%', mb: 6, borderRadius: { xs: 0, md: 4 }, mt: { xs: 0, md: 2 } }}
        />
      ) : featuredFilm ? (
        <Box
          ref={containerRef}
          onMouseDown={heroFilms.length >= 2 ? handleMouseDown : undefined}
          sx={{
            height: HERO_HEIGHT,
            maxHeight: HERO_MAX_HEIGHT,
            width: '100%',
            position: 'relative',
            mb: 6,
            overflow: 'hidden',
            borderRadius: { xs: 0, md: 4 },
            mt: { xs: 0, md: 2 },
            cursor: heroFilms.length >= 2 ? (isDragging ? 'grabbing' : 'grab') : 'default',
            userSelect: isDragging ? 'none' : undefined,
            touchAction: heroFilms.length >= 2 ? 'pan-y' : undefined,
          }}
        >
          <Box
            onTransitionEnd={handleTransitionEnd}
            sx={{
              display: 'flex',
              width: '100%',
              height: '100%',
              transform: `translateX(${currentTranslate}px)`,
              transition: isDragging || isJumping
                ? 'none'
                : isTransitioning
                  ? `transform ${HERO_TRANSITION_MS}ms cubic-bezier(0.22, 0.61, 0.36, 1)`
                  : 'none',
              willChange: 'transform',
              WebkitUserDrag: 'none',
            }}
          >
            {slidesToRender.map((film, idx) => (
              <Box
                key={`${film.id}-${idx}`}
                sx={{
                  position: 'relative',
                  flex: '0 0 100%',
                  height: '100%',
                  overflow: 'hidden',
                  bgcolor: '#000',
                  pointerEvents: isDragging ? 'none' : 'auto',
                }}
              >
                {/* Poster gốc là dọc (2:3), banner này ngang nên vẫn phải crop - giống hệt cách
                    backdrop ở trang chi tiết phim xử lý (cover thường, không blur). Ảnh đầy đủ,
                    không mất nét được hiển thị lại riêng bằng poster nhỏ cạnh tiêu đề bên dưới. */}
                <Box
                  component="img"
                  src={film.thumbnailUrl}
                  alt=""
                  aria-hidden="true"
                  draggable={false}
                  sx={{
                    position: 'absolute',
                    inset: 0,
                    width: '100%',
                    height: '100%',
                    objectFit: 'cover',
                    pointerEvents: 'none',
                    userSelect: 'none',
                    WebkitUserDrag: 'none',
                  }}
                />
                <Box
                  sx={{
                    position: 'absolute',
                    top: 0,
                    left: 0,
                    right: 0,
                    bottom: 0,
                    background: `linear-gradient(to right, ${alpha(theme.palette.background.default, 0.9)} 0%, ${alpha(theme.palette.background.default, 0.4)} 50%, transparent 100%), linear-gradient(to top, ${theme.palette.background.default} 0%, transparent 30%)`,
                    display: 'flex',
                    alignItems: 'center',
                    gap: 4,
                    px: { xs: 4, md: 8 },
                  }}
                >
                  {/* Poster đầy đủ, sắc nét (không crop/blur) - để người dùng luôn thấy được ảnh gốc
                      dù nền phía sau bị cắt để lấp banner ngang. */}
                  <Box
                    component="img"
                    src={film.thumbnailUrl}
                    alt={film.title}
                    draggable={false}
                    sx={{
                      display: { xs: 'none', md: 'block' },
                      width: 180,
                      aspectRatio: '2 / 3',
                      objectFit: 'cover',
                      borderRadius: 3,
                      border: '2px solid rgba(255,255,255,0.15)',
                      boxShadow: '0 12px 32px rgba(0,0,0,0.5)',
                      flexShrink: 0,
                      pointerEvents: 'none',
                      userSelect: 'none',
                      WebkitUserDrag: 'none',
                    }}
                  />
                  <Box sx={{ minWidth: 0 }}>
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
                    {film.title}
                  </Typography>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 4 }}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                      <Star sx={{ color: '#FFD700' }} />
                      <Typography sx={{ color: 'white', fontWeight: 700 }}>{film.averageRating.toFixed(1)}</Typography>
                    </Box>
                    <Typography sx={{ color: 'rgba(255,255,255,0.7)' }}>
                      {film.episodeCount > 0 ? `${film.episodeCount} tập` : 'Phim lẻ'} •{' '}
                      {new Date(film.lastUpdate).getFullYear()}
                    </Typography>
                  </Box>
                  <Box sx={{ display: 'flex', gap: 2 }}>
                    <Button
                      variant="contained"
                      size="large"
                      startIcon={<PlayArrow />}
                      onClick={() => navigate(`/film/${film.id}`)}
                      sx={{ bgcolor: 'primary.main', px: 4, py: 1.5, fontSize: '1.1rem', borderRadius: 2 }}
                    >
                      {t('watchNow')}
                    </Button>
                  </Box>
                  </Box>
                </Box>
              </Box>
            ))}
          </Box>

          {heroFilms.length > 1 && (
            <Box sx={{ position: 'absolute', bottom: 24, left: { xs: 32, md: 64 }, display: 'flex', gap: 1 }}>
              {heroFilms.map((film, index) => (
                <IconButton
                  key={film.id}
                  size="small"
                  onClick={() => goToHeroIndex(index)}
                  sx={{ p: 0.5 }}
                  aria-label={film.title}
                >
                  <Box
                    sx={{
                      width: index === activeDotIndex ? 24 : 8,
                      height: 8,
                      borderRadius: 4,
                      bgcolor: index === activeDotIndex ? 'primary.main' : 'rgba(255,255,255,0.3)',
                      transition: 'all 0.3s ease',
                    }}
                  />
                </IconButton>
              ))}
            </Box>
          )}
        </Box>
      ) : (
        !heroLoading && (
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
        <FilmShowcaseSection titleKey="seriesFilms" category="SERIES" viewAllPath="/film/series" />
        <FilmShowcaseSection titleKey="standaloneFilms" category="STANDALONE" viewAllPath="/film/standalone" />
        <FilmShowcaseSection titleKey="animationFilms" category="ANIMATION" viewAllPath="/film/animation" />
      </Container>
    </Box>
  );
});

FilmHome.displayName = 'FilmHome';

export default FilmHome;
