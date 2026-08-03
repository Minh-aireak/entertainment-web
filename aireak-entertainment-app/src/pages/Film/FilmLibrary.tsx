import React, { useEffect, useState } from 'react';
import {
  Box,
  Typography,
  Container,
  Grid,
  Skeleton,
  Button,
  Alert,
} from '@mui/material';
import { Refresh, Search } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { filmService } from '../../api/filmService';
import type { FilmSummaryResponse } from '../../models';
import { useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import FilmCard from '../../components/Film/FilmCard';

const FilmLibrary: React.FC = React.memo(() => {
  const { t } = useTranslation();
  const [films, setFilms] = useState<FilmSummaryResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const navigate = useNavigate();

  const fetchLibrary = async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await filmService.getMyFollowedFilms();
      setFilms(response.result ?? []);
    } catch (error) {
      console.error('Failed to fetch library:', error);
      setError('Không thể tải thư viện phim của bạn.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchLibrary();
  }, []);

  const handleRemove = async (film: FilmSummaryResponse) => {
    try {
      await filmService.processFollowAction(film.id, true);
      setFilms((prev) => prev.filter((f) => f.id !== film.id));
      window.dispatchEvent(new Event('sidebar-counts:refresh'));
      toast.success('Removed from library');
    } catch {
      toast.error('Failed to remove from library');
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
          {films.length} {films.length === 1 ? 'Film' : 'Films'}
        </Typography>
      </Box>

      {error && (
        <Alert
          severity="error"
          action={<Button color="inherit" size="small" startIcon={<Refresh />} onClick={fetchLibrary}>Thử lại</Button>}
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
            bgcolor: 'rgba(255,255,255,0.02)',
            borderRadius: 8,
            border: '2px dashed rgba(255,255,255,0.05)'
          }}
        >
          <Search sx={{ fontSize: 80, color: 'rgba(255,255,255,0.1)', mb: 2 }} />
          <Typography variant="h5" sx={{ mb: 1, fontWeight: 700 }}>Your library is empty</Typography>
          <Typography variant="body1" color="text.secondary" sx={{ mb: 4 }}>
            Explore our collection and add your favorite films here.
          </Typography>
          <Button
            variant="contained"
            size="large"
            onClick={() => navigate('/film')}
            sx={{ borderRadius: 2, px: 4 }}
          >
            Explore Movies
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
