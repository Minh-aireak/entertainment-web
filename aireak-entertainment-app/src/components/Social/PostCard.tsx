import React, { useEffect, useRef, useState } from 'react';
import { Avatar, Box, Button, Card, CardContent, CardHeader, Divider, Typography } from '@mui/material';
import { Favorite, FavoriteBorder, ChatBubbleOutlined, ShareOutlined } from '@mui/icons-material';
import { keyframes } from '@emotion/react';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';

import CommentSection from '../Comment/CommentSection';
import { formatRelativeTime } from '../../utils/time';
import type { Post } from './types';

const heartBeat = keyframes`
  0% { transform: scale(1); }
  25% { transform: scale(1.35); }
  50% { transform: scale(0.9); }
  75% { transform: scale(1.12); }
  100% { transform: scale(1); }
`;

const pulse = keyframes`
  0% { transform: scale(1); }
  40% { transform: scale(1.22); }
  100% { transform: scale(1); }
`;

function usePulseOnChange(value: number) {
  const [pulsing, setPulsing] = useState(false);
  const prevRef = useRef(value);

  useEffect(() => {
    if (prevRef.current === value) return;
    prevRef.current = value;
    setPulsing(true);
    const timer = window.setTimeout(() => setPulsing(false), 320);
    return () => window.clearTimeout(timer);
  }, [value]);

  return pulsing;
}

interface PostCardProps {
  post: Post;
  liking: boolean;
  onToggleLike: (post: Post) => void;
}

const actionButtonSx = {
  flex: 1,
  py: 1,
  borderRadius: 2,
  color: 'text.secondary',
  fontWeight: 600,
  '&:hover': { bgcolor: 'action.hover' },
};

const PostCard: React.FC<PostCardProps> = React.memo(({ post, liking, onToggleLike }) => {
  const { t, i18n } = useTranslation();
  const [commentsOpen, setCommentsOpen] = useState(false);
  const [justLiked, setJustLiked] = useState(false);
  const countPulsing = usePulseOnChange(post.likeCount);

  const handleLikeClick = () => {
    if (!post.liked) setJustLiked(true);
    onToggleLike(post);
  };

  const handleShare = async () => {
    const url = `${window.location.origin}/social#post-${post.id}`;
    try {
      await navigator.clipboard.writeText(url);
      toast.success(t('postLinkCopied'));
    } catch {
      toast.error(t('postLinkCopyFailed'));
    }
  };

  return (
    <Card id={`post-${post.id}`} elevation={0} sx={{ borderRadius: 3 }} className="mb-4">
      <CardHeader
        avatar={
          <Avatar
            src={post.avatar || undefined}
            slotProps={{ img: { loading: 'lazy' } }}
            sx={{ bgcolor: 'primary.main', fontWeight: 700 }}
          >
            {post.displayName?.[0]?.toUpperCase() || '?'}
          </Avatar>
        }
        title={
          <Typography variant="subtitle2" sx={{ fontWeight: 700 }} noWrap>
            {post.displayName}
          </Typography>
        }
        subheader={
          <Typography variant="caption" color="text.secondary">
            {formatRelativeTime(post.createdDate, i18n.language)}
          </Typography>
        }
      />

      <CardContent sx={{ pt: 0, pb: 1 }}>
        {post.title && (
          <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 0.5 }}>
            {post.title}
          </Typography>
        )}
        <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>
          {post.content}
        </Typography>
      </CardContent>

      {post.likeCount > 0 && (
        <Box className="flex items-center gap-1" sx={{ px: 2, pb: 1 }}>
          <Favorite sx={{ fontSize: 16, color: 'error.main' }} />
          <Typography
            variant="caption"
            color="text.secondary"
            component="span"
            sx={{
              display: 'inline-block',
              animation: countPulsing ? `${pulse} 320ms ease` : 'none',
            }}
          >
            {post.likeCount}
          </Typography>
        </Box>
      )}

      <Divider />

      <Box className="flex items-center gap-1" sx={{ px: 1, py: 0.5 }}>
        <Button
          size="small"
          disabled={liking}
          onClick={handleLikeClick}
          startIcon={
            <Box
              component="span"
              sx={{ display: 'inline-flex', animation: justLiked ? `${heartBeat} 480ms ease` : 'none' }}
              onAnimationEnd={() => setJustLiked(false)}
            >
              {post.liked ? <Favorite fontSize="small" /> : <FavoriteBorder fontSize="small" />}
            </Box>
          }
          sx={{ ...actionButtonSx, color: post.liked ? 'error.main' : 'text.secondary' }}
        >
          {t('like')}
        </Button>

        <Button
          size="small"
          onClick={() => setCommentsOpen((prev) => !prev)}
          startIcon={<ChatBubbleOutlined fontSize="small" />}
          sx={{ ...actionButtonSx, color: commentsOpen ? 'primary.main' : 'text.secondary' }}
        >
          {t('comment')}
        </Button>

        <Button size="small" onClick={handleShare} startIcon={<ShareOutlined fontSize="small" />} sx={actionButtonSx}>
          {t('share')}
        </Button>
      </Box>

      {commentsOpen && (
        <Box className="mt-1" sx={{ px: 2, pb: 2 }}>
          <CommentSection sourceId={post.id} open={commentsOpen} />
        </Box>
      )}
    </Card>
  );
});

PostCard.displayName = 'PostCard';

export default PostCard;
