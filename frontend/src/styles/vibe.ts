import { alpha, type PaletteMode, type Theme } from '@mui/material/styles';

/*
 * "Vibe" layer for the chat / watch-together / comment surfaces: the brand green pushed into a
 * green -> teal -> cyan gradient, pill-shaped inputs, big bubble radii and soft glows. Purely
 * presentational - every helper returns sx only, so screens keep their own logic untouched.
 */

export const VIBE_GRADIENT = 'linear-gradient(135deg, #00A84E 0%, #00C9A7 55%, #22C7F0 100%)';
export const VIBE_GRADIENT_HOVER = 'linear-gradient(135deg, #00B856 0%, #00D9B5 55%, #3AD3F7 100%)';
export const VIBE_TEAL = '#00C9A7';
export const VIBE_LOVE_GRADIENT = 'linear-gradient(135deg, #FF4D8D 0%, #FF7A59 100%)';
export const VIBE_GLOW = '0 8px 22px rgba(0, 201, 167, 0.35)';

/** Fill for idle tiles, "their" bubbles and input bars - reads as glass on both themes. */
export const vibeSurfaceFor = (mode: PaletteMode) =>
  mode === 'dark' ? alpha('#FFFFFF', 0.06) : alpha('#1B2559', 0.045);
export const vibeSurfaceHoverFor = (mode: PaletteMode) =>
  mode === 'dark' ? alpha('#FFFFFF', 0.1) : alpha('#1B2559', 0.08);
export const vibeBorderFor = (mode: PaletteMode) =>
  mode === 'dark' ? alpha('#FFFFFF', 0.09) : alpha('#1B2559', 0.09);
/** Teal for text/icons: the bright VIBE_TEAL on dark, a deeper teal that keeps contrast on white. */
export const vibeAccentTextFor = (mode: PaletteMode) => (mode === 'dark' ? '#3EE6C4' : '#00876A');

export const vibeSurface = (theme: Theme) => vibeSurfaceFor(theme.palette.mode);
export const vibeSurfaceHover = (theme: Theme) => vibeSurfaceHoverFor(theme.palette.mode);
export const vibeBorder = (theme: Theme) => vibeBorderFor(theme.palette.mode);
export const vibeAccentText = (theme: Theme) => vibeAccentTextFor(theme.palette.mode);

/** Gradient-filled text for hero headings and big numbers. */
export const gradientTextSx = {
  backgroundImage: VIBE_GRADIENT,
  WebkitBackgroundClip: 'text',
  backgroundClip: 'text',
  color: 'transparent',
} as const;

/** Glass card used for page sections: big radius, hairline border, soft lift on light mode. */
export const glassCardSx = (theme: Theme) => ({
  borderRadius: '24px',
  bgcolor: 'background.paper',
  border: '1px solid',
  borderColor: vibeBorder(theme),
  boxShadow: theme.palette.mode === 'dark' ? 'none' : '0 12px 32px rgba(27, 37, 89, 0.06)',
});

/** Square-ish gradient tile holding a white icon - the "app icon" accent used in section headers. */
export const iconTileSx = (size = 42) => ({
  width: size,
  height: size,
  borderRadius: `${Math.round(size / 3)}px`,
  display: 'grid',
  placeItems: 'center',
  background: VIBE_GRADIENT,
  boxShadow: VIBE_GLOW,
  color: '#fff',
  flexShrink: 0,
});

/** Chat / room message canvas: two soft colour blobs over a faint dot grid. */
export const chatCanvasSx = (theme: Theme) => ({
  backgroundColor: theme.palette.background.default,
  backgroundImage: [
    `radial-gradient(circle at 0% 0%, ${alpha('#00A84E', theme.palette.mode === 'dark' ? 0.12 : 0.08)}, transparent 42%)`,
    `radial-gradient(circle at 100% 100%, ${alpha('#22C7F0', theme.palette.mode === 'dark' ? 0.12 : 0.08)}, transparent 46%)`,
    `radial-gradient(${alpha(theme.palette.text.primary, theme.palette.mode === 'dark' ? 0.07 : 0.06)} 1px, transparent 1px)`,
  ].join(', '),
  backgroundSize: 'auto, auto, 18px 18px',
});

/** Message bubble. `tail` squares off the corner nearest the sender on the last bubble of a run. */
export const bubbleSx = (theme: Theme, mine: boolean, tail: boolean) => ({
  px: 1.75,
  py: 1,
  borderRadius: '20px',
  ...(tail ? (mine ? { borderBottomRightRadius: '6px' } : { borderBottomLeftRadius: '6px' }) : {}),
  background: mine ? VIBE_GRADIENT : vibeSurface(theme),
  color: mine ? '#fff' : theme.palette.text.primary,
  border: mine ? '1px solid transparent' : `1px solid ${vibeBorder(theme)}`,
  boxShadow: mine ? '0 6px 16px rgba(0, 168, 78, 0.22)' : 'none',
  wordBreak: 'break-word' as const,
});

/** Pill-shaped input bar; glows teal while anything inside it has focus. */
export const composerBarSx = (theme: Theme) => ({
  display: 'flex',
  alignItems: 'center',
  gap: 0.5,
  minHeight: 44,
  pl: 2,
  pr: 0.5,
  borderRadius: '999px',
  bgcolor: vibeSurface(theme),
  border: '1.5px solid',
  borderColor: vibeBorder(theme),
  transition: 'border-color 0.2s ease, box-shadow 0.2s ease, background-color 0.2s ease',
  '&:focus-within': {
    borderColor: alpha(VIBE_TEAL, 0.7),
    boxShadow: `0 0 0 4px ${alpha(VIBE_TEAL, 0.14)}`,
    bgcolor: theme.palette.background.paper,
  },
});

