import React, { useEffect, useState } from 'react';
import { Box, Container, Typography, Skeleton, Pagination } from '@mui/material';
import { useTranslation } from 'react-i18next';
import { filmService } from '../../api/filmService';
import type { FilmSummaryResponse, PageResponse } from '../../models';
import FilmCard from '../../components/Film/FilmCard';

const PAGE_SIZE = 20;

const GRID_COLUMNS = { xs: 'repeat(2, 1fr)', sm: 'repeat(3, 1fr)', md: 'repeat(5, 1fr)' };

const FilmLatest: React.FC = React.memo(() => {
  const { t } = useTranslation();
  const [pageData, setPageData] = useState<PageResponse<FilmSummaryResponse> | null>(null);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    filmService
      .getPageFilms(page, PAGE_SIZE)
      .then((res) => {
        if (!cancelled) setPageData(res.result);
      })
      .catch((error) => {
        console.error('Failed to fetch latest films:', error);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [page]);

  return (
    <Container maxWidth="xl" sx={{ py: 6 }}>
      <Typography variant="h3" sx={{ fontWeight: 900, mb: 4 }}>
        {t('latestUpdatesNav')}
      </Typography>

      <Box sx={{ display: 'grid', gridTemplateColumns: GRID_COLUMNS, gap: 3 }}>
        {loading
          ? Array.from(new Array(PAGE_SIZE)).map((_, index) => (
              <Skeleton key={index} variant="rectangular" sx={{ aspectRatio: '2 / 3', borderRadius: 2 }} />
            ))
          : pageData?.data.map((film) => <FilmCard key={film.id} film={film} />)}
      </Box>

      {!loading && !pageData?.data.length && (
        <Typography variant="body2" color="text.secondary" sx={{ py: 6, textAlign: 'center' }}>
          —
        </Typography>
      )}

      {(pageData?.totalPages ?? 0) > 1 && (
        <Box sx={{ mt: 8, display: 'flex', justifyContent: 'center' }}>
          <Pagination
            count={pageData?.totalPages || 1}
            page={page}
            onChange={(_, value) => setPage(value)}
            color="primary"
            size="large"
            sx={{ '& .MuiPaginationItem-root': { color: 'white', fontWeight: 600 } }}
          />
        </Box>
      )}
    </Container>
  );
});

FilmLatest.displayName = 'FilmLatest';

export default FilmLatest;
