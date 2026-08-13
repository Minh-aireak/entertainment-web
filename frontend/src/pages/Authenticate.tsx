import React, { useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { Box, CircularProgress, Typography } from '@mui/material';
import { useDispatch } from 'react-redux';
import { toast } from 'react-hot-toast';
import { loginSuccess } from '../store';
import { identityService } from '../api/identityService';
import { profileService } from '../api/profileService';
import { useTranslation } from 'react-i18next';

const Authenticate: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const dispatch = useDispatch();
  const { t } = useTranslation();

  useEffect(() => {
    const params = new URLSearchParams(location.search);
    const code = params.get('code');

    if (code) {
      handleAuthentication(code);
    } else {
      navigate('/login');
    }
  }, [location, navigate, dispatch]);

  const handleAuthentication = async (code: string) => {
    try {
      const response = await identityService.outboundAuthenticate(code);
      
      if (response.code === 1000) {
        // Fetch thông tin profile ngay lập tức để có dữ liệu user
        try {
          const profileRes = await profileService.getMyProfile();
          if (profileRes.code === 1000) {
            const profile = profileRes.result;

            // Lấy roles thật từ identity-service (giống Login.tsx) để admin
            // không bị mất quyền quản trị khi đăng nhập qua Google.
            let roles: { name: string; description: string }[] = [
              { name: 'USER', description: 'Default user role' },
            ];
            try {
              const myInfoResponse = await identityService.getMyInfo();
              if (myInfoResponse.code === 1000 && myInfoResponse.result) {
                roles = myInfoResponse.result.roles;
              }
            } catch (roleError) {
              console.debug('Could not load roles after Google login:', roleError);
            }

            const user = {
              id: profile.userId,
              username: profile.username,
              email: profile.email,
              roles,
            };
            dispatch(loginSuccess({ user }));
          } else {
            // Nếu không lấy được profile, vẫn cho login với user null hoặc placeholder
            dispatch(loginSuccess({ user: null }));
          }
        } catch (profileError) {
          console.error('Failed to fetch profile after Google login:', profileError);
          dispatch(loginSuccess({ user: null }));
        }

        toast.success(t('googleLoginSuccess'));
        navigate('/social');
      } else {
        throw new Error(response.message || t('loginFailed'));
      }
    } catch (error: any) {
      console.error('Authentication error:', error);
      toast.error(error.message || t('googleAuthenticationFailed'));
      navigate('/login');
    }
  };

  return (
    <Box
      sx={{
        height: '100vh',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        bgcolor: 'background.default',
        color: 'text.primary',
        gap: 3,
      }}
    >
      <CircularProgress sx={{ color: '#00A84E' }} />
      <Typography variant="h6" sx={{ fontWeight: 600 }}>
        {t('authenticatingAccount')}
      </Typography>
      <Typography sx={{ color: 'text.secondary' }}>
        {t('pleaseWait')}
      </Typography>
    </Box>
  );
};

export default Authenticate;
