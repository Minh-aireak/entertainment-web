import React, { useEffect, useState } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { Box, Typography, Button, ButtonBase, CircularProgress, Tooltip, Collapse } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { ChatBubbleOutlined, EmojiEmotions, TextSnippet } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { commentService, type CommentType } from '../../api/commentService';
import { setComments, appendComments } from '../../store';
import { type RootState, type AppDispatch } from '../../store';
import { useCommentSocket } from '../../hooks/useCommentSocket';
import CommentComposer from './CommentComposer';
import CommentItem from './CommentItem';
import toast from 'react-hot-toast';
import { VIBE_TEAL, pillSx, vibeBorder, vibeSurface } from '../../styles/vibe';

const DEFAULT_PAGE_SIZE = 5;
const MAX_COMMENT_LENGTH = 2000;

interface CommentSectionProps {
  sourceId: string;
  /** Controlled mode: parent owns the expand/collapse state (e.g. PostCard's Comment action)
   *  and hides the internal toggle button. Omit for the standalone/uncontrolled usage. */
  open?: boolean;
  /** Notifies the parent whenever the known total comment count changes (initial fetch + realtime
   *  create/delete), so a collapsed parent (e.g. PostCard's action bar) can keep showing an accurate count. */
  onTotalChange?: (total: number) => void;
}

const CommentSection: React.FC<CommentSectionProps> = ({ sourceId, open, onTotalChange }) => {
  const { t } = useTranslation();
  const theme = useTheme();
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
  // whenever a "comment:created"/"comment:deleted" broadcast lands for this post - top-level
  // AND replies both count here, mirroring the /comments/count endpoint (which sums top-level
  // comments + their replyCount), unlike getComments' totalElement which is top-level only.
  useCommentSocket(sourceId, showComments, {
    onCommentCreated: () => setTotalComments((prev) => prev + 1),
    onCommentDeleted: () => setTotalComments((prev) => Math.max(0, prev - 1)),
  });

  useEffect(() => {
    onTotalChange?.(totalComments);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [totalComments]);

  const fetchComments = async (pageNum: number) => {
    setLoading(true);
    try {
      const response = await commentService.getComments(sourceId, pageNum, DEFAULT_PAGE_SIZE);
      const newComments = response.result.data;

      if (pageNum === 1) {
        dispatch(setComments({ groupId: sourceId, comments: newComments }));
        // Total INCLUDING replies - the paginated response's totalElement is top-level only
        // (it drives root-comment pagination below), so the header count needs its own fetch.
        commentService.getCommentCount(sourceId)
          .then((countRes) => setTotalComments(countRes?.result ?? 0))
          .catch(() => {});
      } else {
        dispatch(appendComments({ groupId: sourceId, comments: newComments }));
      }

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
        <Button
          startIcon={<ChatBubbleOutlined />}
          onClick={handleToggleComments}
          sx={{
            mb: 1,
            borderRadius: '999px',
            fontWeight: 800,
            px: 1.75,
            color: showComments ? VIBE_TEAL : 'text.secondary',
            bgcolor: showComments ? alpha(VIBE_TEAL, 0.12) : 'transparent',
            '&:hover': { bgcolor: alpha(VIBE_TEAL, 0.16), color: VIBE_TEAL },
          }}
        >
          {t('commentsLabel')} ({totalComments})
        </Button>
      )}

      <Collapse in={showComments} timeout={220} unmountOnExit={!isControlled}>
        <Box sx={{ mt: isControlled ? 0 : 2 }}>
          <Box
            sx={{
              p: { xs: 1.5, sm: 2 },
              mb: 2.5,
              borderRadius: '22px',
              bgcolor: vibeSurface(theme),
              border: '1px solid',
              borderColor: vibeBorder(theme),
            }}
          >
            <Box sx={{ display: 'flex', gap: 1, mb: 1.5 }}>
              <Tooltip title={t('textCommentTooltip')}>
                <ButtonBase
                  onClick={() => setCommentType('TEXT')}
                  aria-pressed={commentType === 'TEXT'}
                  aria-label={t('textCommentTooltip')}
                  sx={{ ...pillSx(theme, commentType === 'TEXT'), display: 'flex', gap: 0.5, py: 0.5 }}
                >
                  <TextSnippet sx={{ fontSize: 17 }} /> Aa
                </ButtonBase>
              </Tooltip>
              <Tooltip title={t('iconCommentTooltip')}>
                <ButtonBase
                  onClick={() => setCommentType('ICON')}
                  aria-pressed={commentType === 'ICON'}
                  aria-label={t('iconCommentTooltip')}
                  sx={{ ...pillSx(theme, commentType === 'ICON'), display: 'flex', gap: 0.5, py: 0.5 }}
                >
                  <EmojiEmotions sx={{ fontSize: 17 }} /> 😎
                </ButtonBase>
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
              <CircularProgress size={24} sx={{ color: VIBE_TEAL }} />
            </Box>
          ) : comments.length === 0 ? (
            <Box sx={{ textAlign: 'center', py: 3 }}>
              <Typography sx={{ fontSize: '1.8rem', lineHeight: 1, mb: 1 }} aria-hidden>💬</Typography>
              <Typography variant="body2" color="text.secondary">
                {t('noCommentsYet')}
              </Typography>
            </Box>
          ) : (
            <Box>
              {comments.map((comment) => (
                <CommentItem key={comment.id} comment={comment} groupId={sourceId} sourceId={sourceId} />
              ))}
            </Box>
          )}

          {hasMore && !loading && comments.length > 0 && (
            <ButtonBase onClick={loadMore} sx={{ ...pillSx(theme, false), mt: 1.5, color: VIBE_TEAL }}>
              {t('loadMoreComments')}
            </ButtonBase>
          )}

          {loading && page > 1 && (
            <Box sx={{ display: 'flex', justifyContent: 'center', py: 1 }}>
              <CircularProgress size={20} sx={{ color: VIBE_TEAL }} />
            </Box>
          )}
        </Box>
      </Collapse>
    </Box>
  );
};

export default CommentSection;
