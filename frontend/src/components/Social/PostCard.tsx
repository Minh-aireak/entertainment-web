import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Avatar, Box, Button, Card, CardContent, CardHeader, Divider, Typography } from '@mui/material';
import { Favorite, FavoriteBorder, ChatBubbleOutlined, ShareOutlined } from '@mui/icons-material';
import { keyframes } from '@emotion/react';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';

import { commentService } from '../../api/commentService';
import CommentSection from '../Comment/CommentSection';
import { formatRelativeTime } from '../../utils/time';
import { getAvatarGradient } from '../../utils/avatarColor';
import PostCardImage from './PostCardImage';
import PostCardWatch from './PostCardWatch';
import { findBackground, findFeeling } from './postComposerOptions';
import type { Post } from './types';

const CONTENT_TRUNCATE_LENGTH = 260;

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
  hideAuthorName?: boolean;
}

const actionButtonSx = {
  flex: 1,
  py: 1,
  borderRadius: 2,
  color: 'text.secondary',
  fontWeight: 600,
  '&:hover': { bgcolor: 'action.hover' },
};

const PostCard: React.FC<PostCardProps> = React.memo(({ post, liking, onToggleLike, hideAuthorName }) => {
  const { t, i18n } = useTranslation();
  const [commentsOpen, setCommentsOpen] = useState(false);
  const [justLiked, setJustLiked] = useState(false);
  const [contentExpanded, setContentExpanded] = useState(false);
  const [commentCount, setCommentCount] = useState(0);
  const countPulsing = usePulseOnChange(post.likeCount);

  // Total comment count INCLUDING replies, so the comment icon shows an accurate number
  // even before the user expands the section.
  useEffect(() => {
    let active = true;
    commentService.getCommentCount(post.id)
      .then((res) => {
        if (active) setCommentCount(res?.result ?? 0);
      })
      .catch(() => {});
    return () => {
      active = false;
    };
  }, [post.id]);

  const handleTotalCommentsChange = useCallback((total: number) => {
    setCommentCount(total);
  }, []);

  const isLongContent = post.content.length > CONTENT_TRUNCATE_LENGTH;
  const displayedContent =
    isLongContent && !contentExpanded
      ? `${post.content.slice(0, CONTENT_TRUNCATE_LENGTH).trimEnd()}…`
      : post.content;

  const background = post.postType === 'TEXT' ? findBackground(post.backgroundColor) : undefined;
  const feeling = findFeeling(post.feeling);

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
    <Card
      id={`post-${post.id}`}
      elevation={0}
      className="mb-4"
      sx={{
        borderRadius: 3,
        transition: 'transform 200ms ease, box-shadow 200ms ease',
        '&:hover': {
          transform: 'translateY(-3px)',
          boxShadow: '0 16px 32px rgba(0, 168, 78, 0.14)',
        },
      }}
    >
      <CardHeader
        avatar={
          <Avatar
            src={post.avatar || undefined}
            slotProps={{ img: { loading: 'lazy' } }}
            sx={{
              width: 44,
              height: 44,
              fontWeight: 700,
              color: '#fff',
              background: post.avatar ? undefined : getAvatarGradient(post.userId || post.displayName),
            }}
          >
            {post.displayName?.[0]?.toUpperCase() || '?'}
          </Avatar>
        }
        title={
          hideAuthorName ? undefined : (
            <Typography variant="subtitle2" sx={{ fontWeight: 700 }} noWrap>
              {post.displayName}
            </Typography>
          )
        }
        subheader={
          <Typography variant="caption" color="text.secondary">
            {formatRelativeTime(post.createdDate, i18n.language)}
            {feeling && ` · đang cảm thấy ${feeling.emoji} ${feeling.label}`}
          </Typography>
        }
      />

      <CardContent sx={{ pt: 0, pb: 1 }}>
        {background ? (
          <Box
            sx={{
              borderRadius: 2,
              background: background.gradient,
              minHeight: 160,
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'center',
              textAlign: 'center',
              p: 3,
              gap: 0.5,
            }}
          >
            {post.title && (
              <Typography variant="h6" sx={{ fontWeight: 800, color: background.textColor }}>
                {post.title}
              </Typography>
            )}
            <Typography
              variant="h6"
              sx={{ fontWeight: 800, color: background.textColor, whiteSpace: 'pre-wrap', lineHeight: 1.35 }}
            >
              {displayedContent}
            </Typography>
            {isLongContent && (
              <Button
                size="small"
                onClick={() => setContentExpanded((prev) => !prev)}
                sx={{
                  mt: 0.5,
                  px: 0,
                  minWidth: 0,
                  fontWeight: 700,
                  color: background.textColor,
                  textDecoration: 'underline',
                  '&:hover': { bgcolor: 'transparent' },
                }}
              >
                {contentExpanded ? t('readLess') : t('readMore')}
              </Button>
            )}
          </Box>
        ) : (
          <>
            {post.title && (
              <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 0.5 }}>
                {post.title}
              </Typography>
            )}
            <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>
              {displayedContent}
            </Typography>
            {isLongContent && (
              <Button
                size="small"
                onClick={() => setContentExpanded((prev) => !prev)}
                sx={{
                  mt: 0.5,
                  px: 0,
                  minWidth: 0,
                  fontWeight: 700,
                  color: 'primary.main',
                  '&:hover': { bgcolor: 'transparent', textDecoration: 'underline' },
                }}
              >
                {contentExpanded ? t('readLess') : t('readMore')}
              </Button>
            )}
          </>
        )}
      </CardContent>

      {post.postType === 'IMAGE' && post.imageUrls && post.imageUrls.length > 0 && (
        <Box sx={{ px: 2, pb: 2 }}>
          <PostCardImage imageUrls={post.imageUrls} />
        </Box>
      )}
      {post.postType === 'WATCH_TOGETHER' && post.watchRoomId && (
        <Box sx={{ px: 2, pb: 2 }}>
          <PostCardWatch
            watchFilmTitle={post.watchFilmTitle || ''}
            watchFilmThumbnailUrl={post.watchFilmThumbnailUrl}
            watchInviteCode={post.watchInviteCode || ''}
            watchParticipantCount={post.watchParticipantCount}
          />
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
          {post.likeCount > 0 && (
            <Box
              component="span"
              sx={{ display: 'inline-block', mr: 0.5, animation: countPulsing ? `${pulse} 320ms ease` : 'none' }}
            >
              {post.likeCount}
            </Box>
          )}
          {t('like')}
        </Button>

        <Button
          size="small"
          onClick={() => setCommentsOpen((prev) => !prev)}
          startIcon={<ChatBubbleOutlined fontSize="small" />}
          sx={{ ...actionButtonSx, color: commentsOpen ? 'primary.main' : 'text.secondary' }}
        >
          {commentCount > 0 && (
            <Box component="span" sx={{ display: 'inline-block', mr: 0.5 }}>
              {commentCount}
            </Box>
          )}
          {t('comment')}
        </Button>

        <Button size="small" onClick={handleShare} startIcon={<ShareOutlined fontSize="small" />} sx={actionButtonSx}>
          {t('share')}
        </Button>
      </Box>

      {commentsOpen && (
        <Box className="mt-1" sx={{ px: 2, pb: 2 }}>
          <CommentSection sourceId={post.id} open={commentsOpen} onTotalChange={handleTotalCommentsChange} />
        </Box>
      )}
    </Card>
  );
});

PostCard.displayName = 'PostCard';

export default PostCard;
