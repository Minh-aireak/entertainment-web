import React, { useState, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import {
  Box,
  Button,
  TextField,
  Typography,
  Paper,
  InputAdornment,
  IconButton,
  alpha,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Lock, Visibility, VisibilityOff } from '@mui/icons-material';
import { toast } from 'react-hot-toast';
import { identityService } from '../api/identityService';
import AuthLayout from '../components/Layout/AuthLayout';
import { useTranslation } from 'react-i18next';

const ResetPassword: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const theme = useTheme();
  const { t } = useTranslation();
  const [isLoading, setIsLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [token, setToken] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  useEffect(() => {
    const params = new URLSearchParams(location.search);
    const tokenParam = params.get('token');
    if (!tokenParam) {
      toast.error(t('invalidResetToken'));
      navigate('/login');
    } else {
      setToken(tokenParam);
    }
  }, [location, navigate, t]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (password !== confirmPassword) {
      toast.error(t('confirmPasswordMismatch'));
      return;
    }

    if (password.length < 8) {
      toast.error(t('passwordMinLength'));
      return;
    }

    setIsLoading(true);
    try {
      await identityService.resetPassword({ token, password });
      toast.success(t('resetPasswordSuccess'));
      navigate('/login');
    } catch (error: any) {
      const message = error.response?.data?.message || t('resetPasswordFailed');
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
            backgroundColor: 'background.paper',
            border: '1px solid',
            borderColor: 'divider',
            boxShadow: theme.palette.mode === 'dark' ? '0 24px 48px rgba(0, 0, 0, 0.4)' : '0 24px 48px rgba(0, 0, 0, 0.12)',
            width: '100%',
            maxWidth: '480px',
          }}
        >
          <Box sx={{ mb: 5, textAlign: 'center' }}>
            <Typography variant="h4" sx={{ fontWeight: 800, color: 'text.primary', mb: 1.5 }}>
              {t('resetPassword')}
            </Typography>
            <Typography sx={{ color: 'text.secondary' }}>
              {t('resetPasswordDescription')}
            </Typography>
          </Box>

          <Box component="form" onSubmit={handleSubmit} noValidate>
            <Box sx={{ mb: 3 }}>
              <TextField
                fullWidth
                id="password"
                name="password"
                type={showPassword ? 'text' : 'password'}
                label={t('newPassword')}
                variant="outlined"
                placeholder="••••••••"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
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
            </Box>

            <Box sx={{ mb: 4 }}>
              <TextField
                fullWidth
                id="confirmPassword"
                name="confirmPassword"
                type={showPassword ? 'text' : 'password'}
                label={t('confirmPassword')}
                variant="outlined"
                placeholder="••••••••"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                required
                slotProps={{
                  input: {
                    startAdornment: (
                      <InputAdornment position="start">
                        <Lock sx={{ color: 'text.disabled', fontSize: 20 }} />
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
                '&:hover': { bgcolor: '#008F41' },
              }}
            >
              {isLoading ? t('updating') : t('resetPassword')}
            </Button>
          </Box>
        </Paper>
      </Box>
    </AuthLayout>
  );
};

export default ResetPassword;
