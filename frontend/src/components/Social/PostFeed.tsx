import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Box, Button } from '@mui/material';
import { Autorenew } from '@mui/icons-material';
import { keyframes } from '@emotion/react';
import { useSelector } from 'react-redux';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';

import { postService } from '../../api/postService';
import { type RootState } from '../../store';
import { useInfiniteScroll } from '../../hooks/useInfiniteScroll';
import { usePostLikeToggle } from '../../hooks/usePostLikeToggle';
import PostComposer from './PostComposer';
import PostFeedList from './PostFeedList';
import type { Post } from './types';

const RANDOM_LIMIT = 5;
const MAX_EXCLUDE_IDS = 100;

const spin = keyframes`
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
`;

const PostFeed: React.FC = () => {
  const { t } = useTranslation();
  const profileData = useSelector((state: RootState) => state.profile.profileData);

  const [posts, setPosts] = useState<Post[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [hasMore, setHasMore] = useState(true);
  const { likingIds, handleToggleLike } = usePostLikeToggle(posts, setPosts);

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
        setHasMore(fresh.length >= RANDOM_LIMIT);
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

  return (
    <Box>
      <PostComposer
        avatar={profileData?.avatar}
        displayName={profileData?.displayName || profileData?.username}
        onPostCreated={handlePostCreated}
      />

      <Box className="flex justify-end" sx={{ mb: 1.5 }}>
        <Button
          onClick={handleShuffle}
          disabled={loading}
          size="small"
          startIcon={
            <Autorenew fontSize="small" sx={{ animation: loading ? `${spin} 900ms linear infinite` : 'none' }} />
          }
          sx={{
            textTransform: 'none',
            fontWeight: 700,
            fontSize: '0.8125rem',
            borderRadius: 999,
            color: 'primary.main',
            bgcolor: 'action.hover',
            px: 1.75,
            '&:hover': { bgcolor: 'action.selected' },
          }}
        >
          {t('refreshFeed')}
        </Button>
      </Box>

      <PostFeedList
        posts={posts}
        loading={loading}
        loadingMore={loadingMore}
        hasMore={hasMore}
        likingIds={likingIds}
        onToggleLike={handleToggleLike}
        sentinelRef={sentinelRef}
        emptyMessage={t('noPostsYet')}
      />
    </Box>
  );
};

export default PostFeed;
