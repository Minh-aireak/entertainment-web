import React from 'react';
import { Box, Button, Chip, Typography } from '@mui/material';
import { PlayArrow, Star } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import type { FilmSummaryResponse } from '../../models';
import { GENRE_LABELS_VI } from '../../constants/film';

interface FilmCardFeaturedProps {
  film: FilmSummaryResponse;
}

const FilmCardFeatured: React.FC<FilmCardFeaturedProps> = React.memo(({ film }) => {
  const { t } = useTranslation();
  const navigate = useNavigate();

  const handleOpen = () => navigate(`/film/${film.id}`);

  return (
    <Box
      onClick={handleOpen}
      sx={{
        position: 'relative',
        width: '100%',
        height: '100%',
        borderRadius: 2,
        overflow: 'hidden',
        cursor: 'pointer',
        border: '1px solid rgba(255,255,255,0.05)',
        transition: 'transform 0.3s ease-in-out',
        bgcolor: '#000',
        '&:hover': { transform: 'translateY(-4px)' },
      }}
    >
      <Box
        component="img"
        src={film.thumbnailUrl || 'https://via.placeholder.com/600x600?text=No+Thumbnail'}
        alt={film.title}
        loading="lazy"
        sx={{ position: 'absolute', inset: 0, width: '100%', height: '100%', objectFit: 'cover' }}
      />
      <Box
        sx={{
          position: 'absolute',
          inset: 0,
          background: 'linear-gradient(to top, rgba(0,0,0,0.92) 0%, rgba(0,0,0,0.35) 55%, transparent 100%)',
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'flex-end',
          p: { xs: 1.5, md: 2.5 },
        }}
      >
        {film.genres && film.genres.length > 0 && (
          <Box sx={{ display: 'flex', gap: 0.75, flexWrap: 'wrap', mb: 1 }}>
            {film.genres.slice(0, 3).map((genre) => (
              <Chip
                key={genre}
                label={GENRE_LABELS_VI[genre] ?? genre}
                size="small"
                sx={{ bgcolor: 'rgba(255,255,255,0.15)', color: 'white', fontWeight: 600, height: 22 }}
              />
            ))}
          </Box>
        )}
        <Typography
          variant="h5"
          sx={{
            color: 'white',
            fontWeight: 800,
            mb: 1,
            display: '-webkit-box',
            WebkitLineClamp: 2,
            WebkitBoxOrient: 'vertical',
            overflow: 'hidden',
            lineHeight: 1.2,
          }}
        >
          {film.title}
        </Typography>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 1.5 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
            <Star sx={{ color: '#FFD700', fontSize: 18 }} />
            <Typography variant="body2" sx={{ color: 'white', fontWeight: 700 }}>
              {film.averageRating.toFixed(1)}
            </Typography>
          </Box>
          <Typography variant="body2" sx={{ color: 'rgba(255,255,255,0.7)' }}>
            {film.episodeCount > 0 ? `${film.episodeCount} tập` : 'Phim lẻ'}
          </Typography>
        </Box>
        <Button
          variant="contained"
          size="medium"
          startIcon={<PlayArrow />}
          sx={{ bgcolor: 'primary.main', alignSelf: 'flex-start' }}
          onClick={(e) => {
            e.stopPropagation();
            handleOpen();
          }}
        >
          {t('watchNow')}
        </Button>
      </Box>
    </Box>
  );
});

FilmCardFeatured.displayName = 'FilmCardFeatured';

export default FilmCardFeatured;
