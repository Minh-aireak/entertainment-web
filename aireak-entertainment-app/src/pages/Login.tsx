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
} from '@mui/material';
import { Visibility, VisibilityOff, Mail, Lock, Google } from '@mui/icons-material';
import { useDispatch } from 'react-redux';
import { toast } from 'react-hot-toast';
import { loginStart, loginSuccess, loginFailure } from '../store';
import { identityService } from '../api/identityService';
import { profileService } from '../api/profileService';
import AuthLayout from '../components/Layout/AuthLayout';
import { Divider } from '@mui/material';

const Login: React.FC = () => {
  const navigate = useNavigate();
  const dispatch = useDispatch();

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
      toast.success("Đăng nhập thành công!");
      navigate("/social");
    } catch (error: any) {
      const message = error.response?.data?.message || "Đăng nhập thất bại";
      dispatch(loginFailure(message));
      toast.error(message);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <AuthLayout>
      <Box
        sx={{
          flex: 1,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          px: 2,
          py: 8,
          background: 'linear-gradient(180deg, rgba(0, 168, 78, 0.05) 0%, rgba(0, 0, 0, 0) 100%)',
        }}
      >
        <Paper
          elevation={0}
          sx={{
            p: { xs: 4, sm: 6 },
            borderRadius: 4,
            backgroundColor: '#141414',
            border: '1px solid rgba(255, 255, 255, 0.05)',
            boxShadow: '0 24px 48px rgba(0, 0, 0, 0.4)',
            width: '100%',
            maxWidth: '480px',
            animation: 'fadeIn 0.6s ease-out',
            '@keyframes fadeIn': {
              from: { opacity: 0, transform: 'translateY(20px)' },
              to: { opacity: 1, transform: 'translateY(0)' },
            },
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
              Chào mừng trở lại
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
                label="Username"
                variant="outlined"
                placeholder="username"
                value={formData.username}
                onChange={handleChange}
                required
                slotProps={{
                  input: {
                    startAdornment: (
                      <InputAdornment position="start">
                        <Mail sx={{ color: 'rgba(255, 255, 255, 0.3)', fontSize: 20 }} />
                      </InputAdornment>
                    ),
                  },
                }}
                sx={{
                  '& .MuiOutlinedInput-root': {
                    borderRadius: 2,
                    bgcolor: 'rgba(255, 255, 255, 0.03)',
                    '&:hover fieldset': { borderColor: 'rgba(255, 255, 255, 0.2)' },
                    '&.Mui-focused fieldset': { borderColor: '#00A84E' },
                  },
                  '& .MuiInputLabel-root': { color: 'rgba(255, 255, 255, 0.5)' },
                  '& .MuiInputLabel-root.Mui-focused': { color: '#00A84E' },
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
                label="Mật khẩu"
                variant="outlined"
                placeholder="••••••••"
                value={formData.password}
                onChange={handleChange}
                required
                slotProps={{
                  input: {
                    startAdornment: (
                      <InputAdornment position="start">
                        <Lock sx={{ color: 'rgba(255, 255, 255, 0.3)', fontSize: 20 }} />
                      </InputAdornment>
                    ),
                    endAdornment: (
                      <InputAdornment position="end">
                        <IconButton
                          onClick={() => setShowPassword(!showPassword)}
                          edge="end"
                          sx={{ color: 'rgba(255, 255, 255, 0.3)' }}
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
                    bgcolor: 'rgba(255, 255, 255, 0.03)',
                    '&:hover fieldset': { borderColor: 'rgba(255, 255, 255, 0.2)' },
                    '&.Mui-focused fieldset': { borderColor: '#00A84E' },
                  },
                  '& .MuiInputLabel-root': { color: 'rgba(255, 255, 255, 0.5)' },
                  '& .MuiInputLabel-root.Mui-focused': { color: '#00A84E' },
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
                Quên mật khẩu?
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
              {isLoading ? 'Đang đăng nhập...' : 'Đăng nhập'}
            </Button>

            <Box sx={{ my: 3, display: 'flex', alignItems: 'center' }}>
              <Divider sx={{ flex: 1, borderColor: 'rgba(255, 255, 255, 0.1)' }} />
              <Typography sx={{ px: 2, color: 'rgba(255, 255, 255, 0.3)', fontSize: '0.875rem' }}>
                HOẶC
              </Typography>
              <Divider sx={{ flex: 1, borderColor: 'rgba(255, 255, 255, 0.1)' }} />
            </Box>

            <Button
              fullWidth
              variant="outlined"
              startIcon={<Google />}
              onClick={handleGoogleLogin}
              sx={{
                py: 1.5,
                borderRadius: 2,
                borderColor: 'rgba(255, 255, 255, 0.2)',
                color: '#fff',
                fontSize: '0.95rem',
                fontWeight: 600,
                textTransform: 'none',
                '&:hover': {
                  borderColor: '#fff',
                  bgcolor: 'rgba(255, 255, 255, 0.05)',
                },
              }}
            >
              Tiếp tục với Google
            </Button>

            <Box sx={{ mt: 4, textAlign: 'center' }}>
              <Typography sx={{ color: 'rgba(255, 255, 255, 0.5)', fontSize: '0.875rem' }}>
                Chưa có tài khoản?{' '}
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
                  Đăng ký ngay
                </Link>
              </Typography>
            </Box>
          </Box>
        </Paper>

        <Box sx={{ mt: 4, display: 'flex', gap: 3 }}>
          {['Điều khoản', 'Bảo mật', 'Trợ giúp'].map((item) => (
            <Link
              key={item}
              href="#"
              sx={{
                color: 'rgba(255, 255, 255, 0.3)',
                textDecoration: 'none',
                fontSize: '0.75rem',
                '&:hover': { color: 'rgba(255, 255, 255, 0.5)' },
              }}
            >
              {item}
            </Link>
          ))}
        </Box>
      </Box>
    </AuthLayout>
  );
};

export default Login;
