import React from 'react';
import { Avatar, Box, Button, Paper, Skeleton, Typography } from '@mui/material';
import { ChevronRight } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import type { UserFullSummaryResponse } from '../../models';

interface ProfileSummaryCardProps {
  summary: UserFullSummaryResponse | null;
  loading: boolean;
}

const ProfileSummaryCard: React.FC<ProfileSummaryCardProps> = React.memo(({ summary, loading }) => {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();

  const joinDate = summary?.joinDate
    ? new Date(summary.joinDate).toLocaleDateString(i18n.language)
    : '';

  return (
    <Paper
      elevation={0}
      sx={{
        p: 3,
        borderRadius: 3,
        bgcolor: '#141414',
        border: '1px solid rgba(255, 255, 255, 0.06)',
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        textAlign: 'center',
      }}
    >
      {loading && !summary ? (
        <>
          <Skeleton variant="circular" width={88} height={88} sx={{ mb: 2 }} />
          <Skeleton variant="text" width="60%" />
          <Skeleton variant="text" width="40%" />
        </>
      ) : (
        <>
          <Avatar
            src={summary?.avatar}
            sx={{
              width: 88,
              height: 88,
              mb: 2,
              fontSize: 32,
              fontWeight: 700,
              bgcolor: 'primary.main',
              border: '3px solid rgba(0, 168, 78, 0.35)',
            }}
          >
            {summary?.displayName?.[0]?.toUpperCase()}
          </Avatar>
          <Typography variant="h6" sx={{ fontWeight: 700 }} noWrap>
            {summary?.displayName || summary?.username || '—'}
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
            @{summary?.username}
          </Typography>
          {joinDate && (
            <Typography variant="caption" color="text.secondary" sx={{ mb: 2 }}>
              {t('memberSince', { date: joinDate })}
            </Typography>
          )}

          <Box sx={{ display: 'flex', gap: 4, my: 2 }}>
            <Box>
              <Typography variant="h6" sx={{ fontWeight: 800 }}>
                {summary?.totalFriends ?? 0}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                {t('statFriends')}
              </Typography>
            </Box>
            <Box>
              <Typography variant="h6" sx={{ fontWeight: 800 }}>
                {summary?.totalPosts ?? 0}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                {t('statPosts')}
              </Typography>
            </Box>
          </Box>

          <Button
            fullWidth
            variant="outlined"
            endIcon={<ChevronRight />}
            onClick={() => navigate('/social/profile')}
            sx={{ mt: 'auto', borderColor: 'rgba(255,255,255,0.15)' }}
          >
            {t('viewProfile')}
          </Button>
        </>
      )}
    </Paper>
  );
});

ProfileSummaryCard.displayName = 'ProfileSummaryCard';

export default ProfileSummaryCard;
