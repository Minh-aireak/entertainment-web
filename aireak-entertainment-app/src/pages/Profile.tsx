import React, { useState, useEffect, useCallback } from 'react';
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
  Card,
  CardContent,
  CircularProgress,
  InputAdornment,
} from '@mui/material';
import { Edit, CameraAlt, CalendarMonth, Message } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { toast } from 'react-hot-toast';
import { profileService } from '../api/profileService';
import { notificationService } from '../api/notificationService';
import { chatService } from '../api/chatService';
import type { UserFullSummaryResponse, NotificationResponse, ConversationResponse } from '../models';

const ProfilePage: React.FC = () => {
  const { t } = useTranslation();
  
  const [isEditing, setIsEditing] = useState(false);
  const [loading, setLoading] = useState(false);
  const [profileData, setProfileData] = useState<UserFullSummaryResponse | null>(null);
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
    dob: '',
    phoneNumber: '',
    city: '',
    avatar: '',
  });

  const fetchProfileData = useCallback(async () => {
    setLoading(true);
    try {
      const [summaryRes, notificationsRes, conversationsRes] = await Promise.all([
        profileService.getUserSummary(),
        notificationService.getMyNotifications(1, 3),
        chatService.getMyConversations(1, 3),
      ]);

      if (summaryRes.code === 1000) {
        const profile = summaryRes.result;
        setProfileData(profile);
        setFormData({
          username: profile.username || '',
          email: profile.email || '',
          displayName: profile.displayName || '',
          dob: profile.dob || '',
          phoneNumber: profile.phoneNumber || '',
          city: profile.city || '',
          avatar: profile.avatar || '',
        });
      }

      if (notificationsRes.code === 1000) {
        setRecentActivities(prev => ({ ...prev, notifications: notificationsRes.result.data }));
      }

      if (conversationsRes.code === 1000) {
        setRecentActivities(prev => ({ ...prev, conversations: conversationsRes.result.data }));
      }

    } catch (error) {
      console.error('Failed to fetch profile data:', error);
      toast.error('Không thể tải thông tin cá nhân');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchProfileData();
  }, [fetchProfileData]);

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

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
  };

  const handleSave = async () => {
    try {
      const response = await profileService.updateProfile({
        displayName: formData.displayName,
        city: formData.city,
        dob: formData.dob,
        firstName: '', // Cần thiết nếu API yêu cầu
        lastName: '', // Cần thiết nếu API yêu cầu
        email: formData.email,
        phoneNumber: formData.phoneNumber,
      });
      if (response.code === 1000) {
        toast.success('Cập nhật thông tin thành công!');
        setIsEditing(false);
        fetchProfileData();
      }
    } catch (error) {
      console.error('Failed to update profile:', error);
      toast.error('Cập nhật thất bại');
    }
  };

  if (loading && !profileData) {
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
              <Avatar 
                src={formData.avatar}
                sx={{ width: 150, height: 150, fontSize: 60, bgcolor: 'primary.main' }}
              >
                {formData.displayName?.[0]?.toUpperCase() || formData.username?.[0]?.toUpperCase()}
              </Avatar>
              <IconButton 
                sx={{ 
                  position: 'absolute', 
                  bottom: 0, 
                  right: 0, 
                  bgcolor: 'white',
                  boxShadow: 2,
                  '&:hover': { bgcolor: '#f0f0f0' }
                }}
              >
                <CameraAlt color="primary" />
              </IconButton>
            </Box>
            <Typography variant="h5" sx={{ fontWeight: 'bold' }}>{formData.displayName}</Typography>
            <Typography variant="body1" color="text.secondary" sx={{ mb: 3 }}>@{formData.username}</Typography>
            <Divider sx={{ mb: 3 }} />
            <Grid container spacing={2}>
              <Grid size={{ xs: 6 }}>
                <Typography variant="h6" sx={{ fontWeight: 'bold' }}>
                  {profileData?.totalPost ?? '-'}
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
                />
              </Grid>
              <Grid size={{ xs: 12, sm: 6 }}>
                <TextField
                  fullWidth
                  label="Họ và tên"
                  name="displayName"
                  value={formData.displayName}
                  disabled={!isEditing}
                  onChange={handleChange}
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
                  value={formData.city}
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
                        Trò chuyện mới trong {conv.type === 'GROUP' ? (conv as any).groupName : 'cuộc hội thoại'}
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
};

export default ProfilePage;
