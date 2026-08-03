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

interface AuthLayoutProps {
  children: React.ReactNode;
}

const AuthLayout: React.FC<AuthLayoutProps> = ({ children }) => {
  return (
    <Box
      sx={{
        minHeight: '100vh',
        display: 'flex',
        flexDirection: 'column',
        backgroundColor: '#0A0A0A',
        position: 'relative',
      }}
    >
      {/* Header */}
      <AppBar
        position="static"
        sx={{
          backgroundColor: '#0A0A0A',
          borderBottom: '1px solid #1A1A1A',
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
                color: '#ffffff',
                textTransform: 'uppercase',
                letterSpacing: '0.02em',
              }}
            >
              AIREAK
            </Box>
          </Link>

          <Box
            sx={{
              display: { xs: 'none', md: 'flex' },
              alignItems: 'center',
              gap: 2,
            }}
          >
            <Button
              component={RouterLink}
              to="/login"
              sx={{
                color: '#fff',
                textTransform: 'none',
                fontWeight: 600,
                '&:hover': { color: '#00A84E' },
              }}
            >
              Đăng nhập
            </Button>
            <Button
              component={RouterLink}
              to="/register"
              variant="contained"
              sx={{
                bgcolor: '#00A84E',
                color: '#fff',
                textTransform: 'none',
                fontWeight: 600,
                borderRadius: '20px',
                px: 3,
                '&:hover': { bgcolor: '#008F41' },
              }}
            >
              Tham gia ngay
            </Button>
          </Box>
        </Toolbar>
      </AppBar>

      {/* Main Content */}
      <Box
        sx={{
          flex: 1,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          p: { xs: 2, sm: 4 },
        }}
      >
        {children}
      </Box>
    </Box>
  );
};

export default AuthLayout;


