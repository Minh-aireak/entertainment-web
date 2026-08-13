import React from 'react';
import { Link as RouterLink } from 'react-router-dom';
import {
  AppBar,
  Toolbar,
  Box,
  Link,
  Button
} from '@mui/material';
import { Movie } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import LanguageSwitcher from './LanguageSwitcher';

interface AuthLayoutProps {
  children: React.ReactNode;
  // Lets a page (e.g. the split-screen Login) stretch its content edge-to-edge
  // instead of being centered/padded, without changing the default layout
  // used by Register/ForgotPassword/ResetPassword.
  fullBleed?: boolean;
}

const AuthLayout: React.FC<AuthLayoutProps> = ({ children, fullBleed = false }) => {
  const { t } = useTranslation();

  return (
    <Box
      sx={{
        minHeight: '100vh',
        display: 'flex',
        flexDirection: 'column',
        backgroundColor: 'background.default',
        position: 'relative',
      }}
    >
      {/* Header */}
      <AppBar
        position="static"
        sx={{
          backgroundColor: 'background.paper',
          borderBottom: '1px solid',
          borderColor: 'divider',
          boxShadow: 'none',
        }}
      >
        <Toolbar
          sx={{
            justifyContent: 'space-between',
            px: { xs: 2, sm: 8 },
            py: 1.5,
          }}
        >
          <Link
            component={RouterLink}
            to="/login"
            sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 1.5,
              textDecoration: 'none',
              cursor: 'pointer',
            }}
          >
            <Movie sx={{ fontSize: 32, color: '#00A84E' }} />
            <Box
              sx={{
                fontSize: '1.4rem',
                fontWeight: 800,
                color: 'text.primary',
                textTransform: 'uppercase',
                letterSpacing: '0.02em',
              }}
            >
              AIREAK
            </Box>
          </Link>

          <Box
            sx={{
              display: 'flex',
              alignItems: 'center',
              gap: { xs: 0.5, md: 2 },
            }}
          >
            <LanguageSwitcher />
            <Button
              component={RouterLink}
              to="/login"
              sx={{
                display: { xs: 'none', md: 'inline-flex' },
                color: 'text.primary',
                textTransform: 'none',
                fontWeight: 600,
                '&:hover': { color: '#00A84E' },
              }}
            >
              {t('login')}
            </Button>
            <Button
              component={RouterLink}
              to="/register"
              variant="contained"
              sx={{
                display: { xs: 'none', sm: 'inline-flex' },
                bgcolor: '#00A84E',
                color: '#fff',
                textTransform: 'none',
                fontWeight: 600,
                borderRadius: '20px',
                px: 3,
                '&:hover': { bgcolor: '#008F41' },
              }}
            >
              {t('joinNow')}
            </Button>
          </Box>
        </Toolbar>
      </AppBar>

      {/* Main Content */}
      <Box
        sx={{
          flex: 1,
          display: 'flex',
          alignItems: fullBleed ? 'stretch' : 'center',
          justifyContent: fullBleed ? 'stretch' : 'center',
          p: fullBleed ? 0 : { xs: 2, sm: 4 },
        }}
      >
        {children}
      </Box>
    </Box>
  );
};

export default AuthLayout;


