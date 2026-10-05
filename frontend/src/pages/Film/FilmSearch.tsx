import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Box,
  ButtonBase,
  Container,
  Typography,
  Grid,
  Skeleton,
  InputBase,
  Paper,
  Chip,
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
  RestartAlt,
  Category,
  Public,
  CalendarMonth,
  StarRounded,
  Tune,
  ArrowForwardRounded,
  KeyboardArrowDownRounded,
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
import { SectionHeader } from '../../components/common/VibeUi';
import { usePersistedState } from '../../hooks/usePersistedState';
import { useInfiniteScroll } from '../../hooks/useInfiniteScroll';
import {
  VIBE_GLOW,
  VIBE_GRADIENT,
  VIBE_GRADIENT_HOVER,
  VIBE_LOVE_GRADIENT,
  VIBE_TEAL,
  eyebrowSx,
  glassCardSx,
  pillSx,
  softIconButtonSx,
  vibeAccentText,
  vibeBorder,
  vibeSurface,
  vibeSurfaceHover,
} from '../../styles/vibe';

const PAGE_SIZE = 16;

const DEBOUNCE_MS = 300;

const HOT_SEARCH_KEYS = ['ACTION', 'COMEDY', 'KOREA', 'USA', 'VIETNAM'] as const;

const MIN_RATING_OPTIONS = [0, 3, 4, 4.5, 4.8] as const;

type MinRating = (typeof MIN_RATING_OPTIONS)[number];

