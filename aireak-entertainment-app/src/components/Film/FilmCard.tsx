import React from 'react';
import { Box, Button, Card, CardContent, Chip, IconButton, Typography } from '@mui/material';
import { PlayArrow, Star, DeleteOutlined } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import type { FilmStatus, FilmSummaryResponse } from '../../models';

interface FilmCardProps {
  film: FilmSummaryResponse;
  onRemove?: (film: FilmSummaryResponse) => void;
}

const getStatusColor = (status?: FilmStatus) => {
  switch (status) {
    case 'NOW_PLAYING':
      return 'error' as const;
    case 'UPCOMING':
      return 'primary' as const;
    default:
      return 'default' as const;
  }
};

const FilmCard: React.FC<FilmCardProps> = React.memo(({ film, onRemove }) => {
  const { t } = useTranslation();
  const navigate = useNavigate();

  const handleOpen = () => navigate(`/film/${film.id}`);

  const handleRemove = (e: React.MouseEvent) => {
    e.stopPropagation();
    onRemove?.(film);
  };

  return (
    <Card
      onClick={handleOpen}
      sx={{
        height: '100%',
        position: 'relative',
        cursor: 'pointer',
        transition: 'transform 0.3s ease-in-out',
        '&:hover': {
          transform: 'translateY(-6px)',
          '& .film-overlay': { opacity: 1 },
        },
        bgcolor: 'background.paper',
        borderRadius: 2,
        overflow: 'hidden',
        border: '1px solid rgba(255,255,255,0.05)',
      }}
    >
      <Box sx={{ position: 'relative', width: '100%', aspectRatio: '2 / 3' }}>
        <Box
          component="img"
          src={film.thumbnailUrl || 'https://via.placeholder.com/300x450?text=No+Thumbnail'}
          alt={film.title}
          loading="lazy"
          sx={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }}
        />

        {film.status && (
          <Box sx={{ position: 'absolute', top: 10, left: 10, zIndex: 1 }}>
            <Chip
              label={film.status.replace('_', ' ')}
              color={getStatusColor(film.status)}
              size="small"
              sx={{ fontWeight: 700, height: 22 }}
            />
          </Box>
        )}

        {onRemove && (
          <IconButton
            size="small"
            onClick={handleRemove}
            sx={{
              position: 'absolute',
              top: 8,
              right: 8,
              zIndex: 1,
              bgcolor: 'rgba(0,0,0,0.6)',
              color: 'error.main',
              '&:hover': { bgcolor: 'error.main', color: 'white' },
            }}
          >
            <DeleteOutlined fontSize="small" />
          </IconButton>
        )}

        <Box
          className="film-overlay"
          sx={{
            position: 'absolute',
            inset: 0,
            bgcolor: 'rgba(0,0,0,0.6)',
            display: 'flex',
            flexDirection: 'column',
            justifyContent: 'flex-end',
            p: 1.5,
            opacity: 0,
            transition: 'opacity 0.3s ease-in-out',
          }}
        >
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mb: 1 }}>
            <Star sx={{ color: '#FFD700', fontSize: 16 }} />
            <Typography variant="caption" sx={{ color: 'white', fontWeight: 700 }}>
              {film.averageRating.toFixed(1)}
            </Typography>
            <Typography variant="caption" sx={{ color: 'rgba(255,255,255,0.7)' }}>
              ({film.ratingCount})
            </Typography>
          </Box>
          <Button
            variant="contained"
            size="small"
            fullWidth
            startIcon={<PlayArrow fontSize="small" />}
            sx={{ bgcolor: 'primary.main' }}
          >
            {t('watchNow')}
          </Button>
        </Box>
      </Box>

      <CardContent sx={{ p: 1.5, '&:last-child': { pb: 1.5 } }}>
        <Typography variant="subtitle2" noWrap sx={{ fontWeight: 600 }}>
          {film.title}
        </Typography>
        <Typography variant="caption" color="text.secondary">
          {film.episodeCount > 0 ? `${film.episodeCount} Episodes` : 'Movie'} • {new Date(film.lastUpdate).getFullYear()}
        </Typography>
      </CardContent>
    </Card>
  );
});

FilmCard.displayName = 'FilmCard';

export default FilmCard;
