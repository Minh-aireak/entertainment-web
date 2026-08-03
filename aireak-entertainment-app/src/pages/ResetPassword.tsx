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
} from '@mui/material';
import { Lock, Visibility, VisibilityOff } from '@mui/icons-material';
import { toast } from 'react-hot-toast';
import { identityService } from '../api/identityService';
import AuthLayout from '../components/Layout/AuthLayout';

const ResetPassword: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const [isLoading, setIsLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [token, setToken] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  useEffect(() => {
    const params = new URLSearchParams(location.search);
    const tokenParam = params.get('token');
    if (!tokenParam) {
      toast.error('Token không hợp lệ hoặc đã hết hạn');
      navigate('/login');
    } else {
      setToken(tokenParam);
    }
  }, [location, navigate]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (password !== confirmPassword) {
      toast.error('Mật khẩu xác nhận không khớp');
      return;
    }

    if (password.length < 8) {
      toast.error('Mật khẩu phải có ít nhất 8 ký tự');
      return;
    }

    setIsLoading(true);
    try {
      await identityService.resetPassword({ token, password });
      toast.success('Mật khẩu đã được đặt lại thành công! Vui lòng đăng nhập lại.');
      navigate('/login');
    } catch (error: any) {
      const message = error.response?.data?.message || 'Đặt lại mật khẩu thất bại';
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
          <Box sx={{ mb: 5, textAlign: 'center' }}>
            <Typography variant="h4" sx={{ fontWeight: 800, color: '#fff', mb: 1.5 }}>
              Đặt lại mật khẩu
            </Typography>
            <Typography sx={{ color: 'rgba(255, 255, 255, 0.5)' }}>
              Vui lòng nhập mật khẩu mới của bạn bên dưới.
            </Typography>
          </Box>

          <Box component="form" onSubmit={handleSubmit} noValidate>
            <Box sx={{ mb: 3 }}>
              <TextField
                fullWidth
                id="password"
                name="password"
                type={showPassword ? 'text' : 'password'}
                label="Mật khẩu mới"
                variant="outlined"
                placeholder="••••••••"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
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

            <Box sx={{ mb: 4 }}>
              <TextField
                fullWidth
                id="confirmPassword"
                name="confirmPassword"
                type={showPassword ? 'text' : 'password'}
                label="Xác nhận mật khẩu"
                variant="outlined"
                placeholder="••••••••"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                required
                slotProps={{
                  input: {
                    startAdornment: (
                      <InputAdornment position="start">
                        <Lock sx={{ color: 'rgba(255, 255, 255, 0.3)', fontSize: 20 }} />
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
              {isLoading ? 'Đang cập nhật...' : 'Đặt lại mật khẩu'}
            </Button>
          </Box>
        </Paper>
      </Box>
    </AuthLayout>
  );
};

export default ResetPassword;
