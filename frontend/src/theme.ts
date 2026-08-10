import { createTheme, type ThemeOptions } from '@mui/material/styles';

export const getTheme = (mode: 'light' | 'dark') => {
  const isDark = mode === 'dark';

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
        main: '#FF4757',
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
        paper: isDark ? '#141414' : '#ffffff',
      },
      text: {
        primary: isDark ? '#ffffff' : '#1B2559',
        secondary: isDark ? 'rgba(255, 255, 255, 0.7)' : '#A3AED0',
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
      h1: { fontWeight: 700, letterSpacing: '-0.5px' },
      h2: { fontWeight: 700, letterSpacing: '-0.5px' },
      h3: { fontWeight: 600 },
      h4: { fontWeight: 600 },
      body1: { letterSpacing: '0.15px' },
    },
    components: {
      MuiButton: {
        styleOverrides: {
          root: {
            textTransform: 'none',
            borderRadius: 8,
            fontWeight: 600,
            boxShadow: 'none',
            transition: 'all 0.3s ease',
            '&:hover': {
              boxShadow: isDark ? '0 8px 16px rgba(0, 168, 78, 0.2)' : '0 8px 16px rgba(0, 168, 78, 0.1)',
            },
          },
          contained: {
            '&:hover': {
              transform: 'translateY(-2px)',
            },
          },
        },
      },
      MuiCard: {
        styleOverrides: {
          root: {
            borderRadius: 12,
            backgroundColor: isDark ? '#1e1e1e' : '#ffffff',
            border: isDark ? '1px solid rgba(255, 255, 255, 0.08)' : '1px solid rgba(0, 0, 0, 0.05)',
            boxShadow: isDark ? 'none' : '0px 20px 27px 0px rgba(0, 0, 0, 0.05)',
          },
        },
      },
      MuiPaper: {
        styleOverrides: {
          root: {
            backgroundImage: 'none',
          },
        },
      },
      MuiAppBar: {
        styleOverrides: {
          root: {
            backgroundImage: 'none',
          },
        },
      },
      MuiDrawer: {
        styleOverrides: {
          paper: {
            backgroundImage: 'none',
          },
        },
      },
    },
  };

  return createTheme(themeOptions);
};

export default getTheme('dark');
