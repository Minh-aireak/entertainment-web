import React from 'react';
import { Avatar, Box, Button, Paper, Skeleton, Typography } from '@mui/material';
import { People, ChevronRight } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import type { UserRelationshipResponse } from '../../models';

interface FriendsPreviewCardProps {
  friends: UserRelationshipResponse[];
  totalFriends: number;
  loading: boolean;
}

const FriendsPreviewCard: React.FC<FriendsPreviewCardProps> = React.memo(({ friends, totalFriends, loading }) => {
  const { t } = useTranslation();
  const navigate = useNavigate();

  return (
    <Paper
      elevation={0}
      sx={{ p: 3, borderRadius: 3, bgcolor: '#141414', border: '1px solid rgba(255, 255, 255, 0.06)', height: '100%' }}
    >
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <People color="primary" fontSize="small" />
          <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
            {t('myFriendsTitle')} {totalFriends > 0 && `(${totalFriends})`}
          </Typography>
        </Box>
        <Button size="small" onClick={() => navigate('/social/friends')} endIcon={<ChevronRight fontSize="small" />}>
          {t('seeAllFriends')}
        </Button>
      </Box>

      {loading ? (
        <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
          {[0, 1, 2, 3, 4, 5].map((i) => (
            <Skeleton key={i} variant="circular" width={56} height={56} />
          ))}
        </Box>
      ) : friends.length === 0 ? (
        <Box sx={{ py: 3, textAlign: 'center' }}>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            {t('noFriendsYet')}
          </Typography>
          <Button variant="outlined" size="small" onClick={() => navigate('/social/friends')}>
            {t('findFriends')}
          </Button>
        </Box>
      ) : (
        <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
          {friends.map((friend) => (
            <Box
              key={friend.friendId}
              sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', width: 64, cursor: 'pointer' }}
              onClick={() => navigate('/social/friends')}
            >
              <Avatar src={friend.friendAvatar} sx={{ width: 56, height: 56, mb: 0.5 }}>
                {friend.displayName?.[0]?.toUpperCase()}
              </Avatar>
              <Typography variant="caption" noWrap sx={{ width: '100%', textAlign: 'center' }}>
                {friend.displayName}
              </Typography>
            </Box>
          ))}
        </Box>
      )}
    </Paper>
  );
});

FriendsPreviewCard.displayName = 'FriendsPreviewCard';

export default FriendsPreviewCard;
