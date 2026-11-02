import React, { useState, useEffect, useCallback } from 'react';
import { useSelector, useDispatch } from 'react-redux';
import { setProfileData, setProfileLoading, type RootState } from '../store';
import {
  Box,
  Typography,
  Paper,
  Avatar,
  Grid,
  TextField,
  Button,
  IconButton,
  CircularProgress,
  InputAdornment,
  CardContent,
  Card,
} from '@mui/material';
import { CameraAlt, CalendarMonth, Message, Edit, AccountCircle, Bolt, NotificationsActive } from '@mui/icons-material';
import { alpha, useTheme } from '@mui/material/styles';
import { toast } from 'react-hot-toast';
import { profileService } from '../api/profileService';
import { fileService } from '../api/fileService';
import { notificationService } from '../api/notificationService';
import type { NotificationResponse, ConversationResponse } from '../models';
import { useTranslation } from 'react-i18next';
import { EmptyState, SectionHeader } from '../components/common/VibeUi';
import {
  VIBE_GLOW,
  VIBE_GRADIENT,
  VIBE_LOVE_GRADIENT,
  gradientTextSx,
  heroSurfaceSx,
  hoverLiftSx,
  iconTileSx,
  vibeAccentText,
  vibeBorder,
  vibeSurface,
} from '../styles/vibe';

type FormErrors = Partial<Record<'firstName' | 'lastName' | 'phoneNumber' | 'email' | 'displayName' | 'dob', string>>;

