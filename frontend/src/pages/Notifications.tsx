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
import { alpha, useTheme } from '@mui/material/styles';
import { DoneAll, Notifications as NotificationsIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';

import { notificationService } from '../api/notificationService';
import { REALTIME_NOTIFICATION_EVENT } from '../contexts/WebSocketContext';
import type { NotificationResponse, PageResponse } from '../models';
import { getNotificationLink } from '../utils/notificationLink';
import { formatRelativeTime } from '../utils/time';
import { EmptyState, PageHero } from '../components/common/VibeUi';
import { VIBE_GRADIENT, VIBE_LOVE_GRADIENT, VIBE_TEAL, vibeBorder, vibeSurfaceHover } from '../styles/vibe';

const PAGE_SIZE = 12;

const NotificationsPage: React.FC = () => {
  const { t, i18n } = useTranslation();
  const theme = useTheme();
  const navigate = useNavigate();
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
      setError(t('notificationsLoadFailed'));
    }

    if (unreadResult.status === 'fulfilled') {
      setUnreadCount(unreadResult.value.result ?? 0);
    }

    setLoading(false);
  }, [page, t]);

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
      toast.success(t('notificationsMarkedRead'));
    } catch (markError) {
      console.error('Failed to mark notifications as read:', markError);
      toast.error(t('notificationsUpdateFailed'));
    }
  };

  return (
    <Container maxWidth="lg" sx={{ pb: { xs: 3, md: 6 } }}>
      <PageHero
        icon={
          <Badge
            badgeContent={unreadCount}
            max={99}
            sx={{
              '& .MuiBadge-badge': {
                background: VIBE_LOVE_GRADIENT,
                color: '#fff',
                fontWeight: 900,
                border: '2px solid',
                borderColor: 'background.paper',
              },
            }}
          >
            <NotificationsIcon />
          </Badge>
        }
        title={t('notifications')}
        subtitle={unreadCount > 0 ? t('unreadNotifications', { count: unreadCount }) : t('allNotificationsRead')}
        actions={
          <Button
            variant="outlined"
            startIcon={<DoneAll />}
            onClick={handleMarkAllRead}
            disabled={unreadCount === 0}
            sx={{ borderRadius: '999px', px: 3 }}
          >
            {t('markAllRead')}
          </Button>
        }
      />

      {error && <Alert severity="error" sx={{ mb: 3 }}>{error}</Alert>}

      <Paper elevation={0} sx={{ overflow: 'hidden', borderRadius: '24px', bgcolor: 'background.paper', border: '1px solid', borderColor: vibeBorder(theme) }}>
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
          <EmptyState icon={<NotificationsIcon />} title={t('noNotificationsYet')} sx={{ border: 'none', borderRadius: 0 }} />
        ) : (
          pageData.data.map((notification, index) => {
            const link = getNotificationLink(notification);
            return (
              <Box
                key={notification.id}
                onClick={link ? () => navigate(link) : undefined}
                sx={{
                  position: 'relative',
                  display: 'flex',
                  gap: 2,
                  alignItems: 'center',
                  p: { xs: 2, md: 2.5 },
                  bgcolor: notification.read ? 'transparent' : alpha(VIBE_TEAL, 0.07),
                  borderBottom: index < pageData.data.length - 1 ? '1px solid' : 'none',
                  borderBottomColor: vibeBorder(theme),
                  transition: 'background-color 0.2s ease',
                  ...(!notification.read && {
                    '&::before': {
                      content: '""',
                      position: 'absolute',
                      left: 0,
                      top: 14,
                      bottom: 14,
                      width: 4,
                      borderRadius: '0 4px 4px 0',
                      background: VIBE_GRADIENT,
                    },
                  }),
                  ...(link && { cursor: 'pointer', '&:hover': { bgcolor: vibeSurfaceHover(theme) } }),
                }}
              >
                <Badge color="primary" variant="dot" invisible={notification.read} overlap="circular">
                  <Box sx={{ p: '2px', borderRadius: '50%', display: 'inline-flex', background: notification.read ? 'transparent' : VIBE_GRADIENT }}>
                    <Avatar
                      src={notification.avatarSender}
                      sx={{
                        width: 48,
                        height: 48,
                        fontWeight: 800,
                        color: '#fff',
                        background: VIBE_GRADIENT,
                        border: '2px solid',
                        borderColor: 'background.paper',
                      }}
                    >
                      {notification.displayNameSender?.[0]?.toUpperCase() || 'A'}
                    </Avatar>
                  </Box>
                </Badge>
                <Box sx={{ minWidth: 0, flex: 1 }}>
                  <Typography sx={{ fontWeight: notification.read ? 500 : 800, mb: 0.5 }}>
                    {notification.message}
                  </Typography>
                  <Typography variant="caption" sx={{ fontWeight: 700, color: notification.read ? 'text.secondary' : VIBE_TEAL }}>
                    {formatRelativeTime(notification.createdAt, i18n.language)}
                  </Typography>
                </Box>
              </Box>
            );
          })
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
