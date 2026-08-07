import React, { useEffect, useState } from 'react';
import {
  Box,
  Typography,
  Container,
  Grid,
  Skeleton,
  Pagination,
  TextField,
  InputAdornment,
  Button,
  Alert,
  Paper,
  ToggleButton,
  ToggleButtonGroup,
} from '@mui/material';
import {
  Search,
  TrendingUp,
  PlayCircleOutlined,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { filmService } from '../../api/filmService';
import type { FilmSummaryResponse, PageResponse } from '../../models';
import FilmCard from '../../components/Film/FilmCard';

const FilmTrending: React.FC = React.memo(() => {
  const { t } = useTranslation();
  const [pageData, setPageData] = useState<PageResponse<FilmSummaryResponse> | null>(null);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [searchQuery, setSearchQuery] = useState('');
  const [view, setView] = useState<'all' | 'nowPlaying'>('all');
  const [error, setError] = useState<string | null>(null);

  const fetchFilms = async (currentPage: number) => {
    setLoading(true);
    setError(null);
    try {
      const response = view === 'nowPlaying'
        ? await filmService.getNowPlayingFilms(currentPage, 12)
        : await filmService.getPageFilms(currentPage, 12);
      setPageData(response.result);
    } catch (error) {
      console.error('Failed to fetch films:', error);
      setError('Không thể tải danh sách phim. Vui lòng thử lại.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchFilms(page);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, view]);

  const handleSearch = async () => {
    const normalizedQuery = searchQuery.trim();
    if (normalizedQuery) {
      setLoading(true);
      setError(null);
      try {
        const response = await filmService.searchFilms(normalizedQuery);
        setPage(1);
        setPageData({
          currentPage: 1,
          pageSize: response.result.length,
          totalPages: 1,
          totalElement: response.result.length,
          data: response.result
        });
      } catch (error) {
        console.error('Search failed:', error);
        setError('Không thể tìm kiếm phim lúc này.');
      } finally {
        setLoading(false);
      }
    } else {
      setPage(1);
      fetchFilms(1);
    }
  };

  const handleSearchKeyDown = (event: React.KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Enter') handleSearch();
  };

  return (
    <Container maxWidth="xl" sx={{ py: 6 }}>
      <Box sx={{ mb: 6 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 1 }}>
          <TrendingUp color="primary" sx={{ fontSize: 32 }} />
          <Typography variant="h3" sx={{ fontWeight: 900 }}>{t('trendingNow')}</Typography>
        </Box>
        <Typography variant="body1" color="text.secondary">{t('discoverPopular')}</Typography>
      </Box>

      <Box sx={{ mb: 6, display: 'flex', gap: 2, flexWrap: 'wrap' }}>
        <TextField
          fullWidth
          placeholder="Search for movies, actors, directors..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          onKeyDown={handleSearchKeyDown}
          sx={{
            maxWidth: 500,
            '& .MuiOutlinedInput-root': {
              borderRadius: 3,
              bgcolor: 'background.paper',
            }
          }}
          slotProps={{
            input: {
              startAdornment: (
                <InputAdornment position="start">
                  <Search sx={{ color: 'text.secondary' }} />
                </InputAdornment>
              ),
            },
          }}
        />
        <Button
          variant="contained"
          startIcon={<Search />}
          onClick={handleSearch}
          sx={{ borderRadius: 3, px: 3 }}
        >
          Tìm kiếm
        </Button>
        <ToggleButtonGroup
          exclusive
          value={view}
          onChange={(_, nextView) => {
            if (nextView) {
              setView(nextView);
              setPage(1);
              setSearchQuery('');
            }
          }}
          size="small"
          sx={{ '& .MuiToggleButton-root': { px: 2.5, color: 'text.secondary', borderColor: 'rgba(255,255,255,0.12)' } }}
        >
          <ToggleButton value="all">Tất cả</ToggleButton>
          <ToggleButton value="nowPlaying"><PlayCircleOutlined sx={{ mr: 1 }} />Đang cập nhật</ToggleButton>
        </ToggleButtonGroup>
      </Box>

      {error && <Alert severity="error" sx={{ mb: 3 }}>{error}</Alert>}

      {loading ? (
        <Grid container spacing={3}>
          {Array.from(new Array(12)).map((_, index) => (
            <Grid size={{ xs: 12, sm: 6, md: 4, lg: 3 }} key={index}>
              <Skeleton variant="rectangular" sx={{ aspectRatio: '2 / 3', borderRadius: 2 }} />
              <Skeleton variant="text" sx={{ mt: 2 }} />
              <Skeleton variant="text" width="60%" />
            </Grid>
          ))}
        </Grid>
      ) : (
        <>
          <Grid container spacing={3}>
            {pageData?.data.map((film) => (
              <Grid size={{ xs: 12, sm: 6, md: 4, lg: 3 }} key={film.id}>
                <FilmCard film={film} />
              </Grid>
            ))}
          </Grid>

          {!pageData?.data.length && (
            <Paper sx={{ py: 10, px: 3, textAlign: 'center', borderRadius: 4, bgcolor: 'rgba(255,255,255,0.03)' }}>
              <Search sx={{ fontSize: 60, color: 'text.disabled', mb: 2 }} />
              <Typography variant="h6" sx={{ fontWeight: 700 }}>Không tìm thấy phim phù hợp</Typography>
              <Typography color="text.secondary">Thử từ khóa khác hoặc chuyển sang bộ lọc Tất cả.</Typography>
            </Paper>
          )}

          {(pageData?.totalPages ?? 0) > 1 && <Box sx={{ mt: 8, display: 'flex', justifyContent: 'center' }}>
            <Pagination
              count={pageData?.totalPages || 1}
              page={page}
              onChange={(_, value) => setPage(value)}
              color="primary"
              size="large"
              sx={{
                '& .MuiPaginationItem-root': {
                  color: 'white',
                  fontWeight: 600
                }
              }}
            />
          </Box>}
        </>
      )}
    </Container>
  );
});

FilmTrending.displayName = 'FilmTrending';

export default FilmTrending;