/** Round, secondary icon button that sits next to a composer bar (attach, emoji...). */
export const softIconButtonSx = (theme: Theme) => ({
  width: 40,
  height: 40,
  flexShrink: 0,
  color: 'text.secondary',
  bgcolor: vibeSurface(theme),
  border: '1px solid',
  borderColor: vibeBorder(theme),
  transition: 'transform 0.15s ease, color 0.15s ease, background-color 0.15s ease',
  '&:hover': { color: VIBE_TEAL, bgcolor: vibeSurfaceHover(theme), transform: 'translateY(-1px)' },
});

/** Small uppercase teal label that sits above a heading ("PHIM NỔI BẬT", "LIVE"...). */
export const eyebrowSx = (theme: Theme) => ({
  fontSize: '0.7rem',
  fontWeight: 900,
  letterSpacing: '0.14em',
  textTransform: 'uppercase' as const,
  color: vibeAccentText(theme),
});

/** Card hover: lift, teal hairline and a soft glow. Pair with a transition-free card. */
export const hoverLiftSx = (theme: Theme, lift = 4) => ({
  transition: 'transform 0.25s cubic-bezier(.2,.8,.2,1), box-shadow 0.25s ease, border-color 0.25s ease',
  '&:hover': {
    transform: `translateY(-${lift}px)`,
    borderColor: alpha(VIBE_TEAL, 0.45),
    boxShadow: theme.palette.mode === 'dark'
      ? '0 18px 38px rgba(0, 201, 167, 0.16)'
      : '0 18px 38px rgba(0, 168, 78, 0.14)',
  },
});

/** Aurora banner behind page heroes: green, cyan and violet blobs on the paper colour. */
export const heroSurfaceSx = (theme: Theme) => {
  const dark = theme.palette.mode === 'dark';
  return {
    position: 'relative' as const,
    overflow: 'hidden',
    borderRadius: '28px',
    border: '1px solid',
    borderColor: vibeBorder(theme),
    bgcolor: 'background.paper',
    backgroundImage: [
      `radial-gradient(circle at 0% 0%, ${alpha('#00A84E', dark ? 0.3 : 0.16)}, transparent 45%)`,
      `radial-gradient(circle at 78% 10%, ${alpha('#22C7F0', dark ? 0.2 : 0.14)}, transparent 40%)`,
      `radial-gradient(circle at 100% 100%, ${alpha('#7850FF', dark ? 0.24 : 0.1)}, transparent 50%)`,
    ].join(', '),
    boxShadow: dark ? 'none' : '0 12px 32px rgba(27, 37, 89, 0.06)',
  };
};

/** Glass track that holds a row of pill tabs (MUI Tabs with the indicator hidden). */
export const pillTabsTrackSx = (theme: Theme) => ({
  display: 'inline-flex',
  maxWidth: '100%',
  overflow: 'auto',
  p: 0.5,
  borderRadius: '999px',
  bgcolor: vibeSurface(theme),
  border: '1px solid',
  borderColor: vibeBorder(theme),
});

/** A Tab inside pillTabsTrackSx: muted when idle, gradient + glow when selected. */
export const pillTabSx = (theme: Theme) => ({
  textTransform: 'none' as const,
  fontWeight: 800,
  fontSize: '0.875rem',
  minHeight: 40,
  borderRadius: '999px',
  px: 2,
  py: 0.9,
  gap: 1,
  color: 'text.secondary',
  transition: 'color 0.2s ease, background-color 0.2s ease, box-shadow 0.2s ease',
  '&:hover:not(.Mui-selected)': { color: 'text.primary', bgcolor: vibeSurfaceHover(theme) },
  '&.Mui-selected': { color: '#fff', background: VIBE_GRADIENT, boxShadow: VIBE_GLOW },
});

/** Count bubble next to a tab label or section title; `onGradient` for use on a selected pill. */
export const countBadgeSx = (theme: Theme, onGradient = false, urgent = false) => ({
  px: 0.9,
  minWidth: 22,
  borderRadius: '999px',
  fontSize: '0.72rem',
  fontWeight: 900,
  lineHeight: 1.7,
  textAlign: 'center' as const,
  color: urgent || onGradient ? '#fff' : 'text.secondary',
  background: urgent ? VIBE_LOVE_GRADIENT : onGradient ? 'rgba(255,255,255,0.25)' : vibeSurfaceHover(theme),
});

/** Gradient ring around an avatar: wrap the Avatar in a Box with this sx. */
export const avatarRingSx = (theme: Theme, active = true, width = 2.5) => ({
  p: `${width}px`,
  borderRadius: '50%',
  display: 'inline-flex',
  flexShrink: 0,
  background: active ? VIBE_GRADIENT : vibeBorder(theme),
  '& .MuiAvatar-root': { border: '2px solid', borderColor: 'background.paper' },
});

/** Pill toggle/tab: gradient when active, glass otherwise. */
export const pillSx = (theme: Theme, active: boolean) => ({
  px: 1.75,
  py: 0.75,
  borderRadius: '999px',
  fontWeight: 800,
  fontSize: '0.82rem',
  whiteSpace: 'nowrap' as const,
  color: active ? '#fff' : theme.palette.text.primary,
  background: active ? VIBE_GRADIENT : vibeSurface(theme),
  border: '1px solid',
  borderColor: active ? 'transparent' : vibeBorder(theme),
  boxShadow: active ? VIBE_GLOW : 'none',
  transition: 'transform 0.15s ease, background-color 0.15s ease, box-shadow 0.2s ease',
  '&:hover': {
    transform: 'translateY(-1px)',
    ...(active ? {} : { bgcolor: vibeSurfaceHover(theme) }),
  },
});
