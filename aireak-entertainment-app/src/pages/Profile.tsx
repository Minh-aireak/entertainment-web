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
  Divider,
  IconButton,
  CircularProgress,
  InputAdornment,
  CardContent,
  Card,
} from '@mui/material';
import { CameraAlt, CalendarMonth, Message, Edit } from '@mui/icons-material';
import { toast } from 'react-hot-toast';
import { profileService } from '../api/profileService';
import { fileService } from '../api/fileService';
import { notificationService } from '../api/notificationService';
import type { NotificationResponse, ConversationResponse } from '../models';

type FormErrors = Partial<Record<'firstName' | 'lastName' | 'phoneNumber' | 'email' | 'displayName' | 'dob', string>>;

const ProfilePage: React.FC = React.memo(() => {
  const dispatch = useDispatch();
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
        if (!value.trim()) return 'Trường này không được bỏ trống';
        if (value.trim().length < 2) return 'Vui lòng nhập ít nhất 2 ký tự';
        return null;
      case 'phoneNumber':
        if (!value.trim()) return null;
        const phoneRegex = /^(0[1-9])(\d){8}$/;
        if (!phoneRegex.test(value)) return 'Số điện thoại không hợp lệ (phải là 10 số và bắt đầu bằng 0)';
        return null;
      case 'email':
        if (!value.trim()) return 'Trường này không được bỏ trống';
        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        if (!emailRegex.test(value)) return 'Email không đúng định dạng';
        return null;
      case 'displayName':
        if (!value.trim()) return 'Trường này không được bỏ trống';
        return null;
      case 'dob':
        if (!value.trim()) return null;
        const dobDate = new Date(value);
        const today = new Date();
        if (isNaN(dobDate.getTime())) return 'Ngày sinh không hợp lệ';
        if (dobDate > today) return 'Ngày sinh không được lớn hơn hôm nay';
        // Tính tuổi
        let age = today.getFullYear() - dobDate.getFullYear();
        const monthDiff = today.getMonth() - dobDate.getMonth();
        if (monthDiff < 0 || (monthDiff === 0 && today.getDate() < dobDate.getDate())) {
          age--;
        }
        if (age < 16) return 'Bạn phải đủ ít nhất 16 tuổi';
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
      toast.error('Không thể tải thông tin cá nhân');
    } finally {
      dispatch(setProfileLoading(false));
    }
  }, [dispatch, profileData]);

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
      toast.error('Vui lòng kiểm tra lại thông tin nhập vào');
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
        toast.success('Cập nhật thông tin thành công!');
        setIsEditing(false);
        fetchProfileData(true);
      } else {
        toast.error(response.message || 'Cập nhật thất bại');
      }
    } catch (error: any) {
      console.error('Failed to update profile:', error);
      const errorMsg = error.response?.data?.message || 'Cập nhật thất bại';
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
      toast.error('Vui lòng chọn tệp hình ảnh');
      return;
    }

    if (file.size > 5 * 1024 * 1024) {
      toast.error('Kích thước ảnh không được vượt quá 5MB');
      return;
    }

    setUploading(true);
    const toastId = toast.loading('Đang tải ảnh lên...');
    try {
      const uploadRes = await fileService.uploadFile(file);
      if (uploadRes.code === 1000) {
        const newAvatarUrl = uploadRes.result.url;
        
        const updateRes = await profileService.updateAvatar(newAvatarUrl);

        if (updateRes.code === 1000) {
          setFormData(prev => ({ ...prev, avatar: newAvatarUrl }));
          fetchProfileData(true);
          toast.success('Cập nhật ảnh đại diện thành công!', { id: toastId });
        } else {
          toast.error(updateRes.message || 'Không thể cập nhật ảnh đại diện', { id: toastId });
        }
      } else {
        toast.error('Tải ảnh lên thất bại: ' + (uploadRes.message || ''), { id: toastId });
      }
    } catch (error: any) {
      console.error('Failed to upload avatar:', error);
      const errorMsg = error.response?.data?.message || 'Có lỗi xảy ra khi tải ảnh lên';
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
          <Paper sx={{ p: 4, textAlign: 'center', borderRadius: 4 }}>
            <Box sx={{ position: 'relative', display: 'inline-block', mb: 2 }}>
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
                  width: 150, 
                  height: 150, 
                  fontSize: 60, 
                  bgcolor: 'primary.main',
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
                  bottom: 0, 
                  right: 0, 
                  bgcolor: 'white',
                  boxShadow: 2,
                  '&:hover': { bgcolor: '#f0f0f0' },
                  '&.Mui-disabled': { bgcolor: '#e0e0e0' }
                }}
              >
                {uploading ? (
                  <CircularProgress size={20} />
                ) : (
                  <CameraAlt color="primary" />
                )}
              </IconButton>
            </Box>
            <Typography variant="h5" sx={{ fontWeight: 'bold' }}>{formData.displayName}</Typography>
            <Typography variant="body1" color="text.secondary" sx={{ mb: 3 }}>@{formData.username}</Typography>
            <Divider sx={{ mb: 3 }} />
            <Grid container spacing={2}>
              <Grid size={{ xs: 6 }}>
                <Typography variant="h6" sx={{ fontWeight: 'bold' }}>
                  {profileData?.totalPosts ?? '-'}
                </Typography>
                <Typography variant="caption" color="text.secondary">Tổng lịch trình</Typography>
              </Grid>
              <Grid size={{ xs: 6 }}>
                <Typography variant="h6" sx={{ fontWeight: 'bold' }}>
                  {profileData?.totalFriends ?? '-'}
                </Typography>
                <Typography variant="caption" color="text.secondary">Bạn bè</Typography>
              </Grid>
            </Grid>
          </Paper>
        </Grid>

        <Grid size={{ xs: 12, md: 8 }}>
          <Paper sx={{ p: 4, borderRadius: 4 }}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
              <Typography variant="h6" sx={{ fontWeight: 'bold' }}>Thông tin cá nhân</Typography>
              {!isEditing ? (
                <Button startIcon={<Edit />} variant="outlined" onClick={() => setIsEditing(true)}>
                  Chỉnh sửa
                </Button>
              ) : (
                <Box sx={{ display: 'flex', gap: 1 }}>
                  <Button variant="outlined" onClick={() => setIsEditing(false)}>Hủy</Button>
                  <Button variant="contained" onClick={handleSave}>Lưu</Button>
                </Box>
              )}
            </Box>
            
            <Grid container spacing={3}>
              <Grid size={{ xs: 12, sm: 6 }}>
                <TextField
                  fullWidth
                  label="Tên đăng nhập"
                  name="username"
                  value={formData.username}
                  disabled
                />
              </Grid>
              <Grid size={{ xs: 12, sm: 6 }}>
                <TextField
                  fullWidth
                  label="Email"
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
                  label="Tên hiển thị"
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
                  label="Họ"
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
                  label="Tên"
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
                  label="Số điện thoại"
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
                  label="Ngày sinh"
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
                              color: 'rgba(255, 255, 255, 0.4)',
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
                  label="Thành phố"
                  name="city"
                  value={isEditing ? formData.city : maskCity(formData.city)}
                  disabled={!isEditing}
                  onChange={handleChange}
                />
              </Grid>
            </Grid>
          </Paper>

          <Typography variant="h6" sx={{ fontWeight: 'bold', mt: 4, mb: 2 }}>Hoạt động gần đây</Typography>
          <Grid container spacing={2}>
            {recentActivities.notifications.length === 0 && recentActivities.conversations.length === 0 && (
              <Grid size={{ xs: 12 }}>
                <Typography variant="body2" color="text.secondary">Không có hoạt động gần đây</Typography>
              </Grid>
            )}
            
            {recentActivities.notifications.map((notif) => (
              <Grid size={{ xs: 12 }} key={notif.id}>
                <Card variant="outlined" sx={{ borderRadius: 3 }}>
                  <CardContent sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                    <CalendarMonth color="primary" />
                    <Box>
                      <Typography variant="body1" sx={{ fontWeight: 'medium' }}>
                        {notif.message}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {new Date(notif.createdAt).toLocaleString()}
                      </Typography>
                    </Box>
                  </CardContent>
                </Card>
              </Grid>
            ))}

            {recentActivities.conversations.map((conv) => (
              <Grid size={{ xs: 12 }} key={conv.id}>
                <Card variant="outlined" sx={{ borderRadius: 3 }}>
                  <CardContent sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                    <Message color="secondary" />
                    <Box>
                      <Typography variant="body1" sx={{ fontWeight: 'medium' }}>
                        Trò chuyện mới trong {(conv as any).conversationName || 'cuộc hội thoại'}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {conv.modifiedDate ? new Date(conv.modifiedDate).toLocaleString() : ''}
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
