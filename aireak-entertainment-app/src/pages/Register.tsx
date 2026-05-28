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
import { Visibility, VisibilityOff, Mail, Lock, Person } from '@mui/icons-material';
import { toast } from 'react-hot-toast';
import { identityService } from '../api/identityService';
import AuthLayout from '../components/Layout/AuthLayout';

const Register: React.FC = () => {
  const navigate = useNavigate();

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
      toast.error('Mật khẩu không khớp');
      return;
    }
    setIsLoading(true);
    try {
      await identityService.registration({
        username: formData.username,
        email: formData.email,
        password: formData.password,
      });
      toast.success('Đăng ký thành công! Hãy đăng nhập.');
      navigate('/login');
    } catch (error: any) {
      const message = error.response?.data?.message || 'Đăng ký thất bại';
      toast.error(message);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <AuthLayout>
      {/* Register Card */}
      <Paper
        elevation={0}
        sx={{
          p: { xs: 3, sm: 4 },
          borderRadius: 3,
          backgroundColor: '#141414',
          border: '1px solid #262626',
          boxShadow: '0 8px 32px rgba(0, 0, 0, 0.5)',
          width: '100%',
          maxWidth: '500px',
          animation: 'slideUp 0.5s ease-out',
          '@keyframes slideUp': {
            from: {
              opacity: 0,
              transform: 'translateY(20px)',
            },
            to: {
              opacity: 1,
              transform: 'translateY(0)',
            },
          },
        }}
      >
        {/* Header */}
        <Box sx={{ textAlign: 'center', mb: 4 }}>
          <Typography
            sx={{
              color: 'rgba(255, 255, 255, 0.6)',
              fontSize: '0.95rem',
              letterSpacing: '0.5px',
            }}
          >
            Đăng ký để khám phá các bộ phim tuyệt vời
          </Typography>
        </Box>

        {/* Form */}
        <Box component="form" onSubmit={handleSubmit} noValidate>
          {/* Username Field */}
          <Box sx={{ mb: 2.5 }}>
            <Typography
              sx={{
                fontSize: '0.85rem',
                fontWeight: 600,
                color: 'rgba(255, 255, 255, 0.8)',
                mb: 1,
                textTransform: 'none',
                letterSpacing: '0.5px',
              }}
            >
              Tên đăng nhập
            </Typography>
            <TextField
              fullWidth
              id="username"
              name="username"
              placeholder="your_username"
              value={formData.username}
              onChange={handleChange}
              required
              slotProps={{
                input: {
                  startAdornment: (
                    <InputAdornment position="start">
                      <Person sx={{ color: 'rgba(255, 255, 255, 0.4)', mr: 1 }} />
                    </InputAdornment>
                  ),
                },
              }}
              sx={{
                '& .MuiOutlinedInput-root': {
                  backgroundColor: 'rgba(255, 255, 255, 0.05)',
                  borderRadius: 2,
                  border: '1px solid #333',
                  transition: 'all 0.3s ease',
                  '&:hover': {
                    backgroundColor: 'rgba(255, 255, 255, 0.08)',
                    borderColor: '#444',
                  },
                  '&.Mui-focused': {
                    backgroundColor: 'rgba(255, 255, 255, 0.08)',
                    borderColor: '#00A84E',
                  },
                  '& fieldset': {
                    border: 'none',
                  },
                  '& input::placeholder': {
                    color: 'rgba(255, 255, 255, 0.4)',
                    opacity: 1,
                  },
                },
              }}
            />
          </Box>

          {/* Email Field */}
          <Box sx={{ mb: 2.5 }}>
            <Typography
              sx={{
                fontSize: '0.85rem',
                fontWeight: 600,
                color: 'rgba(255, 255, 255, 0.8)',
                mb: 1,
                textTransform: 'none',
                letterSpacing: '0.5px',
              }}
            >
              Email
            </Typography>
            <TextField
              fullWidth
              id="email"
              name="email"
              type="email"
              placeholder="name@example.com"
              value={formData.email}
              onChange={handleChange}
              required
              slotProps={{
                input: {
                  startAdornment: (
                    <InputAdornment position="start">
                      <Mail sx={{ color: 'rgba(255, 255, 255, 0.4)', mr: 1 }} />
                    </InputAdornment>
                  ),
                },
              }}
              sx={{
                '& .MuiOutlinedInput-root': {
                  backgroundColor: 'rgba(255, 255, 255, 0.05)',
                  borderRadius: 2,
                  border: '1px solid #333',
                  transition: 'all 0.3s ease',
                  '&:hover': {
                    backgroundColor: 'rgba(255, 255, 255, 0.08)',
                    borderColor: '#444',
                  },
                  '&.Mui-focused': {
                    backgroundColor: 'rgba(255, 255, 255, 0.08)',
                    borderColor: '#00A84E',
                  },
                  '& fieldset': {
                    border: 'none',
                  },
                  '& input::placeholder': {
                    color: 'rgba(255, 255, 255, 0.4)',
                    opacity: 1,
                  },
                },
              }}
            />
          </Box>

          {/* Password Field */}
          <Box sx={{ mb: 2.5 }}>
            <Typography
              sx={{
                fontSize: '0.85rem',
                fontWeight: 600,
                color: 'rgba(255, 255, 255, 0.8)',
                mb: 1,
                textTransform: 'none',
                letterSpacing: '0.5px',
              }}
            >
              Mật khẩu
            </Typography>
            <TextField
              fullWidth
              id="password"
              name="password"
              type={showPassword ? 'text' : 'password'}
              placeholder="••••••••"
              value={formData.password}
              onChange={handleChange}
              required
              slotProps={{
                input: {
                  startAdornment: (
                    <InputAdornment position="start">
                      <Lock sx={{ color: 'rgba(255, 255, 255, 0.4)', mr: 1 }} />
                    </InputAdornment>
                  ),
                  endAdornment: (
                    <InputAdornment position="end">
                      <IconButton
                        aria-label="toggle password visibility"
                        onClick={() => setShowPassword(!showPassword)}
                        edge="end"
                        sx={{
                          color: 'rgba(255, 255, 255, 0.4)',
                          '&:hover': {
                            backgroundColor: 'rgba(255, 255, 255, 0.05)',
                          },
                        }}
                      >
                        {showPassword ? <VisibilityOff /> : <Visibility />}
                      </IconButton>
                    </InputAdornment>
                  ),
                },
              }}
              sx={{
                '& .MuiOutlinedInput-root': {
                  backgroundColor: 'rgba(255, 255, 255, 0.05)',
                  borderRadius: 2,
                  border: '1px solid #333',
                  transition: 'all 0.3s ease',
                  '&:hover': {
                    backgroundColor: 'rgba(255, 255, 255, 0.08)',
                    borderColor: '#444',
                  },
                  '&.Mui-focused': {
                    backgroundColor: 'rgba(255, 255, 255, 0.08)',
                    borderColor: '#00A84E',
                  },
                  '& fieldset': {
                    border: 'none',
                  },
                  '& input::placeholder': {
                    color: 'rgba(255, 255, 255, 0.4)',
                    opacity: 1,
                  },
                },
              }}
            />
          </Box>

          {/* Confirm Password Field */}
          <Box sx={{ mb: 4 }}>
            <Typography
              sx={{
                fontSize: '0.85rem',
                fontWeight: 600,
                color: 'rgba(255, 255, 255, 0.8)',
                mb: 1,
                textTransform: 'none',
                letterSpacing: '0.5px',
              }}
            >
              Xác nhận mật khẩu
            </Typography>
            <TextField
              fullWidth
              id="confirmPassword"
              name="confirmPassword"
              type={showConfirmPassword ? 'text' : 'password'}
              placeholder="••••••••"
              value={formData.confirmPassword}
              onChange={handleChange}
              required
              slotProps={{
                input: {
                  startAdornment: (
                    <InputAdornment position="start">
                      <Lock sx={{ color: 'rgba(255, 255, 255, 0.4)', mr: 1 }} />
                    </InputAdornment>
                  ),
                  endAdornment: (
                    <InputAdornment position="end">
                      <IconButton
                        aria-label="toggle confirm password visibility"
                        onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                        edge="end"
                        sx={{
                          color: 'rgba(255, 255, 255, 0.4)',
                          '&:hover': {
                            backgroundColor: 'rgba(255, 255, 255, 0.05)',
                          },
                        }}
                      >
                        {showConfirmPassword ? <VisibilityOff /> : <Visibility />}
                      </IconButton>
                    </InputAdornment>
                  ),
                },
              }}
              sx={{
                '& .MuiOutlinedInput-root': {
                  backgroundColor: 'rgba(255, 255, 255, 0.05)',
                  borderRadius: 2,
                  border: '1px solid #333',
                  transition: 'all 0.3s ease',
                  '&:hover': {
                    backgroundColor: 'rgba(255, 255, 255, 0.08)',
                    borderColor: '#444',
                  },
                  '&.Mui-focused': {
                    backgroundColor: 'rgba(255, 255, 255, 0.08)',
                    borderColor: '#00A84E',
                  },
                  '& fieldset': {
                    border: 'none',
                  },
                  '& input::placeholder': {
                    color: 'rgba(255, 255, 255, 0.4)',
                    opacity: 1,
                  },
                },
              }}
            />
          </Box>

          {/* Submit Button */}
          <Button
            type="submit"
            fullWidth
            disabled={isLoading}
            variant="contained"
            sx={{
              py: 1.5,
              fontSize: '1rem',
              fontWeight: 700,
              borderRadius: 2,
              backgroundColor: '#00A84E',
              '&:hover': {
                backgroundColor: '#00C853',
                transform: 'none',
              },
              mb: 3,
            }}
          >
            {isLoading ? 'Đang xử lý...' : 'Đăng ký ngay'}
          </Button>

          {/* Footer Link */}
          <Box sx={{ textAlign: 'center' }}>
            <Typography sx={{ fontSize: '0.9rem', color: 'rgba(255, 255, 255, 0.6)' }}>
              Đã có tài khoản?{' '}
              <Link
                component={RouterLink}
                to="/login"
                sx={{
                  color: '#00A84E',
                  textDecoration: 'none',
                  fontWeight: 600,
                  '&:hover': {
                    textDecoration: 'underline',
                  },
                }}
              >
                Đăng nhập ngay
              </Link>
            </Typography>
          </Box>
        </Box>
      </Paper>
    </AuthLayout>
  );
};

export default Register;
