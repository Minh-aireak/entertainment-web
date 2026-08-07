import React, { useCallback, useEffect, useState } from 'react';
import {
  Box,
  Container,
  Typography,
  Grid,
  Skeleton,
  CircularProgress,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  IconButton,
} from '@mui/material';
import { SwapVert } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { filmService } from '../../api/filmService';
import type { Country, FilmCategory, FilmSortField, FilmSummaryResponse, Genre, SortDirection } from '../../models';
import { COUNTRY_LABELS_VI, COUNTRY_VALUES, GENRE_LABELS_VI, GENRE_VALUES } from '../../constants/film';
import { useInfiniteScroll } from '../../hooks/useInfiniteScroll';
import FilmCard from '../../components/Film/FilmCard';

interface FilmCategoryBrowseProps {
  category: FilmCategory;
  titleKey: string;
  countryFilterEnabled?: boolean;
}

const PAGE_SIZE = 20;

const SORT_FIELD_KEYS: Record<FilmSortField, string> = {
  LAST_UPDATE: 'sortLastUpdate',
  AVERAGE_RATING: 'sortAverageRating',
  FOLLOW_COUNT: 'sortFollowCount',
  RELEASE_DATE: 'sortReleaseDate',
};

const FilmCategoryBrowse: React.FC<FilmCategoryBrowseProps> = React.memo(
  ({ category, titleKey, countryFilterEnabled }) => {
    const { t } = useTranslation();
    const [country, setCountry] = useState<Country | 'ALL'>('ALL');
    const [genre, setGenre] = useState<Genre | 'ALL'>('ALL');
    const [sortBy, setSortBy] = useState<FilmSortField>('LAST_UPDATE');
    const [sortDir, setSortDir] = useState<SortDirection>('DESC');

    const [films, setFilms] = useState<FilmSummaryResponse[]>([]);
    const [page, setPage] = useState(1);
    const [hasMore, setHasMore] = useState(true);
    const [loading, setLoading] = useState(true);
    const [loadingMore, setLoadingMore] = useState(false);

    const showGenreFilter = category !== 'ANIMATION';

    const fetchPage = useCallback(
      async (targetPage: number, replace: boolean) => {
        if (replace) setLoading(true);
        else setLoadingMore(true);
        try {
          const res = await filmService.browseFilms({
            category,
            country: country === 'ALL' ? undefined : country,
            genre: showGenreFilter && genre !== 'ALL' ? genre : undefined,
            sortBy,
            sortDir,
            page: targetPage,
            size: PAGE_SIZE,
          });
          setFilms((prev) => (replace ? res.result.data : [...prev, ...res.result.data]));
          setHasMore(targetPage < res.result.totalPages);
          setPage(targetPage);
        } catch (error) {
          console.error('Failed to browse films:', error);
          if (replace) setFilms([]);
        } finally {
          setLoading(false);
          setLoadingMore(false);
        }
      },
      [category, country, genre, showGenreFilter, sortBy, sortDir]
    );

    useEffect(() => {
      fetchPage(1, true);
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [category, country, genre, sortBy, sortDir]);

    const handleLoadMore = useCallback(() => {
      if (loading || loadingMore || !hasMore) return;
      fetchPage(page + 1, false);
    }, [loading, loadingMore, hasMore, page, fetchPage]);

    const sentinelRef = useInfiniteScroll({
      hasMore,
      loading: loading || loadingMore,
      onLoadMore: handleLoadMore,
    });

    return (
      <Container maxWidth="xl" sx={{ py: 6 }}>
        <Typography variant="h3" sx={{ fontWeight: 900, mb: 3 }}>
          {t(titleKey)}
        </Typography>

        <Box sx={{ display: 'flex', gap: 2, mb: 4, flexWrap: 'wrap', alignItems: 'center' }}>
          {countryFilterEnabled && (
            <FormControl size="small" sx={{ minWidth: 160 }}>
              <InputLabel id="country-filter-label">{t('filterByCountry')}</InputLabel>
              <Select
                labelId="country-filter-label"
                label={t('filterByCountry')}
                value={country}
                onChange={(e) => setCountry(e.target.value as Country | 'ALL')}
              >
                <MenuItem value="ALL">{t('allCountries')}</MenuItem>
                {COUNTRY_VALUES.map((c) => (
                  <MenuItem key={c} value={c}>{COUNTRY_LABELS_VI[c]}</MenuItem>
                ))}
              </Select>
            </FormControl>
          )}

          {showGenreFilter && (
            <FormControl size="small" sx={{ minWidth: 160 }}>
              <InputLabel id="genre-filter-label">{t('filterByGenre')}</InputLabel>
              <Select
                labelId="genre-filter-label"
                label={t('filterByGenre')}
                value={genre}
                onChange={(e) => setGenre(e.target.value as Genre | 'ALL')}
              >
                <MenuItem value="ALL">{t('allGenres')}</MenuItem>
                {GENRE_VALUES.filter((g) => g !== 'ANIMATION').map((g) => (
                  <MenuItem key={g} value={g}>{GENRE_LABELS_VI[g]}</MenuItem>
                ))}
              </Select>
            </FormControl>
          )}

          <FormControl size="small" sx={{ minWidth: 180 }}>
            <InputLabel id="sort-by-label">{t('sortBy')}</InputLabel>
            <Select
              labelId="sort-by-label"
              label={t('sortBy')}
              value={sortBy}
              onChange={(e) => setSortBy(e.target.value as FilmSortField)}
            >
              {(Object.keys(SORT_FIELD_KEYS) as FilmSortField[]).map((field) => (
                <MenuItem key={field} value={field}>{t(SORT_FIELD_KEYS[field])}</MenuItem>
              ))}
            </Select>
          </FormControl>

          <IconButton
            onClick={() => setSortDir((prev) => (prev === 'DESC' ? 'ASC' : 'DESC'))}
            sx={{ bgcolor: 'rgba(255,255,255,0.06)' }}
            title={sortDir === 'DESC' ? 'Giảm dần' : 'Tăng dần'}
          >
            <SwapVert sx={{ transform: sortDir === 'ASC' ? 'scaleY(-1)' : undefined }} />
          </IconButton>
        </Box>

        {loading ? (
          <Grid container spacing={3}>
            {Array.from(new Array(PAGE_SIZE)).map((_, index) => (
              <Grid size={{ xs: 12, sm: 6, md: 4, lg: 3 }} key={index}>
                <Skeleton variant="rectangular" sx={{ aspectRatio: '2 / 3', borderRadius: 2 }} />
              </Grid>
            ))}
          </Grid>
        ) : (
          <Grid container spacing={3}>
            {films.map((film) => (
              <Grid size={{ xs: 12, sm: 6, md: 4, lg: 3 }} key={film.id}>
                <FilmCard film={film} />
              </Grid>
            ))}
          </Grid>
        )}

        {!loading && films.length === 0 && (
          <Typography variant="body2" color="text.secondary" sx={{ py: 6, textAlign: 'center' }}>
            —
          </Typography>
        )}

        <Box ref={sentinelRef} sx={{ display: 'flex', justifyContent: 'center', py: 4, minHeight: 40 }}>
          {loadingMore && <CircularProgress size={26} />}
        </Box>
      </Container>
    );
  }
);

FilmCategoryBrowse.displayName = 'FilmCategoryBrowse';

export default FilmCategoryBrowse;
