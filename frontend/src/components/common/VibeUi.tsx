import React from 'react';
import { Box, Typography } from '@mui/material';
import { alpha, useTheme, type SxProps, type Theme } from '@mui/material/styles';
import {
  VIBE_TEAL,
  countBadgeSx,
  eyebrowSx,
  heroSurfaceSx,
  iconTileSx,
  vibeSurface,
} from '../../styles/vibe';

/*
 * Page-level building blocks of the "vibe" look (the watch-together lobby/room style): an aurora
 * hero for page titles, gradient-tile section headers and a friendly empty state. Presentational
 * only - pages pass in their existing text, counts and actions.
 */

const toSxArray = (sx?: SxProps<Theme>) => (sx === undefined ? [] : Array.isArray(sx) ? sx : [sx]);

interface PageHeroProps {
  icon: React.ReactNode;
  title: React.ReactNode;
  subtitle?: React.ReactNode;
  eyebrow?: React.ReactNode;
  /** Right-hand side of the title row (search box, primary action...). */
  actions?: React.ReactNode;
  /** Rendered under the title row, inside the banner (tabs, filters...). */
  children?: React.ReactNode;
  sx?: SxProps<Theme>;
}

export const PageHero: React.FC<PageHeroProps> = ({ icon, title, subtitle, eyebrow, actions, children, sx }) => {
  const theme = useTheme();
  return (
    <Box sx={[heroSurfaceSx(theme), { p: { xs: 2.5, md: 3.5 }, mb: 3 }, ...toSxArray(sx)]}>
      <Box
        sx={{
          display: 'flex',
          flexWrap: 'wrap',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 2,
        }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: { xs: 1.5, md: 2 }, minWidth: 0 }}>
          <Box sx={{ ...iconTileSx(56), borderRadius: '18px', '& .MuiSvgIcon-root': { fontSize: 30 } }}>{icon}</Box>
          <Box sx={{ minWidth: 0 }}>
            {eyebrow && <Typography sx={{ ...eyebrowSx(theme), mb: 0.25 }}>{eyebrow}</Typography>}
            <Typography
              component="h1"
              sx={{
                fontWeight: 900,
                letterSpacing: '-0.035em',
                lineHeight: 1.08,
                fontSize: { xs: '1.75rem', md: '2.35rem' },
              }}
            >
              {title}
            </Typography>
            {subtitle && (
              <Typography component="div" variant="body2" color="text.secondary" sx={{ mt: 0.75, maxWidth: 640 }}>
                {subtitle}
              </Typography>
            )}
          </Box>
        </Box>
        {actions && <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>{actions}</Box>}
      </Box>
      {children && <Box sx={{ mt: 2.5 }}>{children}</Box>}
    </Box>
  );
};

interface SectionHeaderProps {
  title: React.ReactNode;
  icon?: React.ReactNode;
  count?: React.ReactNode;
  action?: React.ReactNode;
  sx?: SxProps<Theme>;
}

export const SectionHeader: React.FC<SectionHeaderProps> = ({ title, icon, count, action, sx }) => {
  const theme = useTheme();
  return (
    <Box sx={[{ display: 'flex', alignItems: 'center', gap: 1.25, mb: 2, minWidth: 0 }, ...toSxArray(sx)]}>
      {icon && (
        <Box sx={{ ...iconTileSx(34), borderRadius: '11px', boxShadow: 'none', '& .MuiSvgIcon-root': { fontSize: 19 } }}>
          {icon}
        </Box>
      )}
      <Typography variant="h6" component="h2" noWrap sx={{ fontWeight: 900, letterSpacing: '-0.02em', minWidth: 0 }}>
        {title}
      </Typography>
      {count !== undefined && count !== null && <Box component="span" sx={countBadgeSx(theme)}>{count}</Box>}
      <Box sx={{ flex: 1 }} />
      {action}
    </Box>
  );
};

interface EmptyStateProps {
  icon?: React.ReactNode;
  /** Used instead of `icon` for a playful, colourful mark. */
  emoji?: string;
  title: React.ReactNode;
  description?: React.ReactNode;
  action?: React.ReactNode;
  compact?: boolean;
  sx?: SxProps<Theme>;
}

export const EmptyState: React.FC<EmptyStateProps> = ({ icon, emoji, title, description, action, compact, sx }) => {
  const theme = useTheme();
  return (
    <Box
      sx={[
        {
          textAlign: 'center',
          py: compact ? 5 : 8,
          px: 3,
          borderRadius: '24px',
          border: '1.5px dashed',
          borderColor: alpha(VIBE_TEAL, theme.palette.mode === 'dark' ? 0.28 : 0.35),
          bgcolor: vibeSurface(theme),
          backgroundImage: `radial-gradient(circle at 50% 0%, ${alpha(VIBE_TEAL, 0.12)}, transparent 55%)`,
        },
        ...toSxArray(sx),
      ]}
    >
      <Box
        sx={{
          mx: 'auto',
          mb: 2,
          animation: 'vibeEmptyFloat 3.2s ease-in-out infinite',
          '@keyframes vibeEmptyFloat': {
            '0%, 100%': { transform: 'translateY(0)' },
            '50%': { transform: 'translateY(-6px)' },
          },
          '@media (prefers-reduced-motion: reduce)': { animation: 'none' },
          ...(emoji
            ? { fontSize: compact ? '2.4rem' : '3rem', lineHeight: 1 }
            : {
                ...iconTileSx(compact ? 60 : 72),
                borderRadius: '50%',
                '& .MuiSvgIcon-root': { fontSize: compact ? 30 : 36 },
              }),
        }}
        aria-hidden
      >
        {emoji ?? icon}
      </Box>
      <Typography variant="h6" sx={{ fontWeight: 900, mb: 0.75, letterSpacing: '-0.01em' }}>
        {title}
      </Typography>
      {description && (
        <Typography variant="body2" color="text.secondary" sx={{ maxWidth: 420, mx: 'auto' }}>
          {description}
        </Typography>
      )}
      {action && <Box sx={{ mt: 3 }}>{action}</Box>}
    </Box>
  );
};
