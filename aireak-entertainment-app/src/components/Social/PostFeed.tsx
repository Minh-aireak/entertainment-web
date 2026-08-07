import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Box, Card, CircularProgress, Grow, IconButton, Skeleton, Tooltip, Typography } from '@mui/material';
import { Autorenew, AutoAwesome } from '@mui/icons-material';
import { useSelector } from 'react-redux';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';

import { postService } from '../../api/postService';
import { type RootState } from '../../store';
import { useInfiniteScroll } from '../../hooks/useInfiniteScroll';
import PostComposer from './PostComposer';
import PostCard from './PostCard';
import type { Post } from './types';

const RANDOM_LIMIT = 5;
const MAX_EXCLUDE_IDS = 100;
const ENTRANCE_STAGGER_CAP = 6;

const FeedSkeleton: React.FC = () => (
  <Box className="flex flex-col gap-4">
    {[0, 1].map((i) => (
      <Card key={i} elevation={0} sx={{ p: 2.5, borderRadius: 3 }}>
        <Box className="flex items-center gap-2" sx={{ mb: 2 }}>
          <Skeleton variant="circular" width={40} height={40} />
          <Box sx={{ flex: 1 }}>
            <Skeleton variant="text" width="30%" />
            <Skeleton variant="text" width="20%" />
          </Box>
        </Box>
        <Skeleton variant="text" width="90%" />
        <Skeleton variant="text" width="70%" />
      </Card>
    ))}
  </Box>
);

const PostFeed: React.FC = () => {
  const { t } = useTranslation();
  const profileData = useSelector((state: RootState) => state.profile.profileData);

  const [posts, setPosts] = useState<Post[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [hasMore, setHasMore] = useState(true);
  const [likingIds, setLikingIds] = useState<Set<string>>(new Set());

  const postsRef = useRef<Post[]>([]);
  useEffect(() => {
    postsRef.current = posts;
  }, [posts]);

  const fetchRandomPosts = useCallback(async (excludeIds: string[], isInitial: boolean, notifySuccess = false) => {
    if (isInitial) setLoading(true);
    else setLoadingMore(true);

    try {
      const res = await postService.getRandomPosts(RANDOM_LIMIT, excludeIds);
      if (res.data.code === 1000) {
        const fresh = res.data.result ?? [];
        setPosts((prev) => (isInitial ? fresh : [...prev, ...fresh]));
        setHasMore(fresh.length > 0);
        if (notifySuccess) toast.success(t('feedRefreshed'));
      }
    } catch (error) {
      console.error('Failed to fetch random posts:', error);
      if (!isInitial) toast.error(t('postsLoadMoreFailed'));
    } finally {
      if (isInitial) setLoading(false);
      else setLoadingMore(false);
    }
  }, [t]);

  useEffect(() => {
    fetchRandomPosts([], true);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleLoadMore = useCallback(() => {
    if (loadingMore || loading || !hasMore) return;
    const excludeIds = postsRef.current.map((p) => p.id).slice(-MAX_EXCLUDE_IDS);
    fetchRandomPosts(excludeIds, false);
  }, [loading, loadingMore, hasMore, fetchRandomPosts]);

  const handleShuffle = useCallback(() => {
    if (loading) return;
    setHasMore(true);
    fetchRandomPosts([], true, true);
  }, [loading, fetchRandomPosts]);

  const sentinelRef = useInfiniteScroll({
    hasMore,
    loading: loading || loadingMore,
    onLoadMore: handleLoadMore,
  });

  const handlePostCreated = useCallback((post: Post) => {
    setPosts((prev) => [post, ...prev]);
  }, []);

  const handleToggleLike = useCallback(async (post: Post) => {
    if (likingIds.has(post.id)) return;
    setLikingIds((prev) => new Set(prev).add(post.id));

    const previousLiked = post.liked;
    const previousCount = post.likeCount;
    setPosts((prev) =>
      prev.map((p) =>
        p.id === post.id
          ? { ...p, liked: !previousLiked, likeCount: previousCount + (previousLiked ? -1 : 1) }
          : p
      )
    );

    try {
      const res = await postService.toggleLike(post.id, post.postType);
      if (res.data.code === 1000 && res.data.result) {
        const { liked, likeCount } = res.data.result;
        setPosts((prev) => prev.map((p) => (p.id === post.id ? { ...p, liked, likeCount } : p)));
      }
    } catch (error) {
      console.error('Failed to toggle like:', error);
      setPosts((prev) =>
        prev.map((p) => (p.id === post.id ? { ...p, liked: previousLiked, likeCount: previousCount } : p))
      );
      toast.error(t('likeFailed'));
    } finally {
      setLikingIds((prev) => {
        const next = new Set(prev);
        next.delete(post.id);
        return next;
      });
    }
  }, [likingIds, t]);

  return (
    <Box>
      <PostComposer
        avatar={profileData?.avatar}
        displayName={profileData?.displayName || profileData?.username}
        onPostCreated={handlePostCreated}
      />

      <Box className="flex items-center justify-between" sx={{ mb: 2, px: 0.5 }}>
        <Box className="flex items-center gap-1">
          <AutoAwesome sx={{ fontSize: 20, color: 'primary.main' }} />
          <Box>
            <Typography variant="subtitle1" sx={{ fontWeight: 800, lineHeight: 1.2 }}>
              {t('discoverFeedTitle')}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              {t('discoverFeedSubtitle')}
            </Typography>
          </Box>
        </Box>

        <Tooltip title={t('refreshFeed')}>
          <span>
            <IconButton
              onClick={handleShuffle}
              disabled={loading}
              size="small"
              sx={{
                color: 'primary.main',
                bgcolor: 'action.hover',
                animation: loading ? 'spin 900ms linear infinite' : 'none',
                '@keyframes spin': { from: { transform: 'rotate(0deg)' }, to: { transform: 'rotate(360deg)' } },
                '&:hover': { bgcolor: 'action.selected', transform: 'rotate(90deg)' },
                transition: 'transform 200ms ease',
              }}
            >
              <Autorenew fontSize="small" />
            </IconButton>
          </span>
        </Tooltip>
      </Box>

      {loading ? (
        <FeedSkeleton />
      ) : posts.length === 0 ? (
        <Card elevation={0} sx={{ p: 4, textAlign: 'center', borderRadius: 3 }}>
          <Typography color="text.secondary">{t('noPostsYet')}</Typography>
        </Card>
      ) : (
        <Box className="flex flex-col gap-4">
          {posts.map((post, index) => (
            <Grow
              in
              appear
              key={post.id}
              timeout={400}
              style={{ transitionDelay: `${Math.min(index, ENTRANCE_STAGGER_CAP) * 60}ms` }}
            >
              <div>
                <PostCard post={post} liking={likingIds.has(post.id)} onToggleLike={handleToggleLike} />
              </div>
            </Grow>
          ))}

          <Box ref={sentinelRef} className="flex items-center justify-center" sx={{ py: 2, minHeight: 40 }}>
            {loadingMore && <CircularProgress size={22} />}
            {!hasMore && !loadingMore && (
              <Typography variant="caption" color="text.secondary">
                {t('endOfFeed')}
              </Typography>
            )}
          </Box>
        </Box>
      )}
    </Box>
  );
};

export default PostFeed;
