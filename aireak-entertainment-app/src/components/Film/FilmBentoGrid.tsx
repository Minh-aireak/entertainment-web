import React, { useEffect, useState } from 'react';
import { Box, Skeleton, Typography } from '@mui/material';
import type { FilmSummaryResponse } from '../../models';
import FilmCard from './FilmCard';
import FilmCardFeatured from './FilmCardFeatured';

interface FilmBentoGridProps {
  films: FilmSummaryResponse[];
  loading?: boolean;
}

const ROW_HEIGHT = { xs: '150px', sm: '170px', md: '190px', lg: '210px' };

// Ảnh rộng hơn cao ít nhất tỉ lệ này mới coi là "ngang" - poster phim đa phần là ảnh dọc nên
// ngưỡng chỉ cần lệch nhẹ khỏi hình vuông là đủ phân biệt.
const LANDSCAPE_RATIO_THRESHOLD = 1.15;

type Orientation = 'portrait' | 'landscape';

const FilmBentoGrid: React.FC<FilmBentoGridProps> = React.memo(({ films, loading }) => {
  // Không có sẵn kích thước ảnh từ API nên phải dò ngay trên trình duyệt (naturalWidth/Height) rồi
  // mới quyết định ô lưới hẹp-cao (ảnh dọc) hay rộng-thấp (ảnh ngang). Mặc định coi là "dọc" trong
  // lúc chờ dò xong vì đa số poster trong hệ thống là ảnh dọc, hạn chế lưới bị nhảy bố cục.
  const [orientations, setOrientations] = useState<Record<string, Orientation>>({});

  useEffect(() => {
    let cancelled = false;
    films.forEach((film) => {
      if (!film.thumbnailUrl || orientations[film.id]) return;
      const img = new Image();
      img.onload = () => {
        if (cancelled || img.naturalWidth === 0 || img.naturalHeight === 0) return;
        const orientation: Orientation =
          img.naturalWidth / img.naturalHeight >= LANDSCAPE_RATIO_THRESHOLD ? 'landscape' : 'portrait';
        setOrientations((prev) => (prev[film.id] ? prev : { ...prev, [film.id]: orientation }));
      };
      img.src = film.thumbnailUrl;
    });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [films]);

  if (loading) {
    return (
      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: { xs: 'repeat(2, 1fr)', md: 'repeat(4, 1fr)' },
          gridAutoRows: ROW_HEIGHT,
          gridAutoFlow: 'dense',
          gap: 2,
        }}
      >
        <Skeleton variant="rounded" sx={{ gridColumn: 'span 2', gridRow: 'span 2' }} />
        {Array.from(new Array(8)).map((_, index) => (
          <Skeleton key={index} variant="rounded" sx={{ width: '100%', height: '100%' }} />
        ))}
      </Box>
    );
  }

  if (films.length === 0) {
    return (
      <Typography variant="body2" color="text.secondary" sx={{ py: 4 }}>
        —
      </Typography>
    );
  }

  const [first, ...rest] = films;

  return (
    <Box
      sx={{
        display: 'grid',
        gridTemplateColumns: { xs: 'repeat(2, 1fr)', md: 'repeat(4, 1fr)' },
        gridAutoRows: ROW_HEIGHT,
        gridAutoFlow: 'dense',
        gap: 2,
      }}
    >
      <Box sx={{ gridColumn: 'span 2', gridRow: 'span 2' }}>
        <FilmCardFeatured film={first} />
      </Box>
      {rest.map((film) => {
        const orientation = orientations[film.id] ?? 'portrait';
        const span =
          orientation === 'landscape'
            ? { gridColumn: 'span 2', gridRow: 'span 2' }
            : { gridColumn: 'span 1', gridRow: 'span 2' };
        return (
          <Box key={film.id} sx={{ width: '100%', height: '100%', ...span }}>
            <FilmCard film={film} variant="fill" />
          </Box>
        );
      })}
    </Box>
  );
});

FilmBentoGrid.displayName = 'FilmBentoGrid';

export default FilmBentoGrid;