interface FilmFilters {
  genre: Genre | 'ALL';
  country: Country | 'ALL';
  year: number | 'ALL';
  minRating: MinRating;
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

const focusRingSx = { outline: `2px solid ${VIBE_TEAL}`, outlineOffset: 2 };

/** The vibe colours plus violet, ending where they start so the moving ring loops without a seam. */
const SEARCH_RING_FLOW = 'linear-gradient(90deg, #00A84E, #00C9A7, #22C7F0, #7850FF, #00A84E)';

/*
 * Pill search bar. Focusing it swaps the hairline border for a gradient ring that keeps flowing:
 * the paper fill is clipped to the padding box and the gradient is painted in the transparent
 * border, then slid along by exactly one tile per loop.
 */
const searchBarSx = (theme: Theme) => {
  const dark = theme.palette.mode === 'dark';
  const paper = theme.palette.background.paper;
  return {
    display: 'flex',
    alignItems: 'center',
    gap: { xs: 0.75, sm: 1.25 },
    height: { xs: 58, md: 66 },
    px: { xs: 0.75, md: 1 },
    borderRadius: '999px',
    border: '2px solid',
    borderColor: vibeBorder(theme),
    bgcolor: paper,
    boxShadow: dark ? '0 14px 36px rgba(0, 0, 0, 0.45)' : '0 14px 36px rgba(27, 37, 89, 0.1)',
    transition: 'box-shadow 0.25s ease, border-color 0.25s ease, transform 0.25s cubic-bezier(.2,.8,.2,1)',
    '@keyframes filmSearchRingFlow': {
      from: { backgroundPosition: '0 0, 0% 50%' },
      to: { backgroundPosition: '0 0, 200% 50%' },
    },
    '&:hover': { borderColor: alpha(VIBE_TEAL, 0.45) },
    '&:focus-within': {
      borderColor: 'transparent',
      background: `linear-gradient(${paper}, ${paper}) padding-box, ${SEARCH_RING_FLOW} border-box`,
      backgroundSize: '100% 100%, 200% 100%',
      animation: 'filmSearchRingFlow 3s linear infinite',
      boxShadow: `0 0 0 6px ${alpha(VIBE_TEAL, 0.14)}, 0 18px 44px ${alpha(VIBE_TEAL, dark ? 0.22 : 0.18)}`,
      transform: 'translateY(-2px)',
    },
    // The search orb is the first child: it tilts while the bar has focus.
    '&:focus-within > :first-of-type': { transform: 'rotate(-12deg) scale(1.06)' },
    '@media (prefers-reduced-motion: reduce)': { '&:focus-within': { animation: 'none' } },
  };
};

const searchOrbSx = {
  width: { xs: 40, md: 48 },
  height: { xs: 40, md: 48 },
  borderRadius: '50%',
  display: 'grid',
  placeItems: 'center',
  flexShrink: 0,
  color: '#fff',
  background: VIBE_GRADIENT,
  boxShadow: VIBE_GLOW,
  transition: 'transform 0.35s cubic-bezier(.2,.8,.2,1)',
  '& .MuiSvgIcon-root': { fontSize: { xs: 22, md: 25 } },
};

const searchInputSx = {
  flex: 1,
  minWidth: 0,
  fontSize: { xs: '1rem', md: '1.08rem' },
  fontWeight: 600,
  '& input': { textOverflow: 'ellipsis' },
  '& input::placeholder': { color: 'text.secondary', opacity: 0.85, fontWeight: 500 },
};

const clearButtonSx = (theme: Theme) => ({
  ...softIconButtonSx(theme),
  width: 36,
  height: 36,
  '@keyframes filmSearchPop': {
    from: { opacity: 0, transform: 'scale(0.6)' },
    to: { opacity: 1, transform: 'scale(1)' },
  },
  animation: 'filmSearchPop 0.22s cubic-bezier(.2,.8,.2,1)',
  '@media (prefers-reduced-motion: reduce)': { animation: 'none' },
});

const goButtonSx = {
  width: { xs: 44, md: 50 },
  height: { xs: 44, md: 50 },
  flexShrink: 0,
  color: '#fff',
  background: VIBE_GRADIENT,
  boxShadow: VIBE_GLOW,
  transition: 'transform 0.18s cubic-bezier(.2,.8,.2,1), box-shadow 0.2s ease',
  '& .MuiSvgIcon-root': { transition: 'transform 0.2s ease' },
  '&:hover': { background: VIBE_GRADIENT_HOVER, transform: 'scale(1.06)' },
  '&:hover .MuiSvgIcon-root': { transform: 'translateX(3px)' },
  '&:active': { transform: 'scale(0.92)' },
};

const hotBadgeSx = {
  display: 'inline-flex',
  alignItems: 'center',
  gap: 0.5,
  px: 1.25,
  py: 0.5,
  borderRadius: '999px',
  background: VIBE_LOVE_GRADIENT,
  boxShadow: '0 6px 16px rgba(255, 77, 141, 0.35)',
  color: '#fff',
  fontSize: '0.7rem',
  fontWeight: 900,
  letterSpacing: '0.12em',
  textTransform: 'uppercase' as const,
  '& .MuiSvgIcon-root': { fontSize: 16 },
};

/** Hot keyword as a #hashtag sticker: tilts on hover, lights up while its filter is applied. */
const hotTagSx = (theme: Theme, active: boolean) => ({
  ...pillSx(theme, active),
  py: 0.8,
  fontSize: '0.85rem',
  '&:hover': {
    transform: 'translateY(-2px) rotate(-2deg)',
    ...(active ? {} : { bgcolor: vibeSurfaceHover(theme), borderColor: alpha(VIBE_TEAL, 0.45) }),
  },
  '&:active': { transform: 'scale(0.95)' },
  '&.Mui-focusVisible': focusRingSx,
});

/** Genre / country option: glass pill when idle, gradient with a glow when picked. */
const filterPillSx = (theme: Theme, selected: boolean) => ({
  ...pillSx(theme, selected),
  px: 2,
  py: 1,
  fontSize: '0.875rem',
  '&:hover': {
    transform: 'translateY(-2px)',
    ...(selected
      ? { boxShadow: `0 10px 26px ${alpha(VIBE_TEAL, 0.45)}` }
      : { bgcolor: vibeSurfaceHover(theme), borderColor: alpha(VIBE_TEAL, 0.45) }),
  },
  '&:active': { transform: 'scale(0.95)' },
  '&.Mui-focusVisible': focusRingSx,
});

const resetPillSx = (theme: Theme) => {
  const red = theme.palette.error.main;
  return {
    display: 'inline-flex',
    alignItems: 'center',
    gap: 0.75,
    px: 1.5,
    py: 0.75,
    borderRadius: '999px',
    fontSize: '0.8rem',
    fontWeight: 800,
    color: red,
    bgcolor: alpha(red, 0.1),
    border: '1px solid',
    borderColor: alpha(red, 0.3),
    transition: 'background-color 0.2s ease, transform 0.15s ease',
    '& .MuiSvgIcon-root': { fontSize: 18, transition: 'transform 0.45s cubic-bezier(.2,.8,.2,1)' },
    '&:hover': { bgcolor: alpha(red, 0.18) },
    '&:hover .MuiSvgIcon-root': { transform: 'rotate(-360deg)' },
    '&:active': { transform: 'scale(0.95)' },
    '&.Mui-focusVisible': focusRingSx,
  };
};

/*
 * Pill dropdown built on a plain InputBase, so there is no notched outline to fight: glass when
 * idle, a gradient ring (same padding-box / border-box trick as the search bar, with a teal tint
 * over the paper) once it holds a real filter. The chevron sits in its own little bubble.
 */
const pillSelectSx = (theme: Theme, active: boolean) => {
  const paper = theme.palette.background.paper;
  const tint = alpha(VIBE_TEAL, theme.palette.mode === 'dark' ? 0.1 : 0.07);
  return {
    width: '100%',
    minHeight: 60,
    pl: 1,
    pr: 1.5,
    borderRadius: '999px',
    border: '1.5px solid',
    borderColor: vibeBorder(theme),
    bgcolor: vibeSurface(theme),
    cursor: 'pointer',
    transition: 'transform 0.2s cubic-bezier(.2,.8,.2,1), box-shadow 0.2s ease, border-color 0.2s ease, background-color 0.2s ease',
    '&:hover': { transform: 'translateY(-2px)', borderColor: alpha(VIBE_TEAL, 0.5), bgcolor: vibeSurfaceHover(theme) },
    ...(active
      ? {
          borderColor: 'transparent',
          background: `linear-gradient(${tint}, ${tint}) padding-box, linear-gradient(${paper}, ${paper}) padding-box, ${VIBE_GRADIENT} border-box`,
          boxShadow: `0 10px 26px ${alpha(VIBE_TEAL, 0.2)}`,
          '&:hover': { transform: 'translateY(-2px)' },
        }
      : {}),
    '&.Mui-focused': { boxShadow: `0 0 0 4px ${alpha(VIBE_TEAL, 0.16)}` },
    '& .MuiSelect-select': { py: 0.75 },
    '& .MuiSelect-icon': {
      right: 12,
      top: 'calc(50% - 15px)',
      width: 30,
      height: 30,
      p: '4px',
      boxSizing: 'border-box' as const,
      borderRadius: '50%',
      color: active ? '#fff' : 'text.secondary',
      background: active ? VIBE_GRADIENT : vibeSurfaceHover(theme),
      transition: 'transform 0.25s cubic-bezier(.2,.8,.2,1), color 0.2s ease',
    },
  };
};

const pillSelectOrbSx = (theme: Theme, active: boolean) => ({
  width: 38,
  height: 38,
  borderRadius: '50%',
  display: 'grid',
  placeItems: 'center',
  flexShrink: 0,
  color: active ? '#fff' : vibeAccentText(theme),
  background: active ? VIBE_GRADIENT : alpha(VIBE_TEAL, 0.12),
  '& .MuiSvgIcon-root': { fontSize: 20 },
});

const pillSelectMenuProps = (columns?: number) => ({
  anchorOrigin: { vertical: 'bottom', horizontal: 'left' } as const,
  transformOrigin: { vertical: 'top', horizontal: 'left' } as const,
  slotProps: {
    paper: { sx: { mt: 1, borderRadius: '22px', p: 0.75, maxHeight: 440 } },
    list: {
      sx: columns
        ? { display: 'grid', gridTemplateColumns: `repeat(${columns}, 1fr)`, gap: 0.75, p: 0.5 }
        : { display: 'flex', flexDirection: 'column' as const, gap: 0.5, p: 0.5 },
    },
  },
});

const pillSelectItemSx = (grid: boolean, fullRow: boolean) => ({
  m: 0,
  minHeight: 0,
  py: 1,
  px: 2,
  gap: 1,
  borderRadius: '999px',
  justifyContent: grid ? 'center' : 'flex-start',
  fontWeight: 700,
  ...(grid && fullRow ? { gridColumn: '1 / -1' } : {}),
  '&.Mui-selected, &.Mui-selected:hover, &.Mui-selected.Mui-focusVisible': {
    background: VIBE_GRADIENT,
    color: '#fff',
    boxShadow: VIBE_GLOW,
  },
});

const FilterLabel: React.FC<{ icon: React.ReactNode; children: React.ReactNode }> = ({ icon, children }) => {
  const theme = useTheme();
  return (
    <Box
      sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 0.75,
        mb: 1.5,
        color: vibeAccentText(theme),
        '& .MuiSvgIcon-root': { fontSize: 16 },
      }}
    >
      {icon}
      <Typography component="span" sx={eyebrowSx(theme)}>
        {children}
      </Typography>
    </Box>
  );
};

