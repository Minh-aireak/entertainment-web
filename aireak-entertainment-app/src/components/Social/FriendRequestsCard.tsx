import React from 'react';
import { Avatar, Box, Button, IconButton, Paper, Skeleton, Stack, Typography } from '@mui/material';
import { Check, Close, PersonAddAlt, ChevronRight } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import type { FriendRequestResponse } from '../../models';

interface FriendRequestsCardProps {
  requests: FriendRequestResponse[];
  loading: boolean;
  onAccept: (senderId: string) => void;
  onDecline: (senderId: string) => void;
}

const FriendRequestsCard: React.FC<FriendRequestsCardProps> = React.memo(
  ({ requests, loading, onAccept, onDecline }) => {
    const { t } = useTranslation();
    const navigate = useNavigate();

    return (
      <Paper
        elevation={0}
        sx={{ p: 3, borderRadius: 3, bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider', height: '100%' }}
      >
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <PersonAddAlt color="primary" fontSize="small" />
            <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
              {t('friendRequestsTitle')}
            </Typography>
          </Box>
          <Button size="small" onClick={() => navigate('/social/friends')} endIcon={<ChevronRight fontSize="small" />}>
            {t('seeAllFriends')}
          </Button>
        </Box>

        {loading ? (
          <Box sx={{ py: 1 }}>
            {[0, 1].map((i) => (
              <Box key={i} sx={{ display: 'flex', alignItems: 'center', gap: 2, py: 1 }}>
                <Skeleton variant="circular" width={40} height={40} />
                <Skeleton variant="text" width="50%" />
              </Box>
            ))}
          </Box>
        ) : requests.length === 0 ? (
          <Typography variant="body2" color="text.secondary" sx={{ py: 3, textAlign: 'center' }}>
            {t('noFriendRequests')}
          </Typography>
        ) : (
          <Stack spacing={1}>
            {requests.map((req) => (
              <Box key={req.senderId} sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                <Avatar src={req.avatar} slotProps={{ img: { loading: 'lazy' } }} sx={{ width: 40, height: 40 }}>
                  {req.displayName?.[0]?.toUpperCase()}
                </Avatar>
                <Typography variant="body2" noWrap sx={{ flex: 1, fontWeight: 600 }}>
                  {req.displayName}
                </Typography>
                <IconButton
                  size="small"
                  onClick={() => onAccept(req.senderId)}
                  sx={{ bgcolor: 'primary.main', color: 'white', '&:hover': { bgcolor: 'primary.dark' } }}
                >
                  <Check fontSize="small" />
                </IconButton>
                <IconButton
                  size="small"
                  onClick={() => onDecline(req.senderId)}
                  sx={{ bgcolor: 'action.hover', '&:hover': { bgcolor: 'action.selected' } }}
                >
                  <Close fontSize="small" />
                </IconButton>
              </Box>
            ))}
          </Stack>
        )}
      </Paper>
    );
  }
);

FriendRequestsCard.displayName = 'FriendRequestsCard';

export default FriendRequestsCard;
