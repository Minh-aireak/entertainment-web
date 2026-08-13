import React, { useState } from 'react';
import { useNavigate, Link as RouterLink } from 'react-router-dom';
import {
  Box,
  Button,
  TextField,
  Typography,
  Paper,
  InputAdornment,
  alpha,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Mail, ArrowBack } from '@mui/icons-material';
import { toast } from 'react-hot-toast';
import { identityService } from '../api/identityService';
import AuthLayout from '../components/Layout/AuthLayout';
import { useTranslation } from 'react-i18next';

const ForgotPassword: React.FC = () => {
  const navigate = useNavigate();
  const theme = useTheme();
  const { t } = useTranslation();
  const [isLoading, setIsLoading] = useState(false);
  const [email, setEmail] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email) {
      toast.error(t('emailRequired'));
      return;
    }

    setIsLoading(true);
    try {
      await identityService.forgotPassword({ email });
      toast.success(t('resetRequestSent'));
      navigate('/login');
    } catch (error: any) {
      const message = error.response?.data?.message || t('requestFailed');
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
          <Box sx={{ mb: 4 }}>
            <Button
              component={RouterLink}
              to="/login"
              startIcon={<ArrowBack />}
              sx={{ color: 'text.secondary', textTransform: 'none', '&:hover': { color: 'text.primary' } }}
            >
              {t('backToLogin')}
            </Button>
          </Box>

          <Box sx={{ mb: 5, textAlign: 'center' }}>
            <Typography variant="h4" sx={{ fontWeight: 800, color: 'text.primary', mb: 1.5 }}>
              {t('forgotPasswordTitle')}
            </Typography>
            <Typography sx={{ color: 'text.secondary' }}>
              {t('forgotPasswordDescription')}
            </Typography>
          </Box>

          <Box component="form" onSubmit={handleSubmit} noValidate>
            <Box sx={{ mb: 4 }}>
              <TextField
                fullWidth
                id="email"
                name="email"
                type="email"
                label={t('email')}
                variant="outlined"
                placeholder="email@example.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
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
              {isLoading ? t('processing') : t('sendRequest')}
            </Button>
          </Box>
        </Paper>
      </Box>
    </AuthLayout>
  );
};

export default ForgotPassword;
