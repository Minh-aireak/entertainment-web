import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Box,
  Container,
  Typography,
  Grid,
  Skeleton,
  TextField,
  InputAdornment,
  Paper,
  Chip,
  FormControl,
  Select,
  MenuItem,
  IconButton,
  Tooltip,
  Divider,
  CircularProgress,
  alpha,
  useTheme,
} from '@mui/material';
import type { Theme } from '@mui/material/styles';
import {
  Search,
  Movie,
  Close,
  LocalFireDepartment,
  Theaters,
  InfoOutlined,
  Check,
  RestartAlt,
  Category,
  Public,
  CalendarMonth,
  StarRounded,
  Tune,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { filmService } from '../../api/filmService';
import type {
  FilmSummaryResponse,
  Country,
  Genre,
} from '../../models';
import { COUNTRY_VALUES, GENRE_VALUES } from '../../constants/film';
import FilmCard from '../../components/Film/FilmCard';
import { usePersistedState } from '../../hooks/usePersistedState';
import { useInfiniteScroll } from '../../hooks/useInfiniteScroll';

const PAGE_SIZE = 16;

const DEBOUNCE_MS = 300;

const HOT_SEARCH_KEYS = ['ACTION', 'COMEDY', 'KOREA', 'USA', 'VIETNAM'] as const;

const MIN_RATING_OPTIONS = [0, 3, 4, 4.5, 4.8] as const;

interface FilmFilters {
  genre: Genre | 'ALL';
  country: Country | 'ALL';
  year: number | 'ALL';
  minRating: typeof MIN_RATING_OPTIONS[number];
}

const YEAR_OPTIONS = (() => {
  const current = new Date().getFullYear();
  const out: (number | 'ALL')[] = ['ALL'];
  for (let y = current; y >= current - 20; y -= 1) out.push(y);
  return out;
})();

const applyFilmFilters = (
  items: FilmSummaryResponse[],
  applied: FilmFilters,
): FilmSummaryResponse[] => {
  let out = items;
  const { country, genre, year, minRating } = applied;
  if (country !== 'ALL') out = out.filter((f) => f.country === country);
  if (genre !== 'ALL') out = out.filter((f) => f.genres?.includes(genre));
  if (year !== 'ALL') {
    out = out.filter((f) => f.lastUpdate && new Date(f.lastUpdate).getFullYear() === year);
  }
  if (minRating > 0) {
    out = out.filter((f) => (f.averageRating ?? 0) >= minRating);
  }
  return out;
};

const sectionLabelSx = {
  fontWeight: 700,
  color: 'text.secondary',
  textTransform: 'uppercase' as const,
  letterSpacing: '0.06em',
  fontSize: '0.72rem',
};

const filterChipSx = (selected: boolean, theme: Theme) => ({
  height: 38,
  px: 0.5,
  fontSize: '0.875rem',
  fontWeight: selected ? 700 : 500,
  borderRadius: 2.5,
  borderColor: selected ? 'primary.main' : alpha(theme.palette.text.primary, 0.14),
  bgcolor: selected ? undefined : alpha(theme.palette.text.primary, 0.03),
  transition: 'all 0.15s ease',
  '&:hover': {
    borderColor: 'primary.main',
    bgcolor: selected ? undefined : 'rgba(0,168,78,0.12)',
    transform: 'translateY(-1px)',
  },
});

const selectSx = (theme: Theme) => ({
  borderRadius: 2,
  bgcolor: alpha(theme.palette.text.primary, 0.03),
  '& .MuiOutlinedInput-notchedOutline': {
    borderColor: alpha(theme.palette.text.primary, 0.14),
  },
  '&:hover .MuiOutlinedInput-notchedOutline': {
    borderColor: 'primary.main',
  },
});

const FilmSearch: React.FC = React.memo(() => {
  const { t } = useTranslation();
  const theme = useTheme();
  const [query, setQuery] = usePersistedState('filmSearch:query', '');
  const [committedQuery, setCommittedQuery] = usePersistedState('filmSearch:committedQuery', '');
  const [items, setItems] = usePersistedState<FilmSummaryResponse[]>('filmSearch:items', []);
  const [nextPage, setNextPage] = usePersistedState('filmSearch:nextPage', 1);
  const [hasMore, setHasMore] = usePersistedState('filmSearch:hasMore', true);
  const [searchRaw, setSearchRaw] = usePersistedState<FilmSummaryResponse[]>('filmSearch:searchRaw', []);
  const [visibleCount, setVisibleCount] = usePersistedState('filmSearch:visibleCount', PAGE_SIZE);
  const [loading, setLoading] = useState(items.length === 0);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [focused, setFocused] = useState(false);
  const [filters, setFilters] = usePersistedState<FilmFilters>('filmSearch:filters', {
    genre: 'ALL',
    country: 'ALL',
    year: 'ALL',
    minRating: 0,
  });
  const skipInitialFetchRef = useRef(items.length > 0);

  const fetchInitial = useCallback(
    async (search: string, applied: FilmFilters) => {
      setLoading(true);
      setError(null);
      try {
        const normalizedQuery = search.trim();
        if (normalizedQuery) {
          const raw = await filmService.searchFilms(normalizedQuery);
          const all = raw.result ?? [];
          const filtered = applyFilmFilters(all, applied);
          setSearchRaw(all);
          setItems(filtered.slice(0, PAGE_SIZE));
          setVisibleCount(PAGE_SIZE);
          setHasMore(filtered.length > PAGE_SIZE);
        } else {
          const pageRes = await filmService.getPageFilms(1, PAGE_SIZE);
          const data = pageRes.result.data ?? [];
          setSearchRaw([]);
          setItems(applyFilmFilters(data, applied));
          setNextPage(2);
          setHasMore(1 < (pageRes.result.totalPages ?? 1));
        }
      } catch (err) {
        console.error('Failed to search/browse films:', err);
        setError(t('filmLoadFailed'));
      } finally {
        setLoading(false);
      }
    },
    [setItems, setSearchRaw, setNextPage, setHasMore, setVisibleCount, t],
  );

  useEffect(() => {
    if (skipInitialFetchRef.current) {
      skipInitialFetchRef.current = false;
      return;
    }
    fetchInitial(committedQuery, filters);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [committedQuery, filters]);

  const loadMore = useCallback(async () => {
    if (loading || loadingMore || !hasMore) return;
    setLoadingMore(true);
    try {
      const normalizedQuery = committedQuery.trim();
      if (normalizedQuery) {
        const filtered = applyFilmFilters(searchRaw, filters);
        const newVisible = visibleCount + PAGE_SIZE;
        setItems(filtered.slice(0, newVisible));
        setVisibleCount(newVisible);
        setHasMore(filtered.length > newVisible);
      } else {
        const pageRes = await filmService.getPageFilms(nextPage, PAGE_SIZE);
        const data = pageRes.result.data ?? [];
        setItems((prev) => [...prev, ...applyFilmFilters(data, filters)]);
        setHasMore(nextPage < (pageRes.result.totalPages ?? 1));
        setNextPage(nextPage + 1);
      }
    } catch (err) {
      console.error('Failed to load more films:', err);
    } finally {
      setLoadingMore(false);
    }
  }, [
    loading,
    loadingMore,
    hasMore,
    committedQuery,
    searchRaw,
    filters,
    visibleCount,
    nextPage,
    setItems,
    setHasMore,
    setVisibleCount,
    setNextPage,
  ]);

  const sentinelRef = useInfiniteScroll({
    hasMore,
    loading: loading || loadingMore,
    onLoadMore: loadMore,
  });

  const skipInitialDebounceRef = useRef(true);
  useEffect(() => {
    if (skipInitialDebounceRef.current) {
      skipInitialDebounceRef.current = false;
      return;
    }
    const handler = window.setTimeout(() => {
      setCommittedQuery(query);
    }, DEBOUNCE_MS);
    return () => window.clearTimeout(handler);
  }, [query, setCommittedQuery]);

  const handleHotSearch = useCallback((key: string) => {
    if (GENRE_VALUES.includes(key as Genre)) {
      setFilters((f) => ({ ...f, genre: key as Genre }));
    } else if (COUNTRY_VALUES.includes(key as Country)) {
      setFilters((f) => ({ ...f, country: key as Country }));
    }
    setQuery('');
  }, [setFilters, setQuery]);

  const hotSearchChips = useMemo(() => {
    return HOT_SEARCH_KEYS.map((key) => {
      const isGenre = GENRE_VALUES.includes(key as Genre);
      const isCountry = COUNTRY_VALUES.includes(key as Country);
      const label = isGenre
        ? t(`genre.${key as Genre}`)
        : isCountry
        ? t(`country.${key as Country}`)
        : key;
      return (
        <Chip
          key={key}
          label={label}
          onClick={() => handleHotSearch(key)}
          size="medium"
          sx={{
            fontSize: '0.85rem',
            px: 0.5,
            py: 0.3,
            bgcolor: alpha(theme.palette.text.primary, 0.04),
            borderColor: alpha(theme.palette.text.primary, 0.12),
            color: 'text.primary',
            '&:hover': {
              bgcolor: 'primary.main',
              color: '#fff',
              borderColor: 'primary.main',
            },
          }}
          variant="outlined"
        />
      );
    });
  }, [handleHotSearch, t, theme]);

  const handleClearQuery = useCallback(() => {
    setQuery('');
    setCommittedQuery('');
  }, [setQuery, setCommittedQuery]);

  const resetFilters = useCallback(() => {
    setFilters({
      genre: 'ALL',
      country: 'ALL',
      year: 'ALL',
      minRating: 0,
    });
  }, [setFilters]);

  const hasActiveFilters =
    filters.genre !== 'ALL' ||
    filters.country !== 'ALL' ||
    filters.year !== 'ALL' ||
    filters.minRating > 0;

  return (
    <Box sx={{ minHeight: '100vh', pb: 8 }}>
      <Box
        sx={{
          position: 'relative',
          overflow: 'hidden',
          pt: { xs: 4, md: 6 },
          pb: { xs: 4, md: 6 },
          mb: 6,
          background: `linear-gradient(135deg, ${alpha(theme.palette.primary.main, 0.22)} 0%, ${theme.palette.background.default} 45%, ${theme.palette.background.paper} 100%)`,
          borderBottom: '1px solid',
          borderBottomColor: alpha(theme.palette.text.primary, 0.06),
        }}
      >
        <Box
          sx={{
            position: 'absolute',
            inset: 0,
            opacity: 0.15,
            backgroundImage:
              'radial-gradient(circle at 80% 20%, rgba(0,168,78,0.5) 0%, transparent 45%), radial-gradient(circle at 10% 80%, rgba(120,60,255,0.35) 0%, transparent 45%)',
            pointerEvents: 'none',
          }}
        />
        <Container maxWidth="xl" sx={{ position: 'relative' }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2 }}>
            <Theaters color="primary" sx={{ fontSize: 36 }} />
            <Typography variant="h3" sx={{ fontWeight: 900, letterSpacing: '-0.03em' }}>
              {t('searchPageTitle')}
            </Typography>
          </Box>
          <Typography variant="body1" color="text.secondary" sx={{ mb: 4 }}>
            {t('searchPageSubtitle')}
          </Typography>

          <Box
            sx={{
              maxWidth: 760,
              position: 'relative',
            }}
          >
            <TextField
              fullWidth
              placeholder={t('searchPlaceholder')}
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              onFocus={() => setFocused(true)}
              onBlur={() => setFocused(false)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  setCommittedQuery(query);
                }
              }}
              sx={{
                '& .MuiOutlinedInput-root': {
                  borderRadius: 3,
                  bgcolor: 'background.paper',
                  py: 0.5,
                  fontSize: '1.05rem',
                  transition: 'box-shadow 0.2s ease, transform 0.2s ease',
                  boxShadow: focused
                    ? '0 0 0 4px rgba(0,168,78,0.18), 0 16px 40px rgba(0,0,0,0.45)'
                    : '0 10px 30px rgba(0,0,0,0.3)',
                  border: focused ? '1px solid primary.main' : '1px solid transparent',
                },
                '& .MuiOutlinedInput-notchedOutline': {
                  border: 'none',
                },
              }}
              slotProps={{
                input: {
                  startAdornment: (
                    <InputAdornment position="start">
                      <Search sx={{ color: focused ? 'primary.main' : 'text.secondary', fontSize: 24 }} />
                    </InputAdornment>
                  ),
                  endAdornment: query ? (
                    <InputAdornment position="end">
                      <Tooltip title={t('delete')}>
                        <IconButton onClick={handleClearQuery} size="small" edge="end">
                          <Close fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    </InputAdornment>
                  ) : null,
                },
              }}
            />
          </Box>

          <Box sx={{ mt: 3, display: 'flex', alignItems: 'center', gap: 1.5, flexWrap: 'wrap' }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, color: 'text.secondary' }}>
              <LocalFireDepartment sx={{ fontSize: 18, color: 'error.main' }} />
              <Typography variant="body2" sx={{ fontWeight: 700 }}>
                {t('hotSearches')}
              </Typography>
            </Box>
            {hotSearchChips}
          </Box>
        </Container>
      </Box>

      <Container maxWidth="xl">
        <Paper
          sx={{
            mb: 4,
            p: { xs: 2.5, md: 3.5 },
            borderRadius: 4,
            bgcolor: alpha(theme.palette.text.primary, 0.03),
            border: '1px solid',
            borderColor: alpha(theme.palette.text.primary, 0.06),
          }}
        >
          <Box
            sx={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              mb: 3,
              flexWrap: 'wrap',
              gap: 2,
            }}
          >
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.25 }}>
              <Box
                sx={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  width: 36,
                  height: 36,
                  borderRadius: 2,
                  bgcolor: alpha('#00A84E', 0.15),
                }}
              >
                <Tune sx={{ fontSize: 20, color: 'primary.main' }} />
              </Box>
              <Typography variant="subtitle1" sx={{ fontWeight: 800, fontSize: '1.05rem' }}>
                {t('searchFilters')}
              </Typography>
            </Box>
            {hasActiveFilters && (
              <Chip
                icon={<RestartAlt sx={{ fontSize: '16px !important' }} />}
                label={t('resetFilters')}
                onClick={resetFilters}
                size="medium"
                sx={{
                  fontWeight: 700,
                  bgcolor: alpha('#ef5350', 0.12),
                  color: '#ef5350',
                  border: '1px solid rgba(239,83,80,0.3)',
                  '&:hover': { bgcolor: alpha('#ef5350', 0.2) },
                }}
              />
            )}
          </Box>

          {hasActiveFilters && (
            <Box
              sx={{
                display: 'flex',
                flexWrap: 'wrap',
                gap: 1,
                mb: 3,
                pb: 3,
                borderBottom: '1px solid',
                borderBottomColor: alpha(theme.palette.text.primary, 0.06),
              }}
            >
              {filters.genre !== 'ALL' && (
                <Chip
                  label={t(`genre.${filters.genre}`)}
                  color="primary"
                  size="small"
                  onDelete={() => setFilters((f) => ({ ...f, genre: 'ALL' }))}
                  sx={{ fontWeight: 600 }}
                />
              )}
              {filters.country !== 'ALL' && (
                <Chip
                  label={t(`country.${filters.country}`)}
                  color="primary"
                  size="small"
                  onDelete={() => setFilters((f) => ({ ...f, country: 'ALL' }))}
                  sx={{ fontWeight: 600 }}
                />
              )}
              {filters.year !== 'ALL' && (
                <Chip
                  label={filters.year}
                  color="primary"
                  size="small"
                  onDelete={() => setFilters((f) => ({ ...f, year: 'ALL' }))}
                  sx={{ fontWeight: 600 }}
                />
              )}
              {filters.minRating > 0 && (
                <Chip
                  label={`≥ ${filters.minRating.toFixed(1)} ★`}
                  color="primary"
                  size="small"
                  onDelete={() => setFilters((f) => ({ ...f, minRating: 0 }))}
                  sx={{ fontWeight: 600 }}
                />
              )}
            </Box>
          )}

          <Box sx={{ mb: 3 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75, mb: 1.5 }}>
              <Category sx={{ fontSize: 16, color: 'text.secondary' }} />
              <Typography variant="caption" sx={sectionLabelSx}>
                {t('filterByGenre')}
              </Typography>
            </Box>
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
              <Chip
                label={t('allGenres')}
                onClick={() => setFilters((f) => ({ ...f, genre: 'ALL' }))}
                color={filters.genre === 'ALL' ? 'primary' : 'default'}
                variant={filters.genre === 'ALL' ? 'filled' : 'outlined'}
                size="medium"
                icon={filters.genre === 'ALL' ? <Check sx={{ fontSize: '16px !important' }} /> : undefined}
                sx={filterChipSx(filters.genre === 'ALL', theme)}
              />
              {GENRE_VALUES.map((g) => (
                <Chip
                  key={g}
                  label={t(`genre.${g}`)}
                  onClick={() => setFilters((f) => ({ ...f, genre: f.genre === g ? 'ALL' : g }))}
                  color={filters.genre === g ? 'primary' : 'default'}
                  variant={filters.genre === g ? 'filled' : 'outlined'}
                  size="medium"
                  icon={filters.genre === g ? <Check sx={{ fontSize: '16px !important' }} /> : undefined}
                  sx={filterChipSx(filters.genre === g, theme)}
                />
              ))}
            </Box>
          </Box>

          <Box sx={{ mb: 3 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75, mb: 1.5 }}>
              <Public sx={{ fontSize: 16, color: 'text.secondary' }} />
              <Typography variant="caption" sx={sectionLabelSx}>
                {t('filterByCountry')}
              </Typography>
            </Box>
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
              <Chip
                label={t('allCountries')}
                onClick={() => setFilters((f) => ({ ...f, country: 'ALL' }))}
                color={filters.country === 'ALL' ? 'primary' : 'default'}
                variant={filters.country === 'ALL' ? 'filled' : 'outlined'}
                size="medium"
                icon={filters.country === 'ALL' ? <Check sx={{ fontSize: '16px !important' }} /> : undefined}
                sx={filterChipSx(filters.country === 'ALL', theme)}
              />
              {COUNTRY_VALUES.map((c) => (
                <Chip
                  key={c}
                  label={t(`country.${c}`)}
                  onClick={() => setFilters((f) => ({ ...f, country: f.country === c ? 'ALL' : c }))}
                  color={filters.country === c ? 'primary' : 'default'}
                  variant={filters.country === c ? 'filled' : 'outlined'}
                  size="medium"
                  icon={filters.country === c ? <Check sx={{ fontSize: '16px !important' }} /> : undefined}
                  sx={filterChipSx(filters.country === c, theme)}
                />
              ))}
            </Box>
          </Box>

          <Divider sx={{ my: 3, borderColor: 'divider' }} />

          <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 3 }}>
            <Box sx={{ flex: '1 1 200px', minWidth: 200 }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75, mb: 1 }}>
                <CalendarMonth sx={{ fontSize: 16, color: 'text.secondary' }} />
                <Typography variant="caption" sx={sectionLabelSx}>
                  {t('filterYear')}
                </Typography>
              </Box>
              <FormControl fullWidth size="medium">
                <Select
                  value={filters.year}
                  displayEmpty
                  inputProps={{ 'aria-label': t('filterYear') }}
                  onChange={(e) =>
                    setFilters((f) => ({ ...f, year: e.target.value as number | 'ALL' }))
                  }
                  sx={selectSx(theme)}
                >
                  {YEAR_OPTIONS.map((y) => (
                    <MenuItem key={y} value={y}>
                      {y === 'ALL' ? t('allYears') : y}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
            </Box>

            <Box sx={{ flex: '1 1 200px', minWidth: 200 }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75, mb: 1 }}>
                <StarRounded sx={{ fontSize: 16, color: 'text.secondary' }} />
                <Typography variant="caption" sx={sectionLabelSx}>
                  {t('filterMinRating')}
                </Typography>
              </Box>
              <FormControl fullWidth size="medium">
                <Select
                  value={filters.minRating}
                  displayEmpty
                  inputProps={{ 'aria-label': t('filterMinRating') }}
                  onChange={(e) =>
                    setFilters((f) => ({
                      ...f,
                      minRating: e.target.value as (typeof MIN_RATING_OPTIONS)[number],
                    }))
                  }
                  sx={selectSx(theme)}
                >
                  {MIN_RATING_OPTIONS.map((r) => (
                    <MenuItem key={r} value={r}>
                      {r === 0 ? t('anyRating') : `≥ ${r.toFixed(1)} ★`}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
            </Box>

            <Box
              sx={{
                display: 'flex',
                alignItems: 'center',
                gap: 0.75,
                flex: '1 1 240px',
                color: 'text.secondary',
                pt: { xs: 0, md: 3.75 },
              }}
            >
              <InfoOutlined fontSize="small" sx={{ color: 'text.secondary', fontSize: 16 }} />
              <Typography variant="caption" color="text.secondary">
                {t('searchHint')}
              </Typography>
            </Box>
          </Box>
        </Paper>

        {error && (
          <Box sx={{ mb: 3 }}>
            <Paper
              sx={{
                p: 3,
                borderRadius: 3,
                border: '1px solid rgba(239,83,80,0.35)',
                bgcolor: alpha('#ef5350', 0.08),
                color: 'error.main',
                fontWeight: 600,
              }}
            >
              {error}
            </Paper>
          </Box>
        )}

        {loading ? (
          <Grid container spacing={3}>
            {Array.from(new Array(PAGE_SIZE)).map((_, index) => (
              <Grid size={{ xs: 12, sm: 6, md: 4, lg: 3 }} key={index}>
                <Skeleton variant="rectangular" sx={{ aspectRatio: '2 / 3', borderRadius: 2 }} />
                <Skeleton variant="text" sx={{ mt: 2 }} />
                <Skeleton variant="text" width="60%" />
              </Grid>
            ))}
          </Grid>
        ) : items.length ? (
          <>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
              {t('showingFilms', { count: items.length })}
              {committedQuery.trim() ? t('searchForQuery', { query: committedQuery.trim() }) : ''}
            </Typography>

            <Grid container spacing={3}>
              {items.map((film) => (
                <Grid size={{ xs: 12, sm: 6, md: 4, lg: 3 }} key={film.id}>
                  <FilmCard film={film} size="lg" />
                </Grid>
              ))}
            </Grid>

            <Box
              ref={sentinelRef}
              sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', py: 5, minHeight: 60 }}
            >
              {loadingMore && (
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, color: 'text.secondary' }}>
                  <CircularProgress size={22} thickness={4} />
                  <Typography variant="body2">{t('loadingMoreFilms')}</Typography>
                </Box>
              )}
              {!hasMore && !loadingMore && (
                <Typography variant="body2" color="text.disabled">
                  {t('allFilmsShown')}
                </Typography>
              )}
            </Box>
          </>
        ) : (
          <Paper
            sx={{
              py: 12,
              px: 3,
              textAlign: 'center',
              borderRadius: 4,
              bgcolor: alpha(theme.palette.text.primary, 0.03),
              border: '1px solid',
              borderColor: alpha(theme.palette.text.primary, 0.06),
            }}
          >
            <Movie sx={{ fontSize: 72, color: 'text.disabled', mb: 2 }} />
            <Typography variant="h5" sx={{ fontWeight: 800, mb: 1 }}>
              {t('noResults')}
            </Typography>
            <Typography color="text.secondary">
              {t('noResultsHint')}
            </Typography>
            {hasActiveFilters && (
              <Box sx={{ mt: 4 }}>
                <Chip
                  label={t('clearFilters')}
                  color="primary"
                  variant="filled"
                  onClick={resetFilters}
                  size="medium"
                  sx={{ px: 1, py: 0.5, fontSize: '0.95rem', fontWeight: 600 }}
                />
              </Box>
            )}
          </Paper>
        )}
      </Container>
    </Box>
  );
});

FilmSearch.displayName = 'FilmSearch';

export default FilmSearch;
