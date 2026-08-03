import React, { useState } from 'react';
import { useNavigate, Link as RouterLink } from 'react-router-dom';
import {
  Box,
  Button,
  TextField,
  Typography,
  Paper,
  InputAdornment,
} from '@mui/material';
import { Mail, ArrowBack } from '@mui/icons-material';
import { toast } from 'react-hot-toast';
import { identityService } from '../api/identityService';
import AuthLayout from '../components/Layout/AuthLayout';

const ForgotPassword: React.FC = () => {
  const navigate = useNavigate();
  const [isLoading, setIsLoading] = useState(false);
  const [email, setEmail] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email) {
      toast.error('Vui lòng nhập email');
      return;
    }

    setIsLoading(true);
    try {
      await identityService.forgotPassword({ email });
      toast.success('Yêu cầu đặt lại mật khẩu đã được gửi! Vui lòng kiểm tra email của bạn.');
      navigate('/login');
    } catch (error: any) {
      const message = error.response?.data?.message || 'Gửi yêu cầu thất bại';
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
          }}
        >
          <Box sx={{ mb: 4 }}>
            <Button
              component={RouterLink}
              to="/login"
              startIcon={<ArrowBack />}
              sx={{ color: 'rgba(255, 255, 255, 0.5)', textTransform: 'none', '&:hover': { color: '#fff' } }}
            >
              Quay lại đăng nhập
            </Button>
          </Box>

          <Box sx={{ mb: 5, textAlign: 'center' }}>
            <Typography variant="h4" sx={{ fontWeight: 800, color: '#fff', mb: 1.5 }}>
              Quên mật khẩu?
            </Typography>
            <Typography sx={{ color: 'rgba(255, 255, 255, 0.5)' }}>
              Nhập email của bạn và chúng tôi sẽ gửi liên kết để đặt lại mật khẩu.
            </Typography>
          </Box>

          <Box component="form" onSubmit={handleSubmit} noValidate>
            <Box sx={{ mb: 4 }}>
              <TextField
                fullWidth
                id="email"
                name="email"
                type="email"
                label="Email"
                variant="outlined"
                placeholder="email@example.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
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
              {isLoading ? 'Đang xử lý...' : 'Gửi yêu cầu'}
            </Button>
          </Box>
        </Paper>
      </Box>
    </AuthLayout>
  );
};

export default ForgotPassword;
