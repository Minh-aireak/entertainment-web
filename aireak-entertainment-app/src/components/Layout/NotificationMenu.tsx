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

const formatRelativeTime = (dateString: string) => {
  const createdAt = new Date(dateString).getTime();
  const diffSeconds = Math.max(0, Math.floor((Date.now() - createdAt) / 1000));

  if (diffSeconds < 60) return 'V\u1eeba xong';
  if (diffSeconds < 3600) return `${Math.floor(diffSeconds / 60)} ph\u00fat tr\u01b0\u1edbc`;
  if (diffSeconds < 86400) return `${Math.floor(diffSeconds / 3600)} gi\u1edd tr\u01b0\u1edbc`;
  if (diffSeconds < 604800) return `${Math.floor(diffSeconds / 86400)} ng\u00e0y tr\u01b0\u1edbc`;

  return new Date(dateString).toLocaleDateString('vi-VN');
};

const NotificationMenu: React.FC<NotificationMenuProps> = ({
  unreadCount,
  active = false,
  onMarkedAllRead,
}) => {
  const navigate = useNavigate();
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
      setError('Kh\u00f4ng th\u1ec3 t\u1ea3i th\u00f4ng b\u00e1o.');
    } finally {
      setLoading(false);
    }
  }, []);

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
      setError('Kh\u00f4ng th\u1ec3 \u0111\u00e1nh d\u1ea5u th\u00f4ng b\u00e1o \u0111\u00e3 \u0111\u1ecdc.');
    } finally {
      setMarkingAllRead(false);
    }
  };

  return (
    <>
      <Tooltip title={'Th\u00f4ng b\u00e1o'}>
        <IconButton
          ref={setIconEl}
          onClick={handleToggle}
          aria-label={'Th\u00f4ng b\u00e1o'}
          aria-haspopup="dialog"
          aria-expanded={open}
          sx={{
            color: active || open ? 'primary.main' : 'text.secondary',
            backgroundColor: open ? 'primary.50' : 'transparent',
            '&:hover': {
              backgroundColor: 'primary.50',
              color: 'primary.main',
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
            {'Th\u00f4ng b\u00e1o'}
          </Typography>
          {unreadCount > 0 && (
            <Button
              size="small"
              startIcon={<DoneAll />}
              disabled={markingAllRead}
              onClick={() => void handleMarkAllRead()}
              sx={{ textTransform: 'none', whiteSpace: 'nowrap' }}
            >
              {'\u0110\u00e1nh d\u1ea5u \u0111\u00e3 \u0111\u1ecdc'}
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
                  {'Th\u1eed l\u1ea1i'}
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
                {'B\u1ea1n ch\u01b0a c\u00f3 th\u00f4ng b\u00e1o n\u00e0o'}
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
                secondary={formatRelativeTime(notification.createdAt)}
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
            {'Xem t\u1ea5t c\u1ea3 th\u00f4ng b\u00e1o'}
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
