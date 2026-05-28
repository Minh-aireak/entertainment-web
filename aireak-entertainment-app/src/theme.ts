import { createTheme } from '@mui/material/styles';

const theme = createTheme({
  palette: {
    mode: 'dark',
    primary: {
      main: '#00A84E',
      light: '#00C853',
      dark: '#008F41',
      contrastText: '#ffffff',
    },
    secondary: {
      main: '#141414',
      light: '#1A1A1A',
      dark: '#0A0A0A',
      contrastText: '#ffffff',
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
      default: '#0F0F0F',
      paper: '#1A1A1A',
    },
    text: {
      primary: '#ffffff',
      secondary: 'rgba(255, 255, 255, 0.7)',
    },
    divider: 'rgba(255, 255, 255, 0.08)',
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
      '"Apple Color Emoji"',
      '"Segoe UI Emoji"',
      '"Segoe UI Symbol"',
    ].join(','),
    h1: {
      fontWeight: 700,
      letterSpacing: '-0.5px',
    },
    h2: {
      fontWeight: 700,
      letterSpacing: '-0.5px',
    },
    h3: {
      fontWeight: 600,
    },
    h4: {
      fontWeight: 600,
    },
    body1: {
      letterSpacing: '0.15px',
    },
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
            boxShadow: '0 8px 16px rgba(0, 168, 78, 0.2)',
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
          backgroundColor: '#1e1e1e',
          border: '1px solid rgba(255, 255, 255, 0.08)',
          boxShadow: '0 8px 32px rgba(0, 0, 0, 0.4)',
          transition: 'all 0.3s ease',
          '&:hover': {
            boxShadow: '0 12px 48px rgba(0, 0, 0, 0.5)',
            borderColor: 'rgba(255, 255, 255, 0.12)',
          },
        },
      },
    },
    MuiAppBar: {
      styleOverrides: {
        root: {
          backgroundColor: '#141414',
          borderBottom: '1px solid rgba(255, 255, 255, 0.06)',
        },
      },
    },
    MuiDrawer: {
      styleOverrides: {
        paper: {
          backgroundColor: '#141414',
          borderRight: '1px solid rgba(255, 255, 255, 0.06)',
        },
      },
    },
    MuiListItemButton: {
      styleOverrides: {
        root: {
          borderRadius: 8,
          margin: '4px 8px',
          '&:hover': {
            backgroundColor: 'rgba(0, 168, 78, 0.1)',
          },
          '&.Mui-selected': {
            backgroundColor: 'rgba(0, 168, 78, 0.2)',
            color: '#00A84E',
            '&:hover': {
              backgroundColor: 'rgba(0, 168, 78, 0.3)',
            },
          },
        },
      },
    },
    MuiTextField: {
      styleOverrides: {
        root: {
          '& .MuiOutlinedInput-root': {
            backgroundColor: 'rgba(255, 255, 255, 0.03)',
            '& fieldset': {
              borderColor: 'rgba(0, 168, 78, 0.3)',
            },
            '&:hover fieldset': {
              borderColor: 'rgba(0, 168, 78, 0.5)',
            },
            '&.Mui-focused fieldset': {
              borderColor: '#00A84E',
            },
          },
        },
      },
    },
    MuiChip: {
      styleOverrides: {
        root: {
          backgroundColor: 'rgba(0, 168, 78, 0.1)',
          border: '1px solid rgba(0, 168, 78, 0.3)',
        },
      },
    },
  },
});

export default theme;
