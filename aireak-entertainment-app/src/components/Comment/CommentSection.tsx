import React, { useEffect, useState } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { Box, Typography, Button, CircularProgress, IconButton, Tooltip, Collapse } from '@mui/material';
import { ChatBubbleOutlined, EmojiEmotions, TextSnippet } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { commentService, type CommentType } from '../../api/commentService';
import { setComments, appendComments } from '../../store';
import { type RootState, type AppDispatch } from '../../store';
import { useCommentSocket } from '../../hooks/useCommentSocket';
import CommentComposer from './CommentComposer';
import CommentItem from './CommentItem';
import toast from 'react-hot-toast';

const DEFAULT_PAGE_SIZE = 5;
const MAX_COMMENT_LENGTH = 2000;

interface CommentSectionProps {
  sourceId: string;
  /** Controlled mode: parent owns the expand/collapse state (e.g. PostCard's Comment action)
   *  and hides the internal toggle button. Omit for the standalone/uncontrolled usage. */
  open?: boolean;
}

const CommentSection: React.FC<CommentSectionProps> = ({ sourceId, open }) => {
  const { t } = useTranslation();
  const dispatch = useDispatch<AppDispatch>();
  const comments = useSelector((state: RootState) => state.comment.comments[sourceId] ?? []);
  const isControlled = open !== undefined;

  const [totalComments, setTotalComments] = useState(0);
  const [loading, setLoading] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const [uncontrolledOpen, setUncontrolledOpen] = useState(true);
  const [newComment, setNewComment] = useState('');
  const [commentType, setCommentType] = useState<CommentType>('TEXT');
  const [page, setPage] = useState(1);
  const [hasMore, setHasMore] = useState(true);

  const showComments = isControlled ? Boolean(open) : uncontrolledOpen;

  // Realtime: join the sourceId room while comments are visible, and bump the header count
  // whenever a "comment:created" broadcast lands for this post (top-level or reply, own included).
  // For "comment:deleted" the count is decremented (top-level comments only - a reply delete
  // does not change the top-level comment header count, mirroring what totalElement reports on
  // the REST `getComments` page).
  useCommentSocket(sourceId, showComments, {
    onCommentCreated: () => setTotalComments((prev) => prev + 1),
    onCommentDeleted: (comment) => {
      if (!comment.parentId) {
        setTotalComments((prev) => Math.max(0, prev - 1));
      }
    },
  });

  const fetchComments = async (pageNum: number) => {
    setLoading(true);
    try {
      const response = await commentService.getComments(sourceId, pageNum, DEFAULT_PAGE_SIZE);
      const newComments = response.result.data;

      if (pageNum === 1) {
        dispatch(setComments({ groupId: sourceId, comments: newComments }));
      } else {
        dispatch(appendComments({ groupId: sourceId, comments: newComments }));
      }

      setTotalComments(response?.result?.totalElement || 0);
      setHasMore(pageNum < (response?.result?.totalPages || 0));
      setLoaded(true);
    } catch (error) {
      console.error('Failed to fetch comments:', error);
      toast.error(t('commentsFetchFailed'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (showComments && !loaded) {
      setPage(1);
      fetchComments(1);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [showComments, loaded]);

  const handleToggleComments = () => {
    setUncontrolledOpen((prev) => !prev);
  };

  const handleCreateComment = async () => {
    const trimmed = newComment.trim();
    if (!trimmed) return;
    if (Array.from(trimmed).length > MAX_COMMENT_LENGTH) {
      toast.error(t('commentContentTooLong'));
      return;
    }

    try {
      // Server saves the comment + writes the Outbox event in the same transaction;
      // the new comment reaches the UI (for every viewer, including the sender) only
      // once the "comment:created" broadcast arrives - no local optimistic insert here.
      await commentService.createComment({
        sourceId,
        content: trimmed,
        type: commentType,
        listIdsJoin: [],
      });
      setNewComment('');
      toast.success(t('commentCreated'));
    } catch {
      toast.error(t('commentCreateFailed'));
    }
  };

  const loadMore = () => {
    const nextPage = page + 1;
    setPage(nextPage);
    fetchComments(nextPage);
  };

  return (
    <Box sx={{ mt: isControlled ? 0 : 2 }}>
      {!isControlled && (
        <Button startIcon={<ChatBubbleOutlined />} onClick={handleToggleComments} sx={{ mb: 1 }}>
          {t('commentsLabel')} ({totalComments})
        </Button>
      )}

      <Collapse in={showComments} timeout={220} unmountOnExit={!isControlled}>
        <Box
          sx={{
            mt: isControlled ? 0 : 2,
            pl: 2,
            borderLeft: '2px solid',
            borderColor: 'divider',
          }}
        >
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1, mb: 3 }}>
            <Box sx={{ display: 'flex', gap: 1, mb: 1 }}>
              <Tooltip title={t('textCommentTooltip')}>
                <IconButton
                  size="small"
                  color={commentType === 'TEXT' ? 'primary' : 'default'}
                  onClick={() => setCommentType('TEXT')}
                >
                  <TextSnippet />
                </IconButton>
              </Tooltip>
              <Tooltip title={t('iconCommentTooltip')}>
                <IconButton
                  size="small"
                  color={commentType === 'ICON' ? 'primary' : 'default'}
                  onClick={() => setCommentType('ICON')}
                >
                  <EmojiEmotions />
                </IconButton>
              </Tooltip>
            </Box>
            <CommentComposer
              value={newComment}
              onChange={setNewComment}
              onSubmit={handleCreateComment}
              placeholder={commentType === 'TEXT' ? t('writeCommentPlaceholder') : t('writeIconCommentPlaceholder')}
            />
          </Box>

          {loading && page === 1 ? (
            <Box sx={{ display: 'flex', justifyContent: 'center', py: 2 }}>
              <CircularProgress size={24} />
            </Box>
          ) : comments.length === 0 ? (
            <Typography variant="body2" color="text.secondary" sx={{ py: 2 }}>
              {t('noCommentsYet')}
            </Typography>
          ) : (
            <Box>
              {comments.map((comment) => (
                <CommentItem key={comment.id} comment={comment} groupId={sourceId} sourceId={sourceId} />
              ))}
            </Box>
          )}

          {hasMore && !loading && comments.length > 0 && (
            <Button size="small" onClick={loadMore} sx={{ mt: 1 }}>
              {t('loadMoreComments')}
            </Button>
          )}

          {loading && page > 1 && (
            <Box sx={{ display: 'flex', justifyContent: 'center', py: 1 }}>
              <CircularProgress size={20} />
            </Box>
          )}
        </Box>
      </Collapse>
    </Box>
  );
};

export default CommentSection;
