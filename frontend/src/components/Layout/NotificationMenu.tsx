import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Alert,
  Avatar,
  Badge,
  Box,
  Button,
  Divider,
  Fade,
  IconButton,
  ListItemAvatar,
  ListItemText,
  Paper,
  Popover,
  Popper,
  Skeleton,
  Tooltip,
  Typography,
} from '@mui/material';
import {
  ArrowForward,
  Close,
  DoneAll,
  Notifications as NotificationsIcon,
} from '@mui/icons-material';
import { notificationService } from '../../api/notificationService';
import type { NotificationResponse } from '../../models';
import { REALTIME_NOTIFICATION_EVENT } from '../../contexts/WebSocketContext';
import { useTranslation } from 'react-i18next';
import { formatRelativeTime } from '../../utils/time';

const PREVIEW_SIZE = 6;
const FLYOUT_AUTO_HIDE_MS = 6000;

interface RealtimeNotificationDetail {
  type?: string;
  title?: string;
  content?: string;
}

interface NotificationMenuProps {
  unreadCount: number;
  active?: boolean;
  onMarkedAllRead?: () => void;
}

const NotificationMenu: React.FC<NotificationMenuProps> = ({
  unreadCount,
  active = false,
  onMarkedAllRead,
}) => {
  const navigate = useNavigate();
  const { t, i18n } = useTranslation();
  const flyoutTimerRef = useRef<number | undefined>(undefined);
  const [iconEl, setIconEl] = useState<HTMLButtonElement | null>(null);
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
  const [notifications, setNotifications] = useState<NotificationResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [markingAllRead, setMarkingAllRead] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [flyout, setFlyout] = useState<RealtimeNotificationDetail | null>(null);
  const open = Boolean(anchorEl);
  const flyoutOpen = Boolean(flyout);

  const fetchNotifications = useCallback(async () => {
    setLoading(true);
    setError(null);

    try {
      const response = await notificationService.getMyNotifications(1, PREVIEW_SIZE);
      setNotifications(response.result?.data ?? []);
    } catch (fetchError) {
      console.error('Failed to fetch notification preview:', fetchError);
      setError(t('notificationsLoadFailed'));
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => {
    if (open) {
      void fetchNotifications();
    }
  }, [fetchNotifications, open]);

  const dismissFlyout = useCallback(() => {
    if (flyoutTimerRef.current !== undefined) {
      window.clearTimeout(flyoutTimerRef.current);
      flyoutTimerRef.current = undefined;
    }
    setFlyout(null);
  }, []);

  useEffect(() => {
    const handleRealtimeNotification = (event: Event) => {
      if (open) {
        void fetchNotifications();
      }

      const detail = (event as CustomEvent<RealtimeNotificationDetail>).detail;
      if (detail && (detail.content || detail.title)) {
        setFlyout(detail);
        if (flyoutTimerRef.current !== undefined) {
          window.clearTimeout(flyoutTimerRef.current);
        }
        flyoutTimerRef.current = window.setTimeout(() => {
          setFlyout(null);
        }, FLYOUT_AUTO_HIDE_MS);
      }
    };

    window.addEventListener(REALTIME_NOTIFICATION_EVENT, handleRealtimeNotification);
    return () => {
      window.removeEventListener(REALTIME_NOTIFICATION_EVENT, handleRealtimeNotification);
    };
  }, [fetchNotifications, open]);

  useEffect(() => () => {
    if (flyoutTimerRef.current !== undefined) {
      window.clearTimeout(flyoutTimerRef.current);
    }
  }, []);

  const handleToggle = (event: React.MouseEvent<HTMLElement>) => {
    dismissFlyout();
    setAnchorEl((currentAnchor) => currentAnchor ? null : event.currentTarget);
  };

  const handleClose = () => {
    setAnchorEl(null);
  };

  const handleViewAll = () => {
    handleClose();
    navigate('/social/notifications');
  };

  const handleFlyoutClick = () => {
    dismissFlyout();
    navigate('/social/notifications');
  };

  const handleMarkAllRead = async () => {
    if (unreadCount === 0 || markingAllRead) return;

    setMarkingAllRead(true);
    setError(null);

    try {
      await notificationService.markAllAsRead();
      setNotifications((currentNotifications) =>
        currentNotifications.map((notification) => ({ ...notification, read: true })),
      );
      onMarkedAllRead?.();
      window.dispatchEvent(new Event('sidebar-counts:refresh'));
      window.dispatchEvent(new Event(REALTIME_NOTIFICATION_EVENT));
    } catch (markError) {
      console.error('Failed to mark notifications as read:', markError);
      setError(t('notificationsUpdateFailed'));
    } finally {
      setMarkingAllRead(false);
    }
  };

  return (
    <>
      <Tooltip title={t('notifications')}>
        <IconButton
          ref={setIconEl}
          onClick={handleToggle}
          aria-label={t('notifications')}
          aria-haspopup="dialog"
          aria-expanded={open}
          sx={{
            width: 44,
            height: 44,
            borderRadius: '12px',
            color: active || open ? 'primary.main' : 'text.secondary',
            bgcolor: active || open ? 'primary.50' : (theme) =>
              theme.palette.mode === 'dark' ? 'rgba(255,255,255,0.05)' : 'rgba(0,0,0,0.03)',
            border: '1px solid',
            borderColor: active || open ? 'primary.main' : 'divider',
            transition: 'all 0.2s ease',
            '&:hover': {
              backgroundColor: 'primary.50',
              color: 'primary.main',
              borderColor: 'primary.main',
            },
          }}
        >
          <Badge
            badgeContent={unreadCount}
            color="error"
            max={99}
            overlap="circular"
            invisible={unreadCount === 0}
          >
            <NotificationsIcon fontSize="medium" />
          </Badge>
        </IconButton>
      </Tooltip>

      <Popover
        open={open}
        anchorEl={anchorEl}
        onClose={handleClose}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        transformOrigin={{ vertical: 'top', horizontal: 'right' }}
        slotProps={{
          paper: {
            elevation: 12,
            sx: {
              mt: 1,
              width: { xs: 'calc(100vw - 24px)', sm: 420 },
              maxWidth: 'calc(100vw - 24px)',
              maxHeight: 'min(70vh, 580px)',
              borderRadius: 3,
              overflow: 'hidden',
              border: '1px solid',
              borderColor: 'divider',
            },
          },
        }}
      >
        <Box
          sx={{
            px: 2,
            py: 1.5,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 1,
          }}
        >
          <Typography variant="h6" sx={{ fontWeight: 800 }}>
            {t('notifications')}
          </Typography>
          {unreadCount > 0 && (
            <Button
              size="small"
              startIcon={<DoneAll />}
              disabled={markingAllRead}
              onClick={() => void handleMarkAllRead()}
              sx={{ textTransform: 'none', whiteSpace: 'nowrap' }}
            >
              {t('markAllRead')}
            </Button>
          )}
        </Box>

        <Divider />

        <Box sx={{ overflowY: 'auto', maxHeight: 'min(52vh, 430px)' }}>
          {error && (
            <Alert
              severity="error"
              action={(
                <Button color="inherit" size="small" onClick={() => void fetchNotifications()}>
                  {t('retry')}
                </Button>
              )}
              sx={{ m: 1.5 }}
            >
              {error}
            </Alert>
          )}

          {loading ? (
            <Box sx={{ px: 2, py: 1 }}>
              {Array.from({ length: 4 }).map((_, index) => (
                <Box key={index} sx={{ display: 'flex', alignItems: 'center', gap: 1.5, py: 1 }}>
                  <Skeleton variant="circular" width={48} height={48} />
                  <Box sx={{ flex: 1 }}>
                    <Skeleton width="92%" />
                    <Skeleton width="38%" />
                  </Box>
                </Box>
              ))}
            </Box>
          ) : !error && notifications.length === 0 ? (
            <Box sx={{ px: 3, py: 5, textAlign: 'center' }}>
              <NotificationsIcon sx={{ fontSize: 48, color: 'text.disabled', mb: 1 }} />
              <Typography color="text.secondary">
                {t('noNotificationsYet')}
              </Typography>
            </Box>
          ) : !loading && notifications.map((notification) => (
            <Box
              key={notification.id}
              sx={{
                px: 2,
                py: 1.25,
                display: 'flex',
                alignItems: 'center',
                backgroundColor: notification.read ? 'transparent' : 'action.hover',
                borderBottom: '1px solid',
                borderBottomColor: 'divider',
              }}
            >
              <ListItemAvatar sx={{ minWidth: 60 }}>
                <Badge
                  color="primary"
                  variant="dot"
                  overlap="circular"
                  invisible={notification.read}
                  anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
                >
                  <Avatar src={notification.avatarSender}>
                    {(notification.displayNameSender || notification.message || 'N').charAt(0).toUpperCase()}
                  </Avatar>
                </Badge>
              </ListItemAvatar>
              <ListItemText
                primary={notification.message}
                secondary={formatRelativeTime(notification.createdAt, i18n.language)}
                slotProps={{
                  primary: {
                    variant: 'body2',
                    sx: {
                      fontWeight: notification.read ? 400 : 700,
                      display: '-webkit-box',
                      WebkitBoxOrient: 'vertical',
                      WebkitLineClamp: 2,
                      overflow: 'hidden',
                    },
                  },
                  secondary: {
                    variant: 'caption',
                    sx: {
                      color: notification.read ? 'text.secondary' : 'primary.main',
                      mt: 0.25,
                    },
                  },
                }}
              />
            </Box>
          ))}
        </Box>

        <Divider />

        <Box sx={{ p: 1 }}>
          <Button
            fullWidth
            endIcon={<ArrowForward />}
            onClick={handleViewAll}
            sx={{ py: 1, textTransform: 'none', fontWeight: 700, borderRadius: 2 }}
          >
            {t('viewAllNotifications')}
          </Button>
        </Box>
      </Popover>

      <Popper
        open={flyoutOpen}
        anchorEl={iconEl}
        placement="bottom-end"
        transition
        disablePortal={false}
        sx={{ zIndex: (theme) => theme.zIndex.snackbar }}
        modifiers={[{ name: 'offset', options: { offset: [0, 8] } }]}
      >
        {({ TransitionProps }) => (
          <Fade {...TransitionProps} timeout={200}>
            <Paper
              elevation={12}
              onClick={handleFlyoutClick}
              sx={{
                width: { xs: 'calc(100vw - 24px)', sm: 340 },
                maxWidth: 'calc(100vw - 24px)',
                borderRadius: 3,
                p: 1.5,
                display: 'flex',
                alignItems: 'flex-start',
                gap: 1.5,
                cursor: 'pointer',
                border: '1px solid',
                borderColor: 'divider',
                '&:hover': { backgroundColor: 'action.hover' },
              }}
            >
              <NotificationsIcon color="primary" sx={{ mt: 0.25 }} />
              <Box sx={{ flex: 1, minWidth: 0 }}>
                {flyout?.title && (
                  <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>
                    {flyout.title}
                  </Typography>
                )}
                {(flyout?.content || !flyout?.title) && (
                  <Typography
                    variant="body2"
                    color="text.secondary"
                    sx={{
                      display: '-webkit-box',
                      WebkitBoxOrient: 'vertical',
                      WebkitLineClamp: 2,
                      overflow: 'hidden',
                    }}
                  >
                    {flyout?.content || flyout?.title}
                  </Typography>
                )}
              </Box>
              <IconButton
                size="small"
                onClick={(event) => {
                  event.stopPropagation();
                  dismissFlyout();
                }}
                sx={{ mt: -0.5, mr: -0.5 }}
              >
                <Close fontSize="small" />
              </IconButton>
            </Paper>
          </Fade>
        )}
      </Popper>
    </>
  );
};

export default NotificationMenu;
