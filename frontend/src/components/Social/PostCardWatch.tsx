import React from 'react';
import { Box, Button, Chip, Typography } from '@mui/material';
import {
  MovieCreation,
  PeopleAlt,
  OndemandVideo,
  ContentCopy,
} from '@mui/icons-material';
import { useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { useTranslation } from 'react-i18next';

interface PostCardWatchProps {
  watchFilmTitle: string;
  watchFilmThumbnailUrl?: string;
  watchInviteCode: string;
  watchParticipantCount?: number;
  roomClosed?: boolean;
}

const PostCardWatch: React.FC<PostCardWatchProps> = ({
  watchFilmTitle,
  watchFilmThumbnailUrl,
  watchInviteCode,
  watchParticipantCount,
  roomClosed,
}) => {
  const { t } = useTranslation();
  const navigate = useNavigate();

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(watchInviteCode || '');
      toast.success(t('inviteCodeCopied'));
    } catch {
      toast.error(t('copyFailed'));
    }
  };

  const handleJoin = () => {
    if (roomClosed || !watchInviteCode) return;
    const dest = `/film/watch-together?code=${encodeURIComponent(watchInviteCode)}`;
    navigate(dest);
  };

  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: { xs: 'column', sm: 'row' },
        gap: 1.5,
        borderRadius: 2,
        overflow: 'hidden',
        border: '1px solid',
        borderColor: 'divider',
        bgcolor: 'background.paper',
      }}
    >
      <Box
        sx={{
          position: 'relative',
          width: { xs: '100%', sm: '40%' },
          aspectRatio: '16 / 9',
          flexShrink: 0,
          bgcolor: watchFilmThumbnailUrl ? 'transparent' : 'action.hover',
          backgroundImage: watchFilmThumbnailUrl ? `url(${watchFilmThumbnailUrl})` : undefined,
          backgroundSize: 'cover',
          backgroundPosition: 'center',
        }}
      >
        {!watchFilmThumbnailUrl && (
          <Box
            sx={{
              position: 'absolute',
              inset: 0,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: 'text.secondary',
            }}
          >
            <MovieCreation sx={{ fontSize: 48, opacity: 0.4 }} />
          </Box>
        )}
      </Box>

      <Box sx={{ flex: 1, p: 1.5, minWidth: 0, display: 'flex', flexDirection: 'column', gap: 1 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <Chip
            size="small"
            variant="outlined"
            icon={<PeopleAlt sx={{ fontSize: 16 }} />}
            label={t('participantsInRoom', { count: watchParticipantCount ?? 1 })}
            sx={{ alignSelf: 'flex-start' }}
          />
        </Box>

        <Typography variant="h6" sx={{ fontWeight: 800, lineHeight: 1.2, wordBreak: 'break-word' }} noWrap>
          {watchFilmTitle || t('watchTogetherTitle')}
        </Typography>

        <Box sx={{ flex: 1 }} />

        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 1, mt: 0.5 }}>
          <Button
            size="small"
            variant="outlined"
            startIcon={<ContentCopy fontSize="small" />}
            onClick={handleCopy}
            disabled={!watchInviteCode}
          >
            {t('copyInviteCode')}
          </Button>
          <Button
            size="small"
            variant="contained"
            startIcon={<OndemandVideo fontSize="small" />}
            onClick={handleJoin}
            disabled={roomClosed || !watchInviteCode}
          >
            {roomClosed ? t('roomClosedLabel') : t('joinRoom')}
          </Button>
        </Box>
      </Box>
    </Box>
  );
};

export default PostCardWatch;
