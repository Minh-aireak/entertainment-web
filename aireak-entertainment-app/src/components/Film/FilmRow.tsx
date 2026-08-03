import React, { useCallback, useRef } from 'react';
import { Box, Button, IconButton, Skeleton, Typography } from '@mui/material';
import { ChevronLeft, ChevronRight } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import type { FilmSummaryResponse } from '../../models';
import FilmCard from './FilmCard';

interface FilmRowProps {
  title: string;
  films: FilmSummaryResponse[];
  loading?: boolean;
  onSeeAll?: () => void;
}

const CARD_WIDTH = 200;

const FilmRow: React.FC<FilmRowProps> = React.memo(({ title, films, loading, onSeeAll }) => {
  const { t } = useTranslation();
  const scrollRef = useRef<HTMLDivElement>(null);

  const scrollBy = useCallback((direction: 1 | -1) => {
    scrollRef.current?.scrollBy({ left: direction * CARD_WIDTH * 3, behavior: 'smooth' });
  }, []);

  return (
    <Box sx={{ mb: 5 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
        <Typography variant="h5" sx={{ fontWeight: 800, letterSpacing: '-0.02em' }}>
          {title}
        </Typography>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
          {onSeeAll && (
            <Button onClick={onSeeAll} sx={{ color: 'primary.main', fontWeight: 600, mr: 1 }}>
              {t('explore')}
            </Button>
          )}
          <IconButton size="small" onClick={() => scrollBy(-1)} sx={{ bgcolor: 'rgba(255,255,255,0.06)' }}>
            <ChevronLeft />
          </IconButton>
          <IconButton size="small" onClick={() => scrollBy(1)} sx={{ bgcolor: 'rgba(255,255,255,0.06)' }}>
            <ChevronRight />
          </IconButton>
        </Box>
      </Box>

      <Box
        ref={scrollRef}
        sx={{
          display: 'flex',
          gap: 2,
          overflowX: 'auto',
          scrollSnapType: 'x mandatory',
          pb: 1,
          '&::-webkit-scrollbar': { height: 6 },
          '&::-webkit-scrollbar-thumb': { bgcolor: 'rgba(255,255,255,0.15)', borderRadius: 3 },
        }}
      >
        {loading
          ? Array.from(new Array(6)).map((_, index) => (
              <Box key={index} sx={{ flex: `0 0 ${CARD_WIDTH}px`, scrollSnapAlign: 'start' }}>
                <Skeleton variant="rounded" sx={{ width: CARD_WIDTH, aspectRatio: '2 / 3', borderRadius: 2 }} animation="wave" />
                <Skeleton variant="text" animation="wave" sx={{ mt: 1 }} />
                <Skeleton variant="text" animation="wave" width="70%" />
              </Box>
            ))
          : films.map((film) => (
              <Box key={film.id} sx={{ flex: `0 0 ${CARD_WIDTH}px`, scrollSnapAlign: 'start' }}>
                <FilmCard film={film} />
              </Box>
            ))}

        {!loading && films.length === 0 && (
          <Typography variant="body2" color="text.secondary" sx={{ py: 4 }}>
            —
          </Typography>
        )}
      </Box>
    </Box>
  );
});

FilmRow.displayName = 'FilmRow';

export default FilmRow;
