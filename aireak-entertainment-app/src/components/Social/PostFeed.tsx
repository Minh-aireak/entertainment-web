import React, { useCallback, useEffect, useState } from 'react';
import {
  Avatar,
  Box,
  Button,
  CircularProgress,
  IconButton,
  Paper,
  Skeleton,
  TextField,
  Typography,
} from '@mui/material';
import { Favorite, FavoriteBorder, Send } from '@mui/icons-material';
import { useSelector } from 'react-redux';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';

import { postService } from '../../api/postService';
import { type RootState } from '../../store';
import type { ScheduleResponse } from '../../models';
import CommentSection from '../Comment/CommentSection';

const POST_TYPE = 'BUSINESS_SCHEDULE';
const PAGE_SIZE = 10;

const PostFeed: React.FC = () => {
  const { t } = useTranslation();
  const profileData = useSelector((state: RootState) => state.profile.profileData);

  const [posts, setPosts] = useState<ScheduleResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [posting, setPosting] = useState(false);
  const [likingIds, setLikingIds] = useState<Set<string>>(new Set());

  const fetchPosts = useCallback(async () => {
    setLoading(true);
    try {
      const res = await postService.getPosts(1, PAGE_SIZE, POST_TYPE);
      if (res.data.code === 1000) {
        setPosts(res.data.result?.data ?? []);
      }
    } catch (error) {
      console.error('Failed to fetch posts:', error);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchPosts();
  }, [fetchPosts]);

  const handleCreatePost = useCallback(async () => {
    if (!content.trim()) return;
    setPosting(true);
    try {
      const now = new Date();
      const farFuture = new Date(now.getFullYear() + 10, now.getMonth(), now.getDate());
      const res = await postService.createPost({
        postType: POST_TYPE,
        title: title.trim() || t('untitledPost'),
        content: content.trim(),
        startTime: now.toISOString(),
        endTime: farFuture.toISOString(),
      });
      if (res.data.code === 1000) {
        setTitle('');
        setContent('');
        toast.success(t('postCreated'));
        fetchPosts();
      }
    } catch (error) {
      console.error('Failed to create post:', error);
      toast.error(t('postCreateFailed'));
    } finally {
      setPosting(false);
    }
  }, [content, title, t, fetchPosts]);

  const handleToggleLike = useCallback(async (post: ScheduleResponse) => {
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
      <Paper
        elevation={0}
        sx={{
          p: 2.5,
          mb: 2,
          borderRadius: 3,
          bgcolor: '#141414',
          border: '1px solid rgba(255, 255, 255, 0.06)',
        }}
      >
        <Box sx={{ display: 'flex', gap: 1.5 }}>
          <Avatar src={profileData?.avatar} sx={{ bgcolor: 'primary.main' }}>
            {profileData?.displayName?.[0]?.toUpperCase()}
          </Avatar>
          <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 1 }}>
            <TextField
              size="small"
              placeholder={t('postTitlePlaceholder')}
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              fullWidth
            />
            <TextField
              size="small"
              placeholder={t('postContentPlaceholder')}
              value={content}
              onChange={(e) => setContent(e.target.value)}
              multiline
              minRows={2}
              fullWidth
            />
            <Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
              <Button
                variant="contained"
                endIcon={<Send fontSize="small" />}
                disabled={!content.trim() || posting}
                onClick={handleCreatePost}
              >
                {t('postSubmit')}
              </Button>
            </Box>
          </Box>
        </Box>
      </Paper>

      {loading ? (
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          {[0, 1].map((i) => (
            <Paper
              key={i}
              elevation={0}
              sx={{ p: 2.5, borderRadius: 3, bgcolor: '#141414', border: '1px solid rgba(255, 255, 255, 0.06)' }}
            >
              <Skeleton variant="text" width="30%" />
              <Skeleton variant="text" width="90%" />
              <Skeleton variant="text" width="70%" />
            </Paper>
          ))}
        </Box>
      ) : posts.length === 0 ? (
        <Paper
          elevation={0}
          sx={{
            p: 4,
            textAlign: 'center',
            borderRadius: 3,
            bgcolor: '#141414',
            border: '1px solid rgba(255, 255, 255, 0.06)',
          }}
        >
          <Typography color="text.secondary">{t('noPostsYet')}</Typography>
        </Paper>
      ) : (
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          {posts.map((post) => (
            <Paper
              key={post.id}
              elevation={0}
              sx={{ p: 2.5, borderRadius: 3, bgcolor: '#141414', border: '1px solid rgba(255, 255, 255, 0.06)' }}
            >
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 1.5 }}>
                <Avatar src={profileData?.avatar} sx={{ bgcolor: 'primary.main' }}>
                  {profileData?.displayName?.[0]?.toUpperCase()}
                </Avatar>
                <Box sx={{ minWidth: 0 }}>
                  <Typography variant="subtitle2" sx={{ fontWeight: 700 }} noWrap>
                    {profileData?.displayName || profileData?.username}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {post.createdDate}
                  </Typography>
                </Box>
              </Box>

              {post.title && (
                <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 0.5 }}>
                  {post.title}
                </Typography>
              )}
              <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap', mb: 1.5 }}>
                {post.content}
              </Typography>

              <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                <IconButton
                  size="small"
                  color={post.liked ? 'error' : 'default'}
                  onClick={() => handleToggleLike(post)}
                  disabled={likingIds.has(post.id)}
                >
                  {likingIds.has(post.id) ? (
                    <CircularProgress size={18} />
                  ) : post.liked ? (
                    <Favorite fontSize="small" />
                  ) : (
                    <FavoriteBorder fontSize="small" />
                  )}
                </IconButton>
                <Typography variant="body2" color="text.secondary">
                  {post.likeCount}
                </Typography>
              </Box>

              <CommentSection sourceId={post.id} />
            </Paper>
          ))}
        </Box>
      )}
    </Box>
  );
};

export default PostFeed;
