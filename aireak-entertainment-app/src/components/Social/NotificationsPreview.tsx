import React from 'react';
import { Avatar, Badge, Box, Button, List, ListItemAvatar, ListItemText, Paper, Skeleton, Typography } from '@mui/material';
import { Notifications, DoneAll } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import type { NotificationResponse } from '../../models';
import { formatRelativeTime } from '../../utils/time';

interface NotificationsPreviewProps {
  notifications: NotificationResponse[];
  unreadCount: number;
  loading: boolean;
  onMarkAllRead: () => void;
}

const NotificationsPreview: React.FC<NotificationsPreviewProps> = React.memo(
  ({ notifications, unreadCount, loading, onMarkAllRead }) => {
    const { t, i18n } = useTranslation();

    return (
      <Paper
        elevation={0}
        sx={{ p: 3, borderRadius: 3, bgcolor: '#141414', border: '1px solid rgba(255, 255, 255, 0.06)', height: '100%' }}
      >
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <Badge badgeContent={unreadCount} color="error" max={9}>
              <Notifications color="primary" fontSize="small" />
            </Badge>
            <Typography variant="subtitle1" sx={{ fontWeight: 700, ml: 0.5 }}>
              {t('notifications')}
            </Typography>
          </Box>
          {unreadCount > 0 && (
            <Button size="small" onClick={onMarkAllRead} startIcon={<DoneAll fontSize="small" />}>
              {t('markAllRead')}
            </Button>
          )}
        </Box>

        {loading ? (
          <Box sx={{ py: 1 }}>
            {[0, 1, 2].map((i) => (
              <Box key={i} sx={{ display: 'flex', alignItems: 'center', gap: 2, py: 1 }}>
                <Skeleton variant="circular" width={36} height={36} />
                <Skeleton variant="text" width="70%" />
              </Box>
            ))}
          </Box>
        ) : notifications.length === 0 ? (
          <Typography variant="body2" color="text.secondary" sx={{ py: 3, textAlign: 'center' }}>
            {t('noNotificationsYet')}
          </Typography>
        ) : (
          <List sx={{ p: 0 }}>
            {notifications.map((notif) => (
              <Box
                key={notif.id}
                sx={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 1.5,
                  py: 1,
                  px: 1,
                  borderRadius: 2,
                  bgcolor: notif.read ? 'transparent' : 'rgba(0, 168, 78, 0.06)',
                }}
              >
                <ListItemAvatar sx={{ minWidth: 44 }}>
                  <Avatar src={notif.avatarSender} sx={{ width: 36, height: 36 }}>
                    {notif.displayNameSender?.[0]?.toUpperCase()}
                  </Avatar>
                </ListItemAvatar>
                <ListItemText
                  primary={
                    <Typography variant="body2" sx={{ fontWeight: notif.read ? 400 : 600 }}>
                      {notif.message}
                    </Typography>
                  }
                  secondary={
                    <Typography variant="caption" color="text.secondary" component="span">
                      {formatRelativeTime(notif.createdAt, i18n.language)}
                    </Typography>
                  }
                />
              </Box>
            ))}
          </List>
        )}
      </Paper>
    );
  }
);

NotificationsPreview.displayName = 'NotificationsPreview';

export default NotificationsPreview;
