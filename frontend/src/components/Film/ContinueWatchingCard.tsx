import React from 'react';
import { Box, Card, IconButton, Typography, alpha, useTheme } from '@mui/material';
import { PlayArrow, Close } from '@mui/icons-material';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import type { WatchProgressResponse } from '../../models';

interface ContinueWatchingCardProps {
  progress: WatchProgressResponse;
  onRemove?: (progress: WatchProgressResponse) => void;
}

const ContinueWatchingCard: React.FC<ContinueWatchingCardProps> = React.memo(({ progress, onRemove }) => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const theme = useTheme();

  const ratio = progress.durationSeconds > 0
    ? Math.min(progress.positionSeconds / progress.durationSeconds, 1)
    : 0;
  const remainingMinutes = Math.max(Math.round((progress.durationSeconds - progress.positionSeconds) / 60), 0);

  const handleOpen = () => {
    if (progress.episodeId) {
      navigate(`/film/${progress.filmId}/watch/${progress.episodeId}`);
    } else {
      navigate(`/film/${progress.filmId}`);
    }
  };

  const handleRemove = (e: React.MouseEvent) => {
    e.stopPropagation();
    onRemove?.(progress);
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
          '& .continue-watching-overlay': { opacity: 1 },
        },
        bgcolor: 'background.paper',
        borderRadius: 2,
        overflow: 'hidden',
        border: '1px solid',
        borderColor: alpha(theme.palette.text.primary, 0.05),
      }}
    >
      <Box sx={{ position: 'relative', width: '100%', aspectRatio: '2 / 3', overflow: 'hidden', bgcolor: '#000' }}>
        {progress.thumbnailUrl && (
          <Box
            component="img"
            src={progress.thumbnailUrl}
            alt={progress.filmTitle}
            loading="lazy"
            sx={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }}
          />
        )}

        <Box sx={{ position: 'absolute', top: 10, left: 10, zIndex: 1 }}>
          <Box sx={{ px: 1, py: 0.4, borderRadius: 1, bgcolor: 'rgba(0,0,0,0.65)', backdropFilter: 'blur(4px)' }}>
            <Typography sx={{ color: '#fff', fontSize: '0.65rem', fontWeight: 800, letterSpacing: '0.04em' }}>
              {progress.episodeNumber
                ? t('episodeBadge', { episode: progress.episodeNumber, total: progress.totalEpisodes || progress.episodeNumber })
                : t('standaloneFilm')}
            </Typography>
          </Box>
        </Box>

        {onRemove && (
          <IconButton
            className="continue-watching-overlay"
            size="small"
            onClick={handleRemove}
            sx={{
              position: 'absolute',
              top: 8,
              right: 8,
              zIndex: 1,
              bgcolor: 'rgba(0,0,0,0.6)',
              color: '#fff',
              opacity: 0,
              transition: 'opacity 0.2s',
              '&:hover': { bgcolor: 'error.main' },
            }}
          >
            <Close fontSize="small" />
          </IconButton>
        )}

        <Box
          className="continue-watching-overlay"
          sx={{
            position: 'absolute',
            inset: 0,
            background: 'linear-gradient(to top, rgba(0,0,0,0.85) 0%, transparent 55%)',
            display: 'flex',
            alignItems: 'flex-end',
            justifyContent: 'center',
            opacity: 0,
            transition: 'opacity 0.2s',
          }}
        >
          <Box
            sx={{
              width: 52,
              height: 52,
              borderRadius: '50%',
              bgcolor: 'rgba(0,168,78,0.9)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              mb: 3,
            }}
          >
            <PlayArrow sx={{ color: '#fff', fontSize: 28 }} />
          </Box>
        </Box>

        <Box sx={{ position: 'absolute', left: 0, right: 0, bottom: 0, height: 4, bgcolor: 'rgba(255,255,255,0.25)' }}>
          <Box sx={{ height: '100%', width: `${ratio * 100}%`, bgcolor: 'primary.main' }} />
        </Box>
      </Box>

      <Box sx={{ p: 1.5 }}>
        <Typography variant="subtitle2" noWrap sx={{ fontWeight: 700 }}>
          {progress.filmTitle}
        </Typography>
        <Typography variant="caption" color="text.secondary">
          {remainingMinutes > 0 ? t('remainingMinutes', { count: remainingMinutes }) : t('almostDone')}
        </Typography>
      </Box>
    </Card>
  );
});

ContinueWatchingCard.displayName = 'ContinueWatchingCard';

export default ContinueWatchingCard;
