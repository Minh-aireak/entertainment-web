import React from 'react';
import { alpha, Box, Button, Card, CardContent, Chip, IconButton, Typography, useTheme } from '@mui/material';
import { PlayArrow, Star, DeleteOutlined } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import type { FilmStatus, FilmSummaryResponse } from '../../models';

interface FilmCardProps {
  film: FilmSummaryResponse;
  onRemove?: (film: FilmSummaryResponse) => void;
  variant?: 'poster' | 'fill';
  size?: 'md' | 'lg';
}

const STATUS_KEY: Record<FilmStatus, string> = {
  ONGOING: 'filmStatus.ongoing',
  COMPLETED: 'filmStatus.completed',
};

const getStatusColor = (status?: FilmStatus) => {
  switch (status) {
    case 'ONGOING':
      return 'success' as const;
    case 'COMPLETED':
      return 'default' as const;
    default:
      return 'default' as const;
  }
};

const FilmCard: React.FC<FilmCardProps> = React.memo(({ film, onRemove, variant = 'poster', size = 'md' }) => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const theme = useTheme();
  const isFill = variant === 'fill';
  const isLg = size === 'lg';

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
        border: '1px solid',
        borderColor: alpha(theme.palette.text.primary, 0.05),
      }}
    >
      <Box sx={{ position: 'relative', width: '100%', height: isFill ? '100%' : undefined, aspectRatio: isFill ? undefined : '2 / 3', overflow: 'hidden', bgcolor: '#000' }}>
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
              label={t(STATUS_KEY[film.status])}
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
            bgcolor: isFill ? 'transparent' : 'rgba(0,0,0,0.6)',
            background: isFill
              ? 'linear-gradient(to top, rgba(0,0,0,0.85) 0%, rgba(0,0,0,0.35) 55%, transparent 100%)'
              : undefined,
            display: 'flex',
            flexDirection: 'column',
            justifyContent: 'flex-end',
            p: isFill ? 1 : 1.5,
            opacity: isFill ? 1 : 0,
            transition: 'opacity 0.3s ease-in-out',
          }}
        >
          {isFill ? (
            <>
              {film.genres && film.genres.length > 0 && (
                <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap', mb: 0.5 }}>
                  {film.genres.slice(0, 2).map((genre) => (
                    <Chip
                      key={genre}
                      label={t(`genre.${genre}`, { defaultValue: genre })}
                      size="small"
                      sx={{
                        bgcolor: 'rgba(255,255,255,0.15)',
                        color: 'white',
                        fontWeight: 600,
                        height: 18,
                        fontSize: '0.65rem',
                        '& .MuiChip-label': { px: 0.75 },
                      }}
                    />
                  ))}
                </Box>
              )}
              <Typography
                variant="body2"
                sx={{
                  color: 'white',
                  fontWeight: 700,
                  mb: 0.5,
                  display: '-webkit-box',
                  WebkitLineClamp: 2,
                  WebkitBoxOrient: 'vertical',
                  overflow: 'hidden',
                  lineHeight: 1.25,
                }}
              >
                {film.title}
              </Typography>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                <Star sx={{ color: '#FFD700', fontSize: 14 }} />
                <Typography variant="caption" sx={{ color: 'white', fontWeight: 700 }}>
                  {film.averageRating.toFixed(1)}
                </Typography>
              </Box>
            </>
          ) : (
            <>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mb: isLg ? 1.25 : 1 }}>
                <Star sx={{ color: '#FFD700', fontSize: isLg ? 18 : 16 }} />
                <Typography
                  variant="caption"
                  sx={{ color: 'white', fontWeight: 700, fontSize: isLg ? '0.85rem' : undefined }}
                >
                  {film.averageRating.toFixed(1)}
                </Typography>
                <Typography
                  variant="caption"
                  sx={{ color: 'rgba(255,255,255,0.7)', fontSize: isLg ? '0.8rem' : undefined }}
                >
                  ({film.ratingCount})
                </Typography>
              </Box>
              <Button
                variant="contained"
                size={isLg ? 'medium' : 'small'}
                fullWidth
                startIcon={<PlayArrow fontSize={isLg ? 'medium' : 'small'} />}
                sx={{
                  bgcolor: 'primary.main',
                  fontWeight: 700,
                  fontSize: isLg ? '0.95rem' : '0.8125rem',
                  py: isLg ? 1 : 0.5,
                  borderRadius: isLg ? 2 : 1,
                  letterSpacing: '0.02em',
                  boxShadow: isLg ? '0 4px 14px rgba(0,168,78,0.35)' : 'none',
                  transition: 'transform 0.15s ease, box-shadow 0.15s ease',
                  '&:hover': {
                    bgcolor: 'primary.dark',
                    transform: isLg ? 'scale(1.03)' : undefined,
                    boxShadow: isLg ? '0 6px 18px rgba(0,168,78,0.5)' : undefined,
                  },
                }}
              >
                {t('watchNow')}
              </Button>
            </>
          )}
        </Box>
      </Box>

      {!isFill && (
        <CardContent sx={{ p: isLg ? 1.75 : 1.5, '&:last-child': { pb: isLg ? 1.75 : 1.5 } }}>
          <Typography
            variant={isLg ? 'subtitle1' : 'subtitle2'}
            noWrap
            sx={{ fontWeight: 700, fontSize: isLg ? '1rem' : undefined }}
          >
            {film.title}
          </Typography>
          <Typography
            variant={isLg ? 'body2' : 'caption'}
            color="text.secondary"
            sx={{ fontSize: isLg ? '0.85rem' : undefined }}
          >
            {film.episodeCount > 0 ? t('episodeCount', { count: film.episodeCount }) : t('standaloneFilm')} • {new Date(film.lastUpdate).getFullYear()}
          </Typography>
        </CardContent>
      )}
    </Card>
  );
});

FilmCard.displayName = 'FilmCard';

export default FilmCard;
