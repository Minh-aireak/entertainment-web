import React, { useCallback, useEffect, useState } from 'react';
import { Box, Typography } from '@mui/material';
import { Person } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';

import { postService } from '../../api/postService';
import { useInfiniteScroll } from '../../hooks/useInfiniteScroll';
import { usePostLikeToggle } from '../../hooks/usePostLikeToggle';
import PostFeedList from './PostFeedList';
import type { Post } from './types';

const PAGE_SIZE = 10;
const FIRST_PAGE = 1;

const MyPostsFeed: React.FC = () => {
  const { t } = useTranslation();

  const [posts, setPosts] = useState<Post[]>([]);
  const [page, setPage] = useState(FIRST_PAGE);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [hasMore, setHasMore] = useState(true);
  const { likingIds, handleToggleLike } = usePostLikeToggle(posts, setPosts);

  const fetchPage = useCallback(async (targetPage: number, isInitial: boolean) => {
    if (isInitial) setLoading(true);
    else setLoadingMore(true);

    try {
      const res = await postService.getPosts(targetPage, PAGE_SIZE);
      if (res.data.code === 1000) {
        const result = res.data.result;
        const fresh = result?.data ?? [];
        setPosts((prev) => (isInitial ? fresh : [...prev, ...fresh]));
        setPage(targetPage);
        setHasMore(result ? result.currentPage < result.totalPages : false);
      }
    } catch (error) {
      console.error('Failed to fetch my posts:', error);
      if (!isInitial) toast.error(t('postsLoadMoreFailed'));
    } finally {
      if (isInitial) setLoading(false);
      else setLoadingMore(false);
    }
  }, [t]);

  useEffect(() => {
    fetchPage(FIRST_PAGE, true);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleLoadMore = useCallback(() => {
    if (loadingMore || loading || !hasMore) return;
    fetchPage(page + 1, false);
  }, [loading, loadingMore, hasMore, page, fetchPage]);

  const sentinelRef = useInfiniteScroll({
    hasMore,
    loading: loading || loadingMore,
    onLoadMore: handleLoadMore,
  });

  return (
    <Box>
      <Box className="flex items-center gap-1.5" sx={{ mb: 2, px: 0.5 }}>
        <Person sx={{ fontSize: 24, color: 'primary.main' }} />
        <Typography variant="h6" sx={{ fontWeight: 800, lineHeight: 1.2 }}>
          {t('myPostsTitle')}
        </Typography>
      </Box>

      <PostFeedList
        posts={posts}
        loading={loading}
        loadingMore={loadingMore}
        hasMore={hasMore}
        likingIds={likingIds}
        onToggleLike={handleToggleLike}
        sentinelRef={sentinelRef}
        emptyMessage={t('noMyPostsYet')}
      />
    </Box>
  );
};

export default MyPostsFeed;
