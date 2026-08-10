import React from 'react';
import { Box, Card, CircularProgress, Grow, Skeleton, Typography } from '@mui/material';
import { useTranslation } from 'react-i18next';

import PostCard from './PostCard';
import type { Post } from './types';

const ENTRANCE_STAGGER_CAP = 6;

export const PostFeedSkeleton: React.FC = () => (
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

interface PostFeedListProps {
  posts: Post[];
  loading: boolean;
  loadingMore: boolean;
  hasMore: boolean;
  likingIds: Set<string>;
  onToggleLike: (post: Post) => void;
  sentinelRef: React.RefObject<HTMLDivElement | null>;
  emptyMessage: string;
  hideAuthorName?: boolean;
}

const PostFeedList: React.FC<PostFeedListProps> = ({
  posts,
  loading,
  loadingMore,
  hasMore,
  likingIds,
  onToggleLike,
  sentinelRef,
  emptyMessage,
  hideAuthorName,
}) => {
  const { t } = useTranslation();

  if (loading) return <PostFeedSkeleton />;

  if (posts.length === 0) {
    return (
      <Card elevation={0} sx={{ p: 4, textAlign: 'center', borderRadius: 3 }}>
        <Typography color="text.secondary">{emptyMessage}</Typography>
      </Card>
    );
  }

  return (
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
            <PostCard
              post={post}
              liking={likingIds.has(post.id)}
              onToggleLike={onToggleLike}
              hideAuthorName={hideAuthorName}
            />
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
  );
};

export default PostFeedList;
