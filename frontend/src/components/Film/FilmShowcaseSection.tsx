import React, { useEffect, useRef, useState } from 'react';
import { alpha, Box, Button, Typography, Paper, useTheme } from '@mui/material';
import { ChevronRight } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { filmService } from '../../api/filmService';
import type { FilmCategory, FilmSummaryResponse } from '../../models';
import FilmBentoGrid from './FilmBentoGrid';
import { usePersistedState } from '../../hooks/usePersistedState';
import { VIBE_GRADIENT, VIBE_TEAL, pillSx, vibeBorder } from '../../styles/vibe';

interface FilmShowcaseSectionProps {
  titleKey: string;
  category: FilmCategory;
  viewAllPath: string;
}

const FilmShowcaseSection: React.FC<FilmShowcaseSectionProps> = React.memo(
  ({ titleKey, category, viewAllPath }) => {
    const { t } = useTranslation();
    const navigate = useNavigate();
    const theme = useTheme();
    const [films, setFilms] = usePersistedState<FilmSummaryResponse[]>(`filmShowcase:${category}`, []);
    const [loading, setLoading] = useState(films.length === 0);
    const skipInitialFetchRef = useRef(films.length > 0);

    useEffect(() => {
      if (skipInitialFetchRef.current) {
        skipInitialFetchRef.current = false;
        return;
      }
      let cancelled = false;
      setLoading(true);
      filmService
        .browseFilms({
          category,
          sortBy: 'LAST_UPDATE',
          sortDir: 'DESC',
          page: 1,
          size: 9,
        })
        .then((res) => {
          if (!cancelled) setFilms(res.result.data);
        })
        .catch((error) => {
          console.error(`Failed to load ${category} showcase:`, error);
          if (!cancelled) setFilms([]);
        })
        .finally(() => {
          if (!cancelled) setLoading(false);
        });
      return () => {
        cancelled = true;
      };
    }, [category, setFilms]);

    return (
      <Paper
        elevation={0}
        sx={{
          mb: 6,
          p: { xs: 2, md: 4 },
          borderRadius: '28px',
          bgcolor: alpha(theme.palette.text.primary, 0.03),
          backgroundImage: `radial-gradient(circle at 100% 0%, ${alpha(VIBE_TEAL, 0.08)}, transparent 40%)`,
          border: '1px solid',
          borderColor: vibeBorder(theme),
          transition: 'box-shadow 0.25s ease',
          '&:hover': {
            boxShadow: theme.palette.mode === 'dark' ? '0 12px 36px rgba(0,0,0,0.35)' : '0 12px 36px rgba(27,37,89,0.08)',
          },
        }}
      >
        <Box
          sx={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            mb: 3,
            flexWrap: 'wrap',
            gap: 2,
          }}
        >
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.25, flexShrink: 0 }}>
            <Box aria-hidden sx={{ width: 6, height: 26, borderRadius: 3, background: VIBE_GRADIENT }} />
            <Typography
              variant="h5"
              component="h2"
              sx={{ fontWeight: 900, letterSpacing: '-0.025em' }}
            >
              {t(titleKey)}
            </Typography>
          </Box>

          <Button
            onClick={() => navigate(viewAllPath)}
            endIcon={<ChevronRight sx={{ color: VIBE_TEAL }} />}
            sx={{ ...pillSx(theme, false), flexShrink: 0, py: 0.6 }}
          >
            {t('viewAll')}
          </Button>
        </Box>

        <FilmBentoGrid films={films} loading={loading} />
      </Paper>
    );
  }
);

FilmShowcaseSection.displayName = 'FilmShowcaseSection';

export default FilmShowcaseSection;
