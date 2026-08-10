import React, { useEffect, useRef, useState } from 'react';
import { alpha, Box, Button, Typography, Paper, useTheme } from '@mui/material';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { filmService } from '../../api/filmService';
import type { FilmCategory, FilmSummaryResponse } from '../../models';
import FilmBentoGrid from './FilmBentoGrid';
import { usePersistedState } from '../../hooks/usePersistedState';

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
        sx={{
          mb: 6,
          p: { xs: 2, md: 4 },
          borderRadius: 3,
          bgcolor: alpha(theme.palette.text.primary, 0.03),
          border: '1px solid',
          borderColor: alpha(theme.palette.text.primary, 0.06),
          transition: 'box-shadow 0.25s ease',
          '&:hover': {
            boxShadow: '0 8px 32px rgba(0,0,0,0.25)',
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
          <Typography
            variant="h5"
            sx={{ fontWeight: 800, letterSpacing: '-0.02em', flexShrink: 0 }}
          >
            {t(titleKey)}
          </Typography>

          <Button
            onClick={() => navigate(viewAllPath)}
            sx={{ color: 'primary.main', fontWeight: 600, flexShrink: 0 }}
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
