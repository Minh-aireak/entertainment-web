import { alpha, createTheme, type ThemeOptions } from '@mui/material/styles';
import {
  VIBE_GLOW,
  VIBE_GRADIENT,
  VIBE_GRADIENT_HOVER,
  VIBE_TEAL,
  vibeAccentTextFor,
  vibeBorderFor,
  vibeSurfaceFor,
  vibeSurfaceHoverFor,
} from './styles/vibe';

/*
 * App-wide "vibe" look (the style first built for the watch-together room): gradient primary
 * actions, glass inputs that glow teal on focus, big-radius cards/dialogs/menus, gradient tab
 * indicators and pill chips. Radii are set per component on purpose - shape.borderRadius stays at
 * MUI's default because sx props all over the app multiply it (borderRadius: 3 => 3 * base).
 */
export const getTheme = (mode: 'light' | 'dark') => {
  const isDark = mode === 'dark';
  const surface = vibeSurfaceFor(mode);
  const surfaceHover = vibeSurfaceHoverFor(mode);
  const border = vibeBorderFor(mode);
  const accentText = vibeAccentTextFor(mode);
  const paper = isDark ? '#141414' : '#ffffff';
  const errorMain = '#FF4757';
  const popupShadow = isDark ? '0 18px 44px rgba(0, 0, 0, 0.55)' : '0 18px 44px rgba(27, 37, 89, 0.14)';

  const themeOptions: ThemeOptions = {
    palette: {
      mode,
      primary: {
        main: '#00A84E',
        light: '#00C853',
        dark: '#008F41',
        contrastText: '#ffffff',
      },
      secondary: {
        main: isDark ? '#141414' : '#f5f5f5',
        light: isDark ? '#1A1A1A' : '#ffffff',
        dark: isDark ? '#0A0A0A' : '#e0e0e0',
        contrastText: isDark ? '#ffffff' : '#000000',
      },
      error: {
        main: errorMain,
        light: '#FF6A77',
        dark: '#CC383F',
      },
      success: {
        main: '#00A84E',
        light: '#00C853',
        dark: '#008F41',
      },
      warning: {
        main: '#f39c12',
        light: '#f1c40f',
        dark: '#e67e22',
      },
      background: {
        default: isDark ? '#0A0A0A' : '#F4F7FE',
        paper,
      },
      text: {
        primary: isDark ? '#ffffff' : '#1B2559',
        secondary: isDark ? 'rgba(255, 255, 255, 0.7)' : '#7C88B1',
      },
      divider: isDark ? 'rgba(255, 255, 255, 0.08)' : 'rgba(0, 0, 0, 0.08)',
    },
    typography: {
      fontFamily: [
        'Inter',
        '-apple-system',
        'BlinkMacSystemFont',
        '"Segoe UI"',
        'Roboto',
        '"Helvetica Neue"',
        'Arial',
        'sans-serif',
      ].join(','),
      h1: { fontWeight: 900, letterSpacing: '-0.03em' },
      h2: { fontWeight: 900, letterSpacing: '-0.03em' },
      h3: { fontWeight: 800, letterSpacing: '-0.025em' },
      h4: { fontWeight: 800, letterSpacing: '-0.02em' },
      h5: { fontWeight: 800, letterSpacing: '-0.015em' },
      h6: { fontWeight: 800, letterSpacing: '-0.01em' },
      subtitle1: { fontWeight: 700 },
      subtitle2: { fontWeight: 700 },
      button: { fontWeight: 700 },
      body1: { letterSpacing: '0.15px' },
    },
    components: {
      MuiCssBaseline: {
        styleOverrides: {
          '::selection': { backgroundColor: alpha(VIBE_TEAL, 0.35) },
          '*': { scrollbarWidth: 'thin', scrollbarColor: `${alpha(VIBE_TEAL, 0.35)} transparent` },
          '*::-webkit-scrollbar': { width: 8, height: 8 },
          '*::-webkit-scrollbar-track': { background: 'transparent' },
          '*::-webkit-scrollbar-thumb': { backgroundColor: alpha(VIBE_TEAL, 0.35), borderRadius: 8 },
        },
      },
      MuiButton: {
        defaultProps: { disableElevation: true },
        styleOverrides: {
          root: {
            textTransform: 'none',
            borderRadius: 12,
            fontWeight: 700,
            letterSpacing: '0.01em',
            transition: 'transform 0.18s cubic-bezier(.2,.8,.2,1), box-shadow 0.2s ease, background-color 0.2s ease, border-color 0.2s ease, color 0.2s ease',
            '&:active': { transform: 'scale(0.98)' },
          },
          sizeLarge: { borderRadius: 14, padding: '10px 22px' },
        },
        variants: [
          {
            props: { variant: 'contained', color: 'primary' },
            style: {
              backgroundImage: VIBE_GRADIENT,
              color: '#fff',
              boxShadow: '0 6px 18px rgba(0, 201, 167, 0.28)',
              '&:hover': {
                backgroundImage: VIBE_GRADIENT_HOVER,
                boxShadow: VIBE_GLOW,
                transform: 'translateY(-1px)',
              },
              '&.Mui-disabled': { backgroundImage: 'none', boxShadow: 'none' },
            },
          },
          {
            props: { variant: 'outlined', color: 'primary' },
            style: {
              borderColor: alpha(VIBE_TEAL, 0.45),
              '&:hover': { borderColor: VIBE_TEAL, backgroundColor: alpha(VIBE_TEAL, 0.08) },
            },
          },
          {
            props: { variant: 'text', color: 'primary' },
            style: { '&:hover': { backgroundColor: alpha(VIBE_TEAL, 0.08) } },
          },
        ],
      },
      MuiFab: {
        styleOverrides: {
          primary: {
            backgroundImage: VIBE_GRADIENT,
            boxShadow: VIBE_GLOW,
            '&:hover': { backgroundImage: VIBE_GRADIENT_HOVER },
          },
        },
      },
      MuiIconButton: {
        styleOverrides: {
          root: { transition: 'transform 0.15s ease, background-color 0.15s ease, color 0.15s ease' },
        },
      },
      MuiCard: {
        styleOverrides: {
          root: {
            borderRadius: 20,
            backgroundColor: paper,
            backgroundImage: 'none',
            border: `1px solid ${border}`,
            boxShadow: isDark ? 'none' : '0 12px 32px rgba(27, 37, 89, 0.06)',
          },
        },
      },
      MuiPaper: {
        styleOverrides: {
          root: { backgroundImage: 'none' },
        },
      },
      MuiAppBar: {
        styleOverrides: {
          root: { backgroundImage: 'none' },
        },
      },
      MuiDrawer: {
        styleOverrides: {
          paper: { backgroundImage: 'none' },
        },
      },
      MuiDialog: {
        styleOverrides: {
          paper: {
            borderRadius: 24,
            border: `1px solid ${border}`,
            backgroundImage: 'none',
            boxShadow: popupShadow,
          },
        },
      },
      MuiDialogTitle: {
        styleOverrides: {
          root: { fontWeight: 900, letterSpacing: '-0.02em' },
        },
      },
      MuiPopover: {
        styleOverrides: {
          paper: { borderRadius: 16, border: `1px solid ${border}`, boxShadow: popupShadow },
        },
      },
      MuiMenu: {
        styleOverrides: {
          paper: { borderRadius: 16, border: `1px solid ${border}`, boxShadow: popupShadow },
        },
      },
      MuiMenuItem: {
        styleOverrides: {
          root: {
            borderRadius: 10,
            margin: '2px 6px',
            '&:hover': { backgroundColor: alpha(VIBE_TEAL, 0.08) },
            '&.Mui-selected': { backgroundColor: alpha(VIBE_TEAL, 0.14) },
            '&.Mui-selected:hover': { backgroundColor: alpha(VIBE_TEAL, 0.2) },
          },
        },
      },
      MuiAutocomplete: {
        styleOverrides: {
          paper: { borderRadius: 16, border: `1px solid ${border}`, boxShadow: popupShadow },
          option: { borderRadius: 10, margin: '2px 6px' },
        },
      },
      MuiOutlinedInput: {
        styleOverrides: {
          root: {
            borderRadius: 14,
            backgroundColor: surface,
            transition: 'box-shadow 0.2s ease, background-color 0.2s ease',
            '& .MuiOutlinedInput-notchedOutline': { borderColor: border },
            '&:hover .MuiOutlinedInput-notchedOutline': { borderColor: alpha(VIBE_TEAL, 0.5) },
            '&.Mui-focused:not(.Mui-error)': {
              backgroundColor: paper,
              boxShadow: `0 0 0 4px ${alpha(VIBE_TEAL, 0.14)}`,
            },
            '&.Mui-focused:not(.Mui-error) .MuiOutlinedInput-notchedOutline': {
              borderColor: VIBE_TEAL,
              borderWidth: 1.5,
            },
            '&.Mui-error.Mui-focused': { boxShadow: `0 0 0 4px ${alpha(errorMain, 0.14)}` },
          },
        },
      },
      MuiInputLabel: {
        styleOverrides: {
          root: { '&.Mui-focused:not(.Mui-error)': { color: accentText } },
        },
      },
      MuiChip: {
        styleOverrides: {
          root: { fontWeight: 700, borderRadius: 999 },
        },
        variants: [
          { props: { variant: 'filled', color: 'primary' }, style: { backgroundImage: VIBE_GRADIENT, color: '#fff' } },
          { props: { variant: 'outlined' }, style: { borderColor: border } },
        ],
      },
      MuiTabs: {
        styleOverrides: {
          indicator: { height: 3, borderRadius: 3, backgroundImage: VIBE_GRADIENT },
        },
      },
      MuiTab: {
        styleOverrides: {
          root: {
            textTransform: 'none',
            fontWeight: 800,
            fontSize: '0.95rem',
            '&.Mui-selected': { color: accentText },
          },
        },
      },
      MuiTooltip: {
        styleOverrides: {
          tooltip: {
            borderRadius: 10,
            padding: '6px 10px',
            fontSize: '0.75rem',
            fontWeight: 600,
            backgroundColor: isDark ? '#1E2626' : '#1B2559',
            border: `1px solid ${alpha('#FFFFFF', 0.08)}`,
          },
          arrow: { color: isDark ? '#1E2626' : '#1B2559' },
        },
      },
      MuiAlert: {
        styleOverrides: {
          root: { borderRadius: 16 },
        },
      },
      MuiLinearProgress: {
        styleOverrides: {
          root: { borderRadius: 999, backgroundColor: surface },
          bar: { borderRadius: 999, backgroundImage: VIBE_GRADIENT },
        },
      },
      MuiPaginationItem: {
        styleOverrides: {
          root: {
            borderRadius: 12,
            fontWeight: 700,
            '&.Mui-selected': {
              backgroundImage: VIBE_GRADIENT,
              color: '#fff',
              border: 'none',
              boxShadow: '0 6px 16px rgba(0, 201, 167, 0.3)',
            },
          },
        },
      },
      MuiToggleButton: {
        styleOverrides: {
          root: {
            textTransform: 'none',
            fontWeight: 700,
            borderRadius: 12,
            borderColor: border,
            '&.Mui-selected, &.Mui-selected:hover': { backgroundImage: VIBE_GRADIENT, color: '#fff' },
          },
        },
      },
      MuiSwitch: {
        styleOverrides: {
          switchBase: {
            '&.Mui-checked + .MuiSwitch-track': { backgroundImage: VIBE_GRADIENT, opacity: 1 },
          },
        },
      },
      MuiListItemButton: {
        styleOverrides: {
          root: {
            '&.Mui-selected': { backgroundColor: alpha(VIBE_TEAL, 0.12) },
            '&.Mui-selected:hover': { backgroundColor: alpha(VIBE_TEAL, 0.18) },
            '&:hover': { backgroundColor: surfaceHover },
          },
        },
      },
      MuiBadge: {
        styleOverrides: {
          colorPrimary: { backgroundImage: VIBE_GRADIENT },
        },
      },
    },
  };

  return createTheme(themeOptions);
};

export default getTheme('dark');
