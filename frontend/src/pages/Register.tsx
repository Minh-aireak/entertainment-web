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
  Grid,
  alpha,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Visibility, VisibilityOff, Mail, Lock, Person } from '@mui/icons-material';
import { toast } from 'react-hot-toast';
import { identityService } from '../api/identityService';
import AuthLayout from '../components/Layout/AuthLayout';
import { useTranslation } from 'react-i18next';

const Register: React.FC = () => {
  const navigate = useNavigate();
  const theme = useTheme();
  const { t } = useTranslation();

  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [formData, setFormData] = useState({
    username: '',
    email: '',
    password: '',
    confirmPassword: '',
  });

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (formData.password !== formData.confirmPassword) {
      toast.error(t('passwordsDoNotMatch'));
      return;
    }
    setIsLoading(true);
    try {
      await identityService.registration({
        username: formData.username,
        email: formData.email,
        password: formData.password,
      });
      toast.success(t('registerSuccess'));
      navigate('/login');
    } catch (error: any) {
      const message = error.response?.data?.message || t('registerFailed');
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
          py: 6,
          background: 'linear-gradient(180deg, rgba(0, 168, 78, 0.05) 0%, rgba(0, 0, 0, 0) 100%)',
        }}
      >
        <Paper
          elevation={0}
          sx={{
            p: { xs: 4, sm: 6 },
            borderRadius: 4,
            backgroundColor: 'background.paper',
            border: '1px solid',
            borderColor: 'divider',
            boxShadow: theme.palette.mode === 'dark' ? '0 24px 48px rgba(0, 0, 0, 0.4)' : '0 24px 48px rgba(0, 0, 0, 0.12)',
            width: '100%',
            maxWidth: '520px',
            animation: 'fadeIn 0.6s ease-out',
            '@keyframes fadeIn': {
              from: { opacity: 0, transform: 'translateY(20px)' },
              to: { opacity: 1, transform: 'translateY(0)' },
            },
          }}
        >
          {/* Header */}
          <Box sx={{ mb: 4, textAlign: 'center' }}>
            <Typography
              variant="h4"
              sx={{
                fontWeight: 800,
                color: 'text.primary',
                mb: 1.5,
                letterSpacing: '-0.02em',
              }}
            >
              {t('createAccount')}
            </Typography>
          </Box>

          {/* Form */}
          <Box component="form" onSubmit={handleSubmit} noValidate>
            <Box sx={{ mb: 2.5 }}>
              <TextField
                fullWidth
                id="username"
                name="username"
                label={t('username')}
                variant="outlined"
                placeholder="your_username"
                value={formData.username}
                onChange={handleChange}
                required
                slotProps={{
                  input: {
                    startAdornment: (
                      <InputAdornment position="start">
                        <Person sx={{ color: 'text.disabled', fontSize: 20 }} />
                      </InputAdornment>
                    ),
                  },
                }}
                sx={{
                  '& .MuiOutlinedInput-root': {
                    borderRadius: 2,
                    bgcolor: alpha(theme.palette.text.primary, 0.03),
                    '&:hover fieldset': { borderColor: alpha(theme.palette.text.primary, 0.2) },
                    '&.Mui-focused fieldset': { borderColor: '#00A84E' },
                  },
                  '& .MuiInputLabel-root': { color: 'text.secondary' },
                  '& .MuiInputLabel-root.Mui-focused': { color: '#00A84E' },
                  '& .MuiOutlinedInput-input': { color: 'text.primary' },
                }}
              />
            </Box>

            <Box sx={{ mb: 2.5 }}>
              <TextField
                fullWidth
                id="email"
                name="email"
                type="email"
                label={t('email')}
                variant="outlined"
                placeholder="email@example.com"
                value={formData.email}
                onChange={handleChange}
                required
                slotProps={{
                  input: {
                    startAdornment: (
                      <InputAdornment position="start">
                        <Mail sx={{ color: 'text.disabled', fontSize: 20 }} />
                      </InputAdornment>
                    ),
                  },
                }}
                sx={{
                  '& .MuiOutlinedInput-root': {
                    borderRadius: 2,
                    bgcolor: alpha(theme.palette.text.primary, 0.03),
                    '&:hover fieldset': { borderColor: alpha(theme.palette.text.primary, 0.2) },
                    '&.Mui-focused fieldset': { borderColor: '#00A84E' },
                  },
                  '& .MuiInputLabel-root': { color: 'text.secondary' },
                  '& .MuiInputLabel-root.Mui-focused': { color: '#00A84E' },
                  '& .MuiOutlinedInput-input': { color: 'text.primary' },
                }}
              />
            </Box>

            <Grid container spacing={2} sx={{ mb: 4 }}>
              <Grid size={{ xs: 12, sm: 6 }}>
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
                          <Lock sx={{ color: 'text.disabled', fontSize: 20 }} />
                        </InputAdornment>
                      ),
                      endAdornment: (
                        <InputAdornment position="end">
                          <IconButton
                            onClick={() => setShowPassword(!showPassword)}
                            edge="end"
                            sx={{ color: 'text.disabled' }}
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
                      bgcolor: alpha(theme.palette.text.primary, 0.03),
                      '&:hover fieldset': { borderColor: alpha(theme.palette.text.primary, 0.2) },
                      '&.Mui-focused fieldset': { borderColor: '#00A84E' },
                    },
                    '& .MuiInputLabel-root': { color: 'text.secondary' },
                    '& .MuiInputLabel-root.Mui-focused': { color: '#00A84E' },
                    '& .MuiOutlinedInput-input': { color: 'text.primary' },
                  }}
                />
              </Grid>
              <Grid size={{ xs: 12, sm: 6 }}>
                <TextField
                  fullWidth
                  id="confirmPassword"
                  name="confirmPassword"
                  type={showConfirmPassword ? 'text' : 'password'}
                  label={t('confirmPassword')}
                  variant="outlined"
                  placeholder="••••••••"
                  value={formData.confirmPassword}
                  onChange={handleChange}
                  required
                  slotProps={{
                    input: {
                      startAdornment: (
                        <InputAdornment position="start">
                          <Lock sx={{ color: 'text.disabled', fontSize: 20 }} />
                        </InputAdornment>
                      ),
                      endAdornment: (
                        <InputAdornment position="end">
                          <IconButton
                            onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                            edge="end"
                            sx={{ color: 'text.disabled' }}
                          >
                            {showConfirmPassword ? <VisibilityOff /> : <Visibility />}
                          </IconButton>
                        </InputAdornment>
                      ),
                    },
                  }}
                  sx={{
                    '& .MuiOutlinedInput-root': {
                      borderRadius: 2,
                      bgcolor: alpha(theme.palette.text.primary, 0.03),
                      '&:hover fieldset': { borderColor: alpha(theme.palette.text.primary, 0.2) },
                      '&.Mui-focused fieldset': { borderColor: '#00A84E' },
                    },
                    '& .MuiInputLabel-root': { color: 'text.secondary' },
                    '& .MuiInputLabel-root.Mui-focused': { color: '#00A84E' },
                    '& .MuiOutlinedInput-input': { color: 'text.primary' },
                  }}
                />
              </Grid>
            </Grid>

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
              {isLoading ? t('registering') : t('registerAccount')}
            </Button>

            <Box sx={{ mt: 4, textAlign: 'center' }}>
              <Typography sx={{ color: 'text.secondary', fontSize: '0.875rem' }}>
                {t('alreadyHaveAccount')}{' '}
                <Link
                  component={RouterLink}
                  to="/login"
                  sx={{
                    color: '#00A84E',
                    textDecoration: 'none',
                    fontWeight: 700,
                    ml: 0.5,
                    '&:hover': { textDecoration: 'underline' },
                  }}
                >
                  {t('login')}
                </Link>
              </Typography>
            </Box>
          </Box>
        </Paper>
      </Box>
    </AuthLayout>
  );
};

export default Register;
