import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Box, Card, CircularProgress, Skeleton, Typography } from '@mui/material';
import { useSelector } from 'react-redux';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';

import { postService } from '../../api/postService';
import { type RootState } from '../../store';
import { REALTIME_NOTIFICATION_EVENT } from '../../contexts/WebSocketContext';
import { useInfiniteScroll } from '../../hooks/useInfiniteScroll';
import PostComposer from './PostComposer';
import PostCard from './PostCard';
import type { Post } from './types';

const POST_TYPE = 'BUSINESS_SCHEDULE';
const PAGE_SIZE = 10;

// Post like/comment counts have no dedicated push event on the backend today —
// only a generic "notification" socket message fires for these actions. When one
// of these types arrives we do a light, debounced re-sync of the visible feed's
// counts instead of guessing which post changed.
const REALTIME_SYNC_TYPES = new Set(['SOCIAL_LIKE', 'SOCIAL_COMMENT', 'LIKE_POST', 'COMMENT_POST']);
const REALTIME_SYNC_DEBOUNCE_MS = 1200;

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
  const [page, setPage] = useState(1);
  const [hasMore, setHasMore] = useState(true);
  const [likingIds, setLikingIds] = useState<Set<string>>(new Set());

  const fetchPosts = useCallback(async (pageNum: number) => {
    if (pageNum === 1) setLoading(true);
    else setLoadingMore(true);

    try {
      const res = await postService.getPosts(pageNum, PAGE_SIZE, POST_TYPE);
      if (res.data.code === 1000) {
        const pageData = res.data.result;
        setPosts((prev) => (pageNum === 1 ? pageData?.data ?? [] : [...prev, ...(pageData?.data ?? [])]));
        setHasMore(pageNum < (pageData?.totalPages ?? 0));
      }
    } catch (error) {
      console.error('Failed to fetch posts:', error);
      if (pageNum > 1) toast.error(t('postsLoadMoreFailed'));
    } finally {
      if (pageNum === 1) setLoading(false);
      else setLoadingMore(false);
    }
  }, [t]);

  useEffect(() => {
    fetchPosts(1);
  }, [fetchPosts]);

  const handleLoadMore = useCallback(() => {
    if (loadingMore || loading || !hasMore) return;
    const nextPage = page + 1;
    setPage(nextPage);
    fetchPosts(nextPage);
  }, [page, hasMore, loading, loadingMore, fetchPosts]);

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

  // Soft realtime sync: merge fresh like/comment counts into whatever is already
  // on screen, matched by id. Never reorders or injects posts we didn't already load.
  const syncVisibleCounts = useCallback(async () => {
    try {
      const res = await postService.getPosts(1, PAGE_SIZE, POST_TYPE);
      if (res.data.code !== 1000) return;
      const freshById = new Map((res.data.result?.data ?? []).map((p) => [p.id, p]));
      setPosts((prev) =>
        prev.map((p) => {
          const fresh = freshById.get(p.id);
          return fresh ? { ...p, likeCount: fresh.likeCount, liked: fresh.liked } : p;
        })
      );
    } catch (error) {
      console.error('Failed to sync realtime post counts:', error);
    }
  }, []);

  const syncDebounceRef = useRef<number | undefined>(undefined);

  useEffect(() => {
    const handleRealtimeNotification = (event: Event) => {
      const detail = (event as CustomEvent<{ type?: string }>).detail;
      if (!detail?.type || !REALTIME_SYNC_TYPES.has(detail.type)) return;
      window.clearTimeout(syncDebounceRef.current);
      syncDebounceRef.current = window.setTimeout(syncVisibleCounts, REALTIME_SYNC_DEBOUNCE_MS);
    };

    window.addEventListener(REALTIME_NOTIFICATION_EVENT, handleRealtimeNotification);
    return () => {
      window.removeEventListener(REALTIME_NOTIFICATION_EVENT, handleRealtimeNotification);
      window.clearTimeout(syncDebounceRef.current);
    };
  }, [syncVisibleCounts]);

  return (
    <Box>
      <PostComposer
        avatar={profileData?.avatar}
        displayName={profileData?.displayName || profileData?.username}
        onPostCreated={handlePostCreated}
      />

      {loading ? (
        <FeedSkeleton />
      ) : posts.length === 0 ? (
        <Card elevation={0} sx={{ p: 4, textAlign: 'center', borderRadius: 3 }}>
          <Typography color="text.secondary">{t('noPostsYet')}</Typography>
        </Card>
      ) : (
        <Box className="flex flex-col gap-4">
          {posts.map((post) => (
            <PostCard
              key={post.id}
              post={post}
              liking={likingIds.has(post.id)}
              onToggleLike={handleToggleLike}
            />
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
