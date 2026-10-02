import React, { useMemo, useState } from 'react';
import { Box, ButtonBase, CircularProgress, InputBase, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { PlaylistPlay, Search, SwapVert } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import type { EpisodeResponse } from '../../models';
import {
  VIBE_GLOW,
  VIBE_GRADIENT,
  VIBE_TEAL,
  composerBarSx,
  pillSx,
  vibeBorder,
  vibeSurface,
  vibeSurfaceHover,
} from '../../styles/vibe';

interface EpisodePickerProps {
  episodes: EpisodeResponse[];
  currentEpisodeId?: string;
  onSelect: (episode: EpisodeResponse) => void;
  title: string;
  subtitle?: string;
  /** Read-only (a watch-together viewer): tiles show what's on but can't be picked. */
  disabled?: boolean;
  /** An episode switch is in flight - the tile that was just picked shows a spinner. */
  busy?: boolean;
  /** Caps the tile grid's height so it scrolls inside the card (the room page is tighter). */
  maxGridHeight?: number;
  emptyText?: string;
  formatTitle?: (title: string) => string;
}

const EQ_BAR_DELAYS = [0, 0.3, 0.15];
const keepTitle = (value: string) => value;

/** Tiny animated "now playing" equalizer. */
const NowPlayingBars: React.FC<{ color?: string; height?: number }> = ({ color = '#fff', height = 12 }) => (
  <Box sx={{ display: 'flex', alignItems: 'flex-end', gap: '2px', height }} aria-hidden>
    {EQ_BAR_DELAYS.map((delay) => (
      <Box
        key={delay}
        sx={{
          width: height > 10 ? 3 : 2,
          height: '100%',
          borderRadius: 2,
          bgcolor: color,
          transformOrigin: 'bottom',
          animation: `episodeEq 0.85s ease-in-out ${delay}s infinite alternate`,
          '@keyframes episodeEq': {
            '0%': { transform: 'scaleY(0.25)' },
            '100%': { transform: 'scaleY(1)' },
          },
          '@media (prefers-reduced-motion: reduce)': { animation: 'none', transform: 'scaleY(0.7)' },
        }}
      />
    ))}
  </Box>
);

const EpisodePicker: React.FC<EpisodePickerProps> = ({
  episodes,
  currentEpisodeId,
  onSelect,
  title,
  subtitle,
  disabled = false,
  busy = false,
  maxGridHeight,
  emptyText,
  formatTitle = keepTitle,
}) => {
  const { t } = useTranslation();
  const theme = useTheme();
  const [sortOrder, setSortOrder] = useState<'ASC' | 'DESC'>('ASC');
  const [query, setQuery] = useState('');
  const [pendingId, setPendingId] = useState<string | null>(null);
  // The season tab follows the episode being watched until the user picks another tab; a new
  // current episode resets it (adjusted during render rather than in an effect).
  const [seasonPick, setSeasonPick] = useState<{ episodeId?: string; season: number | null }>({
    episodeId: currentEpisodeId,
    season: null,
  });
  if (seasonPick.episodeId !== currentEpisodeId) {
    setSeasonPick({ episodeId: currentEpisodeId, season: null });
  }

  const currentEpisode = episodes.find((episode) => episode.id === currentEpisodeId);
  const seasons = useMemo(
    () => Array.from(new Set(episodes.map((episode) => episode.seasonNumber))).sort((a, b) => a - b),
    [episodes],
  );
  const preferredSeason = seasonPick.season ?? currentEpisode?.seasonNumber ?? seasons[0];
  const activeSeason = seasons.includes(preferredSeason) ? preferredSeason : seasons[0];

  const needle = query.trim().toLowerCase();
  const visibleEpisodes = episodes
    .filter((episode) => episode.seasonNumber === activeSeason)
    .filter((episode) => !needle
      || String(episode.episodeNumber).includes(needle)
      || formatTitle(episode.title ?? '').toLowerCase().includes(needle))
    .sort((a, b) => (sortOrder === 'ASC' ? a.episodeNumber - b.episodeNumber : b.episodeNumber - a.episodeNumber));

  const handlePick = (episode: EpisodeResponse) => {
    if (disabled || busy || episode.id === currentEpisodeId) return;
    setPendingId(episode.id);
    onSelect(episode);
  };

  return (
    <Box
      sx={{
        position: 'relative',
        overflow: 'hidden',
        borderRadius: '24px',
        p: { xs: 2, md: 2.5 },
        bgcolor: 'background.paper',
        border: '1px solid',
        borderColor: vibeBorder(theme),
      }}
    >
      <Box
        aria-hidden
        sx={{
          position: 'absolute',
          top: -90,
          right: -70,
          width: 240,
          height: 240,
          borderRadius: '50%',
          background: VIBE_GRADIENT,
          opacity: theme.palette.mode === 'dark' ? 0.16 : 0.1,
          filter: 'blur(48px)',
          pointerEvents: 'none',
        }}
      />

      {/* Header: title + quick find + sort */}
      <Box sx={{ position: 'relative', display: 'flex', alignItems: 'center', gap: 1.5, flexWrap: 'wrap', mb: 2 }}>
        <Box
          sx={{
            width: 42,
            height: 42,
            borderRadius: '14px',
            display: 'grid',
            placeItems: 'center',
            background: VIBE_GRADIENT,
            boxShadow: VIBE_GLOW,
            flexShrink: 0,
          }}
        >
          <PlaylistPlay sx={{ color: '#fff' }} />
        </Box>
        <Box sx={{ flex: 1, minWidth: 140 }}>
          <Typography sx={{ fontWeight: 900, fontSize: '1.1rem', letterSpacing: '-0.02em', lineHeight: 1.2 }}>
            {title}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            {subtitle ?? t('episodeCount', { count: episodes.length })}
          </Typography>
        </Box>

        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <Box sx={{ ...composerBarSx(theme), minHeight: 36, pl: 1.25, pr: 1.5, width: { xs: 150, sm: 180 } }}>
            <Search sx={{ fontSize: 18, color: 'text.secondary' }} />
            <InputBase
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder={t('episodeSearchPlaceholder')}
              inputProps={{ 'aria-label': t('episodeSearchPlaceholder') }}
              sx={{ flex: 1, fontSize: '0.85rem', '& input': { py: 0.5 } }}
            />
          </Box>
          <Tooltip title={sortOrder === 'ASC' ? t('switchToDescending') : t('switchToAscending')}>
            <ButtonBase
              onClick={() => setSortOrder((prev) => (prev === 'ASC' ? 'DESC' : 'ASC'))}
              sx={{ ...pillSx(theme, false), display: 'flex', gap: 0.5, py: 0.9 }}
            >
              <SwapVert sx={{ fontSize: 18, color: VIBE_TEAL }} />
              {sortOrder === 'ASC' ? '1 → 9' : '9 → 1'}
            </ButtonBase>
          </Tooltip>
        </Box>
      </Box>

      {/* Season tabs */}
      {seasons.length > 1 && (
        <Box
          sx={{
            position: 'relative',
            display: 'flex',
            gap: 1,
            mb: 2,
            overflowX: 'auto',
            pb: 0.5,
            scrollbarWidth: 'none',
            '&::-webkit-scrollbar': { display: 'none' },
          }}
        >
          {seasons.map((season) => (
            <ButtonBase
              key={season}
              onClick={() => setSeasonPick({ episodeId: currentEpisodeId, season })}
              sx={pillSx(theme, season === activeSeason)}
            >
              {t('seasonLabel', { season })}
            </ButtonBase>
          ))}
        </Box>
      )}

      {/* Now playing strip */}
      {currentEpisode && (
        <Box
          sx={{
            position: 'relative',
            display: 'flex',
            alignItems: 'center',
            gap: 1.25,
            mb: 2,
            px: 1.5,
            py: 1,
            borderRadius: '14px',
            bgcolor: alpha(VIBE_TEAL, theme.palette.mode === 'dark' ? 0.12 : 0.08),
            border: '1px solid',
            borderColor: alpha(VIBE_TEAL, 0.25),
          }}
        >
          <NowPlayingBars color={VIBE_TEAL} />
          <Typography variant="body2" noWrap sx={{ fontWeight: 700, minWidth: 0 }}>
            <Box component="span" sx={{ color: VIBE_TEAL, mr: 0.75 }}>{t('currentEpisode')}:</Box>
            {t('episodeLabel', { episode: currentEpisode.episodeNumber })}
            {currentEpisode.title ? ` · ${formatTitle(currentEpisode.title)}` : ''}
          </Typography>
        </Box>
      )}

      {/* Episode tiles */}
      {episodes.length === 0 ? (
        <Typography color="text.secondary" sx={{ textAlign: 'center', py: 4 }}>
          {emptyText ?? t('noEpisodesInSeason')}
        </Typography>
      ) : visibleEpisodes.length === 0 ? (
        <Typography color="text.secondary" sx={{ textAlign: 'center', py: 4 }}>
          {query.trim() ? t('noEpisodeMatches') : t('noEpisodesInSeason')}
        </Typography>
      ) : (
        <Box
          sx={{
            position: 'relative',
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fill, minmax(64px, 1fr))',
            gap: 1,
            p: 0.5,
            maxHeight: maxGridHeight,
            overflowY: maxGridHeight ? 'auto' : 'visible',
            '&::-webkit-scrollbar': { width: 6 },
            '&::-webkit-scrollbar-thumb': { bgcolor: vibeBorder(theme), borderRadius: 3 },
          }}
        >
          {visibleEpisodes.map((episode) => {
            const isActive = episode.id === currentEpisodeId;
            const isPending = busy && pendingId === episode.id;
            const label = t('episodeLabel', { episode: episode.episodeNumber });
            return (
              <Tooltip
                key={episode.id}
                title={episode.title ? `${label} · ${formatTitle(episode.title)}` : label}
                enterDelay={400}
              >
                <ButtonBase
                  onClick={() => handlePick(episode)}
                  aria-current={isActive ? 'true' : undefined}
                  aria-label={label}
                  disableRipple={disabled}
                  sx={{
                    position: 'relative',
                    height: 56,
                    borderRadius: '16px',
                    flexDirection: 'column',
                    gap: 0.25,
                    color: isActive ? '#fff' : 'text.primary',
                    background: isActive ? VIBE_GRADIENT : vibeSurface(theme),
                    border: '1px solid',
                    borderColor: isActive ? 'transparent' : vibeBorder(theme),
                    boxShadow: isActive ? VIBE_GLOW : 'none',
                    opacity: disabled && !isActive ? 0.55 : 1,
                    cursor: disabled || isActive ? 'default' : 'pointer',
                    transition: 'transform 0.18s cubic-bezier(.2,.8,.2,1), border-color 0.15s ease, background-color 0.15s ease, color 0.15s ease',
                    ...(!disabled && !isActive && {
                      '&:hover': {
                        transform: 'translateY(-3px)',
                        borderColor: alpha(VIBE_TEAL, 0.7),
                        color: VIBE_TEAL,
                        bgcolor: vibeSurfaceHover(theme),
                      },
                    }),
                  }}
                >
                  <Typography component="span" sx={{ fontWeight: 900, fontSize: '1.05rem', lineHeight: 1 }}>
                    {episode.episodeNumber}
                  </Typography>
                  <Typography component="span" sx={{ fontSize: '0.58rem', fontWeight: 800, letterSpacing: '0.14em', opacity: 0.7 }}>
                    {t('episodeShort')}
                  </Typography>
                  {isActive && (
                    <Box sx={{ position: 'absolute', top: 6, right: 7 }}>
                      <NowPlayingBars height={9} />
                    </Box>
                  )}
                  {isPending && (
                    <Box
                      sx={{
                        position: 'absolute',
                        inset: 0,
                        borderRadius: 'inherit',
                        display: 'grid',
                        placeItems: 'center',
                        bgcolor: alpha(theme.palette.background.paper, 0.7),
                      }}
                    >
                      <CircularProgress size={20} sx={{ color: VIBE_TEAL }} />
                    </Box>
                  )}
                </ButtonBase>
              </Tooltip>
            );
          })}
        </Box>
      )}
    </Box>
  );
};

export default EpisodePicker;