const ProfilePage: React.FC = React.memo(() => {
  const dispatch = useDispatch();
  const { t, i18n } = useTranslation();
  const theme = useTheme();
  const fileInputRef = React.useRef<HTMLInputElement>(null);
  const { profileData, loading: profileLoading } = useSelector((state: RootState) => state.profile);
  const [isEditing, setIsEditing] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [touched, setTouched] = useState<Partial<Record<string, boolean>>>({});
  const [errors, setErrors] = useState<FormErrors>({});
  const [recentActivities, setRecentActivities] = useState<{
    notifications: NotificationResponse[];
    conversations: ConversationResponse[];
  }>({
    notifications: [],
    conversations: [],
  });

  const [formData, setFormData] = useState({
    username: '',
    email: '',
    displayName: '',
    firstName: '',
    lastName: '',
    dob: '',
    phoneNumber: '',
    city: '',
    avatar: '',
  });

  // Validation logic
  const validateField = (name: string, value: string): string | null => {
    switch (name) {
      case 'firstName':
      case 'lastName':
        if (!value.trim()) return t('requiredField');
        if (value.trim().length < 2) return t('minTwoCharacters');
        return null;
      case 'phoneNumber':
        if (!value.trim()) return null;
        const phoneRegex = /^(0[1-9])(\d){8}$/;
        if (!phoneRegex.test(value)) return t('invalidPhone');
        return null;
      case 'email':
        if (!value.trim()) return t('requiredField');
        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        if (!emailRegex.test(value)) return t('invalidEmail');
        return null;
      case 'displayName':
        if (!value.trim()) return t('requiredField');
        return null;
      case 'dob':
        if (!value.trim()) return null;
        const dobDate = new Date(value);
        const today = new Date();
        if (isNaN(dobDate.getTime())) return t('invalidBirthDate');
        if (dobDate > today) return t('futureBirthDate');
        // Tính tuổi
        let age = today.getFullYear() - dobDate.getFullYear();
        const monthDiff = today.getMonth() - dobDate.getMonth();
        if (monthDiff < 0 || (monthDiff === 0 && today.getDate() < dobDate.getDate())) {
          age--;
        }
        if (age < 16) return t('minimumAge');
        return null;
      default:
        return null;
    }
  };

  // Cập nhật formData khi profileData trong Redux thay đổi
  useEffect(() => {
    if (profileData) {
      setFormData({
        username: profileData.username || '',
        email: profileData.email || '',
        displayName: profileData.displayName || '',
        firstName: profileData.firstName || '',
        lastName: profileData.lastName || '',
        dob: profileData.dob || '',
        phoneNumber: profileData.phoneNumber || '',
        city: profileData.city || '',
        avatar: profileData.avatar || '',
      });
    }
  }, [profileData]);

  const fetchProfileData = useCallback(async (force = false) => {
    if (!profileData || force) {
      dispatch(setProfileLoading(true));
    }
    
    try {
      const [summaryRes, notificationsRes] = await Promise.all([
        profileService.getUserSummary(),
        notificationService.getMyNotifications(1, 3),
      ]);

      if (summaryRes.code === 1000) {
        dispatch(setProfileData(summaryRes.result));
      }

      if (notificationsRes.code === 1000) {
        setRecentActivities(prev => ({ ...prev, notifications: notificationsRes.result.data }));
      }

    } catch (error) {
      console.error('Failed to fetch profile data:', error);
      toast.error(t('profileLoadFailed'));
    } finally {
      dispatch(setProfileLoading(false));
    }
  }, [dispatch, profileData, t]);

  useEffect(() => {
    if (!profileData) {
      fetchProfileData();
    } else {
      fetchProfileData(false);
    }
  }, []);

  const maskEmail = (email: string) => {
    if (!email) return '';
    const [name, domain] = email.split('@');
    if (name.length <= 3) return email;
    return name.substring(0, 3) + '*******' + '@' + domain;
  };

  const maskPhone = (phone: string) => {
    if (!phone) return '';
    return '*******' + phone.slice(-3);
  };

  const maskCity = (city: string) => {
    if (!city) return '';
    if (city.length <= 2) return city;
    return city.substring(0, 2) + '*******';
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
  };

  const handleBlur = (e: React.FocusEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setTouched(prev => ({ ...prev, [name]: true }));
    const error = validateField(name, value);
    setErrors(prev => ({ ...prev, [name]: error || '' }));
  };

  const validateForm = () => {
    const newErrors: FormErrors = {};
    let isValid = true;
    ['firstName', 'lastName', 'email', 'displayName', 'phoneNumber', 'dob'].forEach(name => {
      const error = validateField(name, formData[name as keyof typeof formData]);
      if (error) {
        newErrors[name as keyof FormErrors] = error;
        isValid = false;
      }
    });
    setErrors(newErrors);
    setTouched({ firstName: true, lastName: true, email: true, displayName: true, phoneNumber: true, dob: true });
    return isValid;
  };

  const handleSave = async () => {
    if (!validateForm()) {
      toast.error(t('checkFormErrors'));
      return;
    }

    try {
      const response = await profileService.updateProfile({
        displayName: formData.displayName,
        city: formData.city,
        dob: formData.dob,
        firstName: formData.firstName,
        lastName: formData.lastName,
        email: formData.email,
        phoneNumber: formData.phoneNumber,
      });
      if (response.code === 1000) {
        toast.success(t('profileUpdated'));
        setIsEditing(false);
        fetchProfileData(true);
      } else {
        toast.error(response.message || t('profileUpdateFailed'));
      }
    } catch (error: any) {
      console.error('Failed to update profile:', error);
      const errorMsg = error.response?.data?.message || t('profileUpdateFailed');
      toast.error(errorMsg);
    }
  };

  const handleAvatarClick = () => {
    fileInputRef.current?.click();
  };

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    if (!file.type.startsWith('image/')) {
      toast.error(t('chooseImageFile'));
      return;
    }

    if (file.size > 5 * 1024 * 1024) {
      toast.error(t('imageTooLarge'));
      return;
    }

    setUploading(true);
    const toastId = toast.loading(t('uploadingImage'));
    try {
      const uploadRes = await fileService.uploadFile(file);
      if (uploadRes.code === 1000) {
        // avatar hiển thị dùng URL mới nhận (còn hiệu lực ngay lúc này); server chỉ lưu fileId và
        // tự resolve lại URL mới mỗi lần trả profile response, tránh lưu presigned URL bị hết hạn.
        const newAvatarUrl = uploadRes.result.url;

        const updateRes = await profileService.updateAvatar(uploadRes.result.id);

        if (updateRes.code === 1000) {
          setFormData(prev => ({ ...prev, avatar: newAvatarUrl }));
          fetchProfileData(true);
          toast.success(t('avatarUpdated'), { id: toastId });
        } else {
          toast.error(updateRes.message || t('avatarUpdateFailed'), { id: toastId });
        }
      } else {
        toast.error(t('imageUploadFailed', { message: uploadRes.message || '' }), { id: toastId });
      }
    } catch (error: any) {
      console.error('Failed to upload avatar:', error);
      const errorMsg = error.response?.data?.message || t('imageUploadError');
      toast.error(errorMsg, { id: toastId });
    } finally {
      setUploading(false);
    }
  };

  if (profileLoading && !profileData) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100%' }}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Box>
      <Grid container spacing={4}>
        <Grid size={{ xs: 12, md: 4 }}>
          <Paper elevation={0} sx={{ textAlign: 'center', borderRadius: '28px', overflow: 'hidden', border: '1px solid', borderColor: vibeBorder(theme) }}>
            <Box
              aria-hidden
              sx={{
                ...heroSurfaceSx(theme),
                height: 128,
                borderRadius: 0,
                border: 'none',
                boxShadow: 'none',
                backgroundColor: alpha(theme.palette.text.primary, 0.02),
              }}
            />
            <Box sx={{ px: 4, pb: 4 }}>
            <Box sx={{ position: 'relative', display: 'inline-block', mb: 2, mt: '-78px', p: '4px', borderRadius: '50%', background: VIBE_GRADIENT, boxShadow: VIBE_GLOW }}>
              <input
                type="file"
                ref={fileInputRef}
                style={{ display: 'none' }}
                accept="image/*"
                onChange={handleFileChange}
              />
              <Avatar 
                src={formData.avatar}
                sx={{
                  width: 140,
                  height: 140,
                  fontSize: 56,
                  fontWeight: 900,
                  color: '#fff',
                  background: VIBE_GRADIENT,
                  border: '4px solid',
                  borderColor: 'background.paper',
                  cursor: uploading ? 'default' : 'pointer',
                  opacity: uploading ? 0.6 : 1,
                  transition: 'opacity 0.3s ease'
                }}
                onClick={!uploading ? handleAvatarClick : undefined}
              >
                {uploading ? (
                  <CircularProgress size={40} color="inherit" />
                ) : (
                  formData.displayName?.[0]?.toUpperCase() || formData.username?.[0]?.toUpperCase()
                )}
              </Avatar>
              <IconButton 
                onClick={!uploading ? handleAvatarClick : undefined}
                disabled={uploading}
                sx={{
                  position: 'absolute',
                  bottom: 6,
                  right: 6,
                  width: 42,
                  height: 42,
                  color: '#fff',
                  background: VIBE_GRADIENT,
                  boxShadow: VIBE_GLOW,
                  border: '3px solid',
                  borderColor: 'background.paper',
                  '&:hover': { transform: 'scale(1.08)' },
                  '&.Mui-disabled': { color: '#fff', opacity: 0.7 }
                }}
              >
                {uploading ? (
                  <CircularProgress size={18} color="inherit" />
                ) : (
                  <CameraAlt sx={{ fontSize: 20 }} />
                )}
              </IconButton>
            </Box>
            <Typography variant="h5" sx={{ fontWeight: 900, letterSpacing: '-0.02em' }}>{formData.displayName}</Typography>
            <Typography variant="body2" sx={{ mb: 3, fontWeight: 800, color: vibeAccentText(theme) }}>@{formData.username}</Typography>
            <Grid container spacing={1.5}>
              {[
                { value: profileData?.totalPosts ?? '-', label: t('totalPosts') },
                { value: profileData?.totalFriends ?? '-', label: t('friends') },
              ].map((stat) => (
                <Grid size={{ xs: 6 }} key={stat.label}>
                  <Box sx={{ py: 1.75, borderRadius: '18px', bgcolor: vibeSurface(theme), border: '1px solid', borderColor: vibeBorder(theme) }}>
                    <Typography variant="h5" sx={{ fontWeight: 900, lineHeight: 1.1, ...gradientTextSx }}>
                      {stat.value}
                    </Typography>
                    <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700 }}>{stat.label}</Typography>
                  </Box>
                </Grid>
              ))}
            </Grid>
            </Box>
          </Paper>
        </Grid>

        <Grid size={{ xs: 12, md: 8 }}>
          <Paper elevation={0} sx={{ p: { xs: 2.5, md: 4 }, borderRadius: '28px', border: '1px solid', borderColor: vibeBorder(theme) }}>
            <SectionHeader
              icon={<AccountCircle />}
              title={t('personalInformation')}
              sx={{ mb: 3 }}
              action={!isEditing ? (
                <Button startIcon={<Edit />} variant="outlined" onClick={() => setIsEditing(true)} sx={{ borderRadius: '999px', px: 2 }}>
                  {t('edit')}
                </Button>
              ) : (
                <Box sx={{ display: 'flex', gap: 1 }}>
                  <Button variant="outlined" onClick={() => setIsEditing(false)} sx={{ borderRadius: '999px', px: 2 }}>{t('cancel')}</Button>
                  <Button variant="contained" onClick={handleSave} sx={{ borderRadius: '999px', px: 2.5 }}>{t('save')}</Button>
                </Box>
              )}
            />
            
            <Grid container spacing={3}>
              <Grid size={{ xs: 12, sm: 6 }}>
                <TextField
                  fullWidth
                  label={t('username')}
                  name="username"
                  value={formData.username}
                  disabled
                />
              </Grid>
              <Grid size={{ xs: 12, sm: 6 }}>
                <TextField
                  fullWidth
                  label={t('email')}
                  name="email"
                  value={isEditing ? formData.email : maskEmail(formData.email)}
                  disabled={!isEditing}
                  onChange={handleChange}
                  onBlur={handleBlur}
                  error={touched.email && !!errors.email}
                  helperText={touched.email && errors.email}
                />
              </Grid>
              <Grid size={{ xs: 12, sm: 6 }}>
                <TextField
                  fullWidth
                  label={t('displayName')}
                  name="displayName"
                  value={formData.displayName}
                  disabled={!isEditing}
                  onChange={handleChange}
                  onBlur={handleBlur}
                  error={touched.displayName && !!errors.displayName}
                  helperText={touched.displayName && errors.displayName}
                />
              </Grid>
              <Grid size={{ xs: 12, sm: 6 }}>
                <TextField
                  fullWidth
                  label={t('lastName')}
                  name="lastName"
                  value={formData.lastName}
                  disabled={!isEditing}
                  onChange={handleChange}
                  onBlur={handleBlur}
                  error={touched.lastName && !!errors.lastName}
                  helperText={touched.lastName && errors.lastName}
                />
              </Grid>
              <Grid size={{ xs: 12, sm: 6 }}>
                <TextField
                  fullWidth
                  label={t('firstName')}
                  name="firstName"
                  value={formData.firstName}
                  disabled={!isEditing}
                  onChange={handleChange}
                  onBlur={handleBlur}
                  error={touched.firstName && !!errors.firstName}
                  helperText={touched.firstName && errors.firstName}
                />
              </Grid>
              <Grid size={{ xs: 12, sm: 6 }}>
                <TextField
                  fullWidth
                  label={t('phoneNumber')}
                  name="phoneNumber"
                  value={isEditing ? formData.phoneNumber : maskPhone(formData.phoneNumber)}
                  disabled={!isEditing}
                  onChange={handleChange}
                  onBlur={handleBlur}
                  error={touched.phoneNumber && !!errors.phoneNumber}
                  helperText={touched.phoneNumber && errors.phoneNumber}
                />
              </Grid>
              <Grid size={{ xs: 12, sm: 6 }}>
                <TextField
                  fullWidth
                  label={t('dateOfBirth')}
                  name="dob"
                  type="date"
                  value={formData.dob}
                  disabled={!isEditing}
                  onChange={handleChange}
                  onBlur={handleBlur}
                  error={touched.dob && !!errors.dob}
                  helperText={touched.dob && errors.dob}
                  slotProps={{
                    input: {
                      startAdornment: (
                        <InputAdornment position="start">
                          <CalendarMonth
                            sx={{
                              color: 'text.secondary',
                              mr: 1
                            }}
                          />
                        </InputAdornment>
                      ),
                    },
                  }}
                />
              </Grid>
              <Grid size={{ xs: 12, sm: 6 }}>
                <TextField
                  fullWidth
                  label={t('city')}
                  name="city"
                  value={isEditing ? formData.city : maskCity(formData.city)}
                  disabled={!isEditing}
                  onChange={handleChange}
                />
              </Grid>
            </Grid>
          </Paper>

          <SectionHeader icon={<Bolt />} title={t('recentActivity')} sx={{ mt: 4 }} />
          <Grid container spacing={1.5}>
            {recentActivities.notifications.length === 0 && recentActivities.conversations.length === 0 && (
              <Grid size={{ xs: 12 }}>
                <EmptyState compact emoji="✨" title={t('noRecentActivity')} />
              </Grid>
            )}
            
            {recentActivities.notifications.map((notif) => (
              <Grid size={{ xs: 12 }} key={notif.id}>
                <Card elevation={0} sx={{ borderRadius: '20px', ...hoverLiftSx(theme, 2) }}>
                  <CardContent sx={{ display: 'flex', alignItems: 'center', gap: 2, '&:last-child': { pb: 2 } }}>
                    <Box sx={{ ...iconTileSx(40), boxShadow: 'none' }}>
                      <NotificationsActive sx={{ fontSize: 20 }} />
                    </Box>
                    <Box sx={{ minWidth: 0 }}>
                      <Typography variant="body1" sx={{ fontWeight: 700 }}>
                        {notif.message}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {new Date(notif.createdAt).toLocaleString(i18n.language)}
                      </Typography>
                    </Box>
                  </CardContent>
                </Card>
              </Grid>
            ))}

            {recentActivities.conversations.map((conv) => (
              <Grid size={{ xs: 12 }} key={conv.id}>
                <Card elevation={0} sx={{ borderRadius: '20px', ...hoverLiftSx(theme, 2) }}>
                  <CardContent sx={{ display: 'flex', alignItems: 'center', gap: 2, '&:last-child': { pb: 2 } }}>
                    <Box sx={{ ...iconTileSx(40), background: VIBE_LOVE_GRADIENT, boxShadow: 'none' }}>
                      <Message sx={{ fontSize: 20 }} />
                    </Box>
                    <Box sx={{ minWidth: 0 }}>
                      <Typography variant="body1" sx={{ fontWeight: 700 }}>
                        {t('newConversationIn', { conversation: (conv as any).conversationName || t('conversationFallback') })}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {conv.modifiedDate ? new Date(conv.modifiedDate).toLocaleString(i18n.language) : ''}
                      </Typography>
                    </Box>
                  </CardContent>
                </Card>
              </Grid>
            ))}
          </Grid>
        </Grid>
      </Grid>
    </Box>
  );
});

export default ProfilePage;
