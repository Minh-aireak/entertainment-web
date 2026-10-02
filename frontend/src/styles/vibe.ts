import { alpha, type Theme } from '@mui/material/styles';

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
export const vibeSurface = (theme: Theme) =>
  theme.palette.mode === 'dark' ? alpha('#FFFFFF', 0.06) : alpha('#1B2559', 0.045);

export const vibeSurfaceHover = (theme: Theme) =>
  theme.palette.mode === 'dark' ? alpha('#FFFFFF', 0.1) : alpha('#1B2559', 0.08);

export const vibeBorder = (theme: Theme) =>
  theme.palette.mode === 'dark' ? alpha('#FFFFFF', 0.09) : alpha('#1B2559', 0.09);

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
