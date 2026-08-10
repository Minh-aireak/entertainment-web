import React, { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Avatar,
  Badge,
  Box,
  Button,
  Container,
  Pagination,
  Paper,
  Skeleton,
  Typography,
} from '@mui/material';
import { DoneAll, Notifications as NotificationsIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';

import { notificationService } from '../api/notificationService';
import { REALTIME_NOTIFICATION_EVENT } from '../contexts/WebSocketContext';
import type { NotificationResponse, PageResponse } from '../models';
import { formatRelativeTime } from '../utils/time';

const PAGE_SIZE = 12;

const NotificationsPage: React.FC = () => {
  const { t, i18n } = useTranslation();
  const [page, setPage] = useState(1);
  const [pageData, setPageData] = useState<PageResponse<NotificationResponse> | null>(null);
  const [unreadCount, setUnreadCount] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchNotifications = useCallback(async () => {
    setLoading(true);
    setError(null);

    const [notificationsResult, unreadResult] = await Promise.allSettled([
      notificationService.getMyNotifications(page, PAGE_SIZE),
      notificationService.getUnreadCount(),
    ]);

    if (notificationsResult.status === 'fulfilled') {
      setPageData(notificationsResult.value.result);
    } else {
      setError('Không thể tải thông báo. Vui lòng thử lại.');
    }

    if (unreadResult.status === 'fulfilled') {
      setUnreadCount(unreadResult.value.result ?? 0);
    }

    setLoading(false);
  }, [page]);

  useEffect(() => {
    fetchNotifications();
  }, [fetchNotifications]);

  useEffect(() => {
    const refreshNotifications = () => fetchNotifications();
    window.addEventListener(REALTIME_NOTIFICATION_EVENT, refreshNotifications);
    return () => window.removeEventListener(REALTIME_NOTIFICATION_EVENT, refreshNotifications);
  }, [fetchNotifications]);

  const handleMarkAllRead = async () => {
    try {
      await notificationService.markAllAsRead();
      setUnreadCount(0);
      setPageData((current) => current ? {
        ...current,
        data: current.data.map((notification) => ({ ...notification, read: true })),
      } : current);
      window.dispatchEvent(new Event('sidebar-counts:refresh'));
      toast.success('Đã đánh dấu tất cả thông báo là đã đọc');
    } catch (markError) {
      console.error('Failed to mark notifications as read:', markError);
      toast.error('Không thể cập nhật thông báo');
    }
  };

  return (
    <Container maxWidth="lg" sx={{ py: { xs: 3, md: 6 } }}>
      <Box sx={{ display: 'flex', alignItems: { xs: 'flex-start', sm: 'center' }, justifyContent: 'space-between', gap: 2, mb: 4, flexDirection: { xs: 'column', sm: 'row' } }}>
        <Box>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 0.75 }}>
            <Badge badgeContent={unreadCount} color="error" max={99}>
              <NotificationsIcon color="primary" sx={{ fontSize: 34 }} />
            </Badge>
            <Typography variant="h3" sx={{ fontWeight: 900, fontSize: { xs: '2rem', md: '3rem' } }}>
              {t('notifications')}
            </Typography>
          </Box>
          <Typography color="text.secondary">
            {unreadCount > 0 ? `${unreadCount} thông báo chưa đọc` : 'Bạn đã xem tất cả thông báo'}
          </Typography>
        </Box>

        <Button
          variant="outlined"
          startIcon={<DoneAll />}
          onClick={handleMarkAllRead}
          disabled={unreadCount === 0}
          sx={{ borderRadius: 3, px: 3 }}
        >
          {t('markAllRead')}
        </Button>
      </Box>

      {error && <Alert severity="error" sx={{ mb: 3 }}>{error}</Alert>}

      <Paper elevation={0} sx={{ overflow: 'hidden', borderRadius: 4, bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider' }}>
        {loading ? (
          <Box sx={{ p: 2 }}>
            {Array.from({ length: 6 }).map((_, index) => (
              <Box key={index} sx={{ display: 'flex', gap: 2, alignItems: 'center', p: 2 }}>
                <Skeleton variant="circular" width={48} height={48} />
                <Box sx={{ flex: 1 }}>
                  <Skeleton width="70%" />
                  <Skeleton width="35%" />
                </Box>
              </Box>
            ))}
          </Box>
        ) : !pageData?.data.length ? (
          <Box sx={{ py: 12, px: 3, textAlign: 'center' }}>
            <NotificationsIcon sx={{ fontSize: 72, color: 'text.disabled', mb: 2 }} />
            <Typography variant="h6" sx={{ fontWeight: 700 }}>{t('noNotificationsYet')}</Typography>
          </Box>
        ) : (
          pageData.data.map((notification, index) => (
            <Box
              key={notification.id}
              sx={{
                display: 'flex',
                gap: 2,
                alignItems: 'center',
                p: { xs: 2, md: 2.5 },
                bgcolor: notification.read ? 'transparent' : 'rgba(0,168,78,0.07)',
                borderBottom: index < pageData.data.length - 1 ? '1px solid' : 'none',
                borderBottomColor: 'divider',
              }}
            >
              <Badge color="primary" variant="dot" invisible={notification.read} overlap="circular">
                <Avatar src={notification.avatarSender} sx={{ width: 48, height: 48 }}>
                  {notification.displayNameSender?.[0]?.toUpperCase() || 'A'}
                </Avatar>
              </Badge>
              <Box sx={{ minWidth: 0, flex: 1 }}>
                <Typography sx={{ fontWeight: notification.read ? 500 : 750, mb: 0.5 }}>
                  {notification.message}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  {formatRelativeTime(notification.createdAt, i18n.language)}
                </Typography>
              </Box>
            </Box>
          ))
        )}
      </Paper>

      {(pageData?.totalPages ?? 0) > 1 && (
        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 4 }}>
          <Pagination count={pageData?.totalPages ?? 1} page={page} onChange={(_, value) => setPage(value)} color="primary" />
        </Box>
      )}
    </Container>
  );
};

export default NotificationsPage;
