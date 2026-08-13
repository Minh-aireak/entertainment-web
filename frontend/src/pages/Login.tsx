import React, { useState } from 'react';
import { useNavigate, Link as RouterLink } from 'react-router-dom';
import {
  Box,
  Button,
  TextField,
  Typography,
  Paper,
  InputAdornment,
  IconButton,
  Link,
  alpha,
} from '@mui/material';
import { Visibility, VisibilityOff, Mail, Lock, Google } from '@mui/icons-material';
import { useDispatch } from 'react-redux';
import { toast } from 'react-hot-toast';
import { loginStart, loginSuccess, loginFailure } from '../store';
import { identityService } from '../api/identityService';
import { profileService } from '../api/profileService';
import AuthLayout from '../components/Layout/AuthLayout';
import LoginShowcase from '../components/Layout/LoginShowcase';
import { Divider } from '@mui/material';
import { useTranslation } from 'react-i18next';

const Login: React.FC = () => {
  const navigate = useNavigate();
  const dispatch = useDispatch();
  const { t } = useTranslation();

  const [showPassword, setShowPassword] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [formData, setFormData] = useState({
    username: '',
    password: '',
  });

  const GOOGLE_CLIENT_ID = '266132262326-islopun9fjajf96gds894ncmi43ugcku.apps.googleusercontent.com';
  const REDIRECT_URI = 'http://localhost:5173/authenticate';

  const handleGoogleLogin = () => {
    const googleAuthUrl = `https://accounts.google.com/o/oauth2/v2/auth?response_type=code&client_id=${GOOGLE_CLIENT_ID}&redirect_uri=${REDIRECT_URI}&scope=openid%20email%20profile`;
    window.location.href = googleAuthUrl;
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    dispatch(loginStart());
    try {
      const loginResponse = await identityService.login(formData);
      if (loginResponse.code !== 1000) {
        throw new Error(loginResponse.message || 'Login failed');
      }

      // Tokens are HttpOnly cookies, so hydrate the authenticated user from the API.
      const [profileResponse, myInfoResponse] = await Promise.all([
        profileService.getMyProfile(),
        identityService.getMyInfo(),
      ]);
      if (profileResponse.code !== 1000 || !profileResponse.result) {
        throw new Error(profileResponse.message || 'Unable to load user profile');
      }
      const profile = profileResponse.result;
      const roles = myInfoResponse.code === 1000 && myInfoResponse.result
        ? myInfoResponse.result.roles
        : [{ name: "USER", description: "Default user role" }];
      const user = {
        id: profile.userId,
        username: profile.username,
        email: profile.email,
        roles,
      };
      dispatch(loginSuccess({ user }));
      toast.success(t('loginSuccess'));
      navigate("/social");
    } catch (error: any) {
      const message = error.response?.data?.message || t('loginFailed');
      dispatch(loginFailure(message));
      toast.error(message);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <AuthLayout fullBleed>
      <Box
        sx={{
          flex: 1,
          display: 'flex',
          flexDirection: { xs: 'column', md: 'row' },
          width: '100%',
          position: 'relative',
          overflow: 'hidden',
          background:
            'radial-gradient(circle at 25% 20%, rgba(0,168,78,0.22) 0%, transparent 45%), ' +
            'radial-gradient(circle at 78% 78%, rgba(0,199,92,0.16) 0%, transparent 50%), ' +
            '#050b08',
        }}
      >
        <LoginShowcase />

        <Box
          sx={{
            flex: 1,
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            px: 2,
            py: 8,
            position: 'relative',
            zIndex: 1,
          }}
        >
        <Paper
          elevation={0}
          sx={{
            p: { xs: 4, sm: 6 },
            borderRadius: 4,
            bgcolor: alpha('#ffffff', 0.07),
            backdropFilter: 'blur(24px)',
            WebkitBackdropFilter: 'blur(24px)',
            border: '1px solid',
            borderColor: alpha('#ffffff', 0.14),
            boxShadow: '0 24px 48px rgba(0, 0, 0, 0.45)',
            width: '100%',
            maxWidth: '480px',
            animation: 'fadeIn 0.6s ease-out',
            '@keyframes fadeIn': {
              from: { opacity: 0, transform: 'translateY(20px)' },
              to: { opacity: 1, transform: 'translateY(0)' },
            },
            // Overrides index.css's theme-toggle-dependent autofill vars, which otherwise paint
            // browser-saved-credential inputs as an opaque block clashing with this glass card.
            '--autofill-bg': '#10201a',
            '--autofill-text': '#ffffff',
          }}
        >
          {/* Header */}
          <Box sx={{ mb: 5, textAlign: 'center' }}>
            <Typography
              variant="h4"
              sx={{
                fontWeight: 800,
                color: '#fff',
                mb: 1.5,
                letterSpacing: '-0.02em',
              }}
            >
              {t('welcomeBackTitle')}
            </Typography>
          </Box>

          {/* Form */}
          <Box component="form" onSubmit={handleSubmit} noValidate>
            <Box sx={{ mb: 3 }}>
              <TextField
                fullWidth
                id="username"
                name="username"
                type="text"
                label={t('username')}
                variant="outlined"
                placeholder="username"
                value={formData.username}
                onChange={handleChange}
                required
                slotProps={{
                  input: {
                    startAdornment: (
                      <InputAdornment position="start">
                        <Mail sx={{ color: alpha('#ffffff', 0.5), fontSize: 20 }} />
                      </InputAdornment>
                    ),
                  },
                }}
                sx={{
                  '& .MuiOutlinedInput-root': {
                    borderRadius: 2,
                    bgcolor: alpha('#ffffff', 0.06),
                    '& fieldset': { borderColor: alpha('#ffffff', 0.14) },
                    '&:hover fieldset': { borderColor: alpha('#ffffff', 0.28) },
                    '&.Mui-focused fieldset': { borderColor: '#00C75C' },
                  },
                  '& .MuiInputLabel-root': { color: alpha('#ffffff', 0.65) },
                  '& .MuiInputLabel-root.Mui-focused': { color: '#00C75C' },
                  '& .MuiOutlinedInput-input': { color: '#fff' },
                }}
              />
            </Box>

            <Box sx={{ mb: 1.5 }}>
              <TextField
                fullWidth
                id="password"
                name="password"
                type={showPassword ? 'text' : 'password'}
                label={t('password')}
                variant="outlined"
                placeholder="••••••••"
                value={formData.password}
                onChange={handleChange}
                required
                slotProps={{
                  input: {
                    startAdornment: (
                      <InputAdornment position="start">
                        <Lock sx={{ color: alpha('#ffffff', 0.5), fontSize: 20 }} />
                      </InputAdornment>
                    ),
                    endAdornment: (
                      <InputAdornment position="end">
                        <IconButton
                          onClick={() => setShowPassword(!showPassword)}
                          edge="end"
                          sx={{ color: alpha('#ffffff', 0.6) }}
                        >
                          {showPassword ? <VisibilityOff /> : <Visibility />}
                        </IconButton>
                      </InputAdornment>
                    ),
                  },
                }}
                sx={{
                  '& .MuiOutlinedInput-root': {
                    borderRadius: 2,
                    bgcolor: alpha('#ffffff', 0.06),
                    '& fieldset': { borderColor: alpha('#ffffff', 0.14) },
                    '&:hover fieldset': { borderColor: alpha('#ffffff', 0.28) },
                    '&.Mui-focused fieldset': { borderColor: '#00C75C' },
                  },
                  '& .MuiInputLabel-root': { color: alpha('#ffffff', 0.65) },
                  '& .MuiInputLabel-root.Mui-focused': { color: '#00C75C' },
                  '& .MuiOutlinedInput-input': { color: '#fff' },
                }}
              />
            </Box>

            <Box sx={{ textAlign: 'right', mb: 4 }}>
              <Link
                component={RouterLink}
                to="/forgot-password"
                sx={{
                  color: '#00A84E',
                  textDecoration: 'none',
                  fontSize: '0.875rem',
                  fontWeight: 600,
                  '&:hover': { textDecoration: 'underline' },
                }}
              >
                {t('forgotPassword')}
              </Link>
            </Box>

            <Button
              fullWidth
              type="submit"
              variant="contained"
              disabled={isLoading}
              sx={{
                py: 1.8,
                borderRadius: 2,
                bgcolor: '#00A84E',
                fontSize: '1rem',
                fontWeight: 700,
                textTransform: 'none',
                boxShadow: '0 8px 16px rgba(0, 168, 78, 0.25)',
                '&:hover': {
                  bgcolor: '#008F41',
                  boxShadow: '0 12px 24px rgba(0, 168, 78, 0.35)',
                },
              }}
            >
              {isLoading ? t('loggingIn') : t('login')}
            </Button>

            <Box sx={{ my: 3, display: 'flex', alignItems: 'center' }}>
              <Divider sx={{ flex: 1, borderColor: alpha('#ffffff', 0.15) }} />
              <Typography sx={{ px: 2, color: alpha('#ffffff', 0.5), fontSize: '0.875rem' }}>
                {t('or')}
              </Typography>
              <Divider sx={{ flex: 1, borderColor: alpha('#ffffff', 0.15) }} />
            </Box>

            <Button
              fullWidth
              variant="outlined"
              startIcon={<Google />}
              onClick={handleGoogleLogin}
              sx={{
                py: 1.5,
                borderRadius: 2,
                borderColor: alpha('#ffffff', 0.25),
                color: '#fff',
                fontSize: '0.95rem',
                fontWeight: 600,
                textTransform: 'none',
                '&:hover': {
                  borderColor: '#ffffff',
                  bgcolor: alpha('#ffffff', 0.08),
                },
              }}
            >
              {t('continueWithGoogle')}
            </Button>

            <Box sx={{ mt: 4, textAlign: 'center' }}>
              <Typography sx={{ color: alpha('#ffffff', 0.65), fontSize: '0.875rem' }}>
                {t('noAccount')}{' '}
                <Link
                  component={RouterLink}
                  to="/register"
                  sx={{
                    color: '#00A84E',
                    textDecoration: 'none',
                    fontWeight: 700,
                    ml: 0.5,
                    '&:hover': { textDecoration: 'underline' },
                  }}
                >
                  {t('registerNow')}
                </Link>
              </Typography>
            </Box>
          </Box>
        </Paper>

        <Box sx={{ mt: 4, display: 'flex', gap: 3, position: 'relative', zIndex: 1 }}>
          {(['terms', 'privacy', 'help'] as const).map((item) => (
            <Link
              key={item}
              href="#"
              sx={{
                color: alpha('#ffffff', 0.45),
                textDecoration: 'none',
                fontSize: '0.75rem',
                '&:hover': { color: alpha('#ffffff', 0.75) },
              }}
            >
              {t(item)}
            </Link>
          ))}
        </Box>
        </Box>
      </Box>
    </AuthLayout>
  );
};

export default Login;