interface PillSelectProps<T extends string | number> {
  value: T;
  options: readonly T[];
  /** The "no filter" option: the pill stays glass while this is picked. */
  emptyValue: T;
  label: string;
  icon: React.ReactNode;
  renderOption: (option: T) => React.ReactNode;
  onChange: (value: T) => void;
  /** Lay the menu out as a grid of bubbles instead of a list. */
  columns?: number;
}

function PillSelect<T extends string | number>({
  value,
  options,
  emptyValue,
  label,
  icon,
  renderOption,
  onChange,
  columns,
}: PillSelectProps<T>) {
  const theme = useTheme();
  const active = value !== emptyValue;
  return (
    <Select
      value={value}
      onChange={(e) => onChange(e.target.value as T)}
      input={<InputBase />}
      inputProps={{ 'aria-label': label }}
      IconComponent={KeyboardArrowDownRounded}
      renderValue={(selected) => (
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.25, minWidth: 0 }}>
          <Box sx={pillSelectOrbSx(theme, active)}>{icon}</Box>
          <Box sx={{ minWidth: 0 }}>
            <Typography
              component="span"
              sx={{
                display: 'block',
                fontSize: '0.66rem',
                fontWeight: 900,
                letterSpacing: '0.12em',
                lineHeight: 1.4,
                textTransform: 'uppercase',
                color: active ? vibeAccentText(theme) : 'text.secondary',
              }}
            >
              {label}
            </Typography>
            <Typography component="span" noWrap sx={{ display: 'block', fontWeight: 800, fontSize: '0.98rem', lineHeight: 1.3 }}>
              {renderOption(selected)}
            </Typography>
          </Box>
        </Box>
      )}
      MenuProps={pillSelectMenuProps(columns)}
      sx={pillSelectSx(theme, active)}
    >
      {options.map((option) => (
        <MenuItem key={String(option)} value={option} sx={pillSelectItemSx(!!columns, option === emptyValue)}>
          {renderOption(option)}
        </MenuItem>
      ))}
    </Select>
  );
}

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

  const hotSearchTags = useMemo(() => {
    return HOT_SEARCH_KEYS.map((key) => {
      const isGenre = GENRE_VALUES.includes(key as Genre);
      const isCountry = COUNTRY_VALUES.includes(key as Country);
      const label = isGenre
        ? t(`genre.${key as Genre}`)
        : isCountry
        ? t(`country.${key as Country}`)
        : key;
      const active = filters.genre === key || filters.country === key;
      return (
        <ButtonBase key={key} onClick={() => handleHotSearch(key)} sx={hotTagSx(theme, active)}>
          <Box
            component="span"
            aria-hidden
            sx={{ mr: '2px', fontWeight: 900, color: active ? 'rgba(255,255,255,0.75)' : vibeAccentText(theme) }}
          >
            #
          </Box>
          {label}
        </ButtonBase>
      );
    });
  }, [filters.genre, filters.country, handleHotSearch, t, theme]);

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

  const activeFilterCount = [
    filters.genre !== 'ALL',
    filters.country !== 'ALL',
    filters.year !== 'ALL',
    filters.minRating > 0,
  ].filter(Boolean).length;
  const hasActiveFilters = activeFilterCount > 0;

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

          <Box sx={{ maxWidth: 760 }}>
            <Box sx={searchBarSx(theme)}>
              <Box sx={searchOrbSx} aria-hidden>
                <Search />
              </Box>
              <InputBase
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    setCommittedQuery(query);
                  }
                }}
                placeholder={t('searchPlaceholder')}
                inputProps={{ 'aria-label': t('searchPageTitle'), enterKeyHint: 'search' }}
                sx={searchInputSx}
              />
              {query && (
                <Tooltip title={t('clearSearch')}>
                  <IconButton onClick={handleClearQuery} aria-label={t('clearSearch')} sx={clearButtonSx(theme)}>
                    <Close fontSize="small" />
                  </IconButton>
                </Tooltip>
              )}
              <Tooltip title={t('searchFilms')}>
                <IconButton onClick={() => setCommittedQuery(query)} aria-label={t('searchFilms')} sx={goButtonSx}>
                  <ArrowForwardRounded />
                </IconButton>
              </Tooltip>
            </Box>
          </Box>

          <Box sx={{ mt: 3, display: 'flex', alignItems: 'center', gap: 1.25, flexWrap: 'wrap' }}>
            <Box sx={hotBadgeSx}>
              <LocalFireDepartment />
              {t('hotSearches')}
            </Box>
            {hotSearchTags}
          </Box>
        </Container>
      </Box>

      <Container maxWidth="xl">
        <Paper
          sx={{
            ...glassCardSx(theme),
            position: 'relative',
            overflow: 'hidden',
            isolation: 'isolate',
            mb: 4,
            p: { xs: 2.5, md: 3.5 },
          }}
        >
          <Box
            aria-hidden
            sx={{
              position: 'absolute',
              zIndex: -1,
              top: -120,
              right: -90,
              width: 300,
              height: 300,
              borderRadius: '50%',
              background: VIBE_GRADIENT,
              opacity: theme.palette.mode === 'dark' ? 0.14 : 0.1,
              filter: 'blur(60px)',
              pointerEvents: 'none',
            }}
          />

          <SectionHeader
            icon={<Tune />}
            title={t('searchFilters')}
            count={activeFilterCount || undefined}
            action={
              hasActiveFilters ? (
                <ButtonBase onClick={resetFilters} aria-label={t('resetFilters')} sx={resetPillSx(theme)}>
                  <RestartAlt />
                  <Box component="span" sx={{ display: { xs: 'none', sm: 'inline' } }}>
                    {t('resetFilters')}
                  </Box>
                </ButtonBase>
              ) : undefined
            }
            sx={{ mb: 3 }}
          />

          {hasActiveFilters && (
            <Box
              sx={{
                display: 'flex',
                flexWrap: 'wrap',
                gap: 1,
                mb: 3,
                pb: 3,
                borderBottom: '1px dashed',
                borderBottomColor: vibeBorder(theme),
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
            <FilterLabel icon={<Category />}>{t('filterByGenre')}</FilterLabel>
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
              <ButtonBase
                onClick={() => setFilters((f) => ({ ...f, genre: 'ALL' }))}
                aria-pressed={filters.genre === 'ALL'}
                sx={filterPillSx(theme, filters.genre === 'ALL')}
              >
                {t('allGenres')}
              </ButtonBase>
              {GENRE_VALUES.map((g) => (
                <ButtonBase
                  key={g}
                  onClick={() => setFilters((f) => ({ ...f, genre: f.genre === g ? 'ALL' : g }))}
                  aria-pressed={filters.genre === g}
                  sx={filterPillSx(theme, filters.genre === g)}
                >
                  {t(`genre.${g}`)}
                </ButtonBase>
              ))}
            </Box>
          </Box>

          <Box sx={{ mb: 3 }}>
            <FilterLabel icon={<Public />}>{t('filterByCountry')}</FilterLabel>
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
              <ButtonBase
                onClick={() => setFilters((f) => ({ ...f, country: 'ALL' }))}
                aria-pressed={filters.country === 'ALL'}
                sx={filterPillSx(theme, filters.country === 'ALL')}
              >
                {t('allCountries')}
              </ButtonBase>
              {COUNTRY_VALUES.map((c) => (
                <ButtonBase
                  key={c}
                  onClick={() => setFilters((f) => ({ ...f, country: f.country === c ? 'ALL' : c }))}
                  aria-pressed={filters.country === c}
                  sx={filterPillSx(theme, filters.country === c)}
                >
                  {t(`country.${c}`)}
                </ButtonBase>
              ))}
            </Box>
          </Box>

          <Divider sx={{ my: 3, borderStyle: 'dashed', borderColor: vibeBorder(theme) }} />

          <Box sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 2 }}>
            <Box sx={{ flex: { xs: '1 1 100%', sm: '0 1 300px' }, minWidth: 0 }}>
              <PillSelect
                value={filters.year}
                options={YEAR_OPTIONS}
                emptyValue="ALL"
                label={t('filterYear')}
                icon={<CalendarMonth />}
                renderOption={(y) => (y === 'ALL' ? t('allYears') : y)}
                onChange={(year) => setFilters((f) => ({ ...f, year }))}
                columns={3}
              />
            </Box>

            <Box sx={{ flex: { xs: '1 1 100%', sm: '0 1 300px' }, minWidth: 0 }}>
              <PillSelect<MinRating>
                value={filters.minRating}
                options={MIN_RATING_OPTIONS}
                emptyValue={0}
                label={t('filterMinRating')}
                icon={<StarRounded />}
                renderOption={(r) =>
                  r === 0 ? (
                    t('anyRating')
                  ) : (
                    <Box component="span" sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5 }}>
                      <StarRounded sx={{ fontSize: 18, color: '#FFC83D' }} />≥ {r.toFixed(1)}
                    </Box>
                  )
                }
                onChange={(minRating) => setFilters((f) => ({ ...f, minRating }))}
              />
            </Box>

            <Box
              sx={{
                display: 'flex',
                alignItems: 'center',
                gap: 0.75,
                flex: '1 1 240px',
                color: 'text.secondary',
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
