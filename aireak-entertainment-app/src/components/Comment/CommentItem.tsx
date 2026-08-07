import React, { useState } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { Avatar, Box, Button, CircularProgress, IconButton, Menu, MenuItem, TextField, Typography } from '@mui/material';
import { Delete, Edit, EmojiEmotions, Favorite, FavoriteBorder, MoreVert, ThumbUp, ThumbUpOffAlt } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import { commentService, type CommentReactionType, type CommentResponse } from '../../api/commentService';
import { appendComments, patchComment, setComments } from '../../store';
import { type AppDispatch, type RootState } from '../../store';
import { useConfirmDialog } from '../../contexts/ConfirmDialogContext';
import CommentComposer from './CommentComposer';

const MAX_COMMENT_LENGTH = 2000;
const REPLIES_PAGE_SIZE = 5;

interface CommentItemProps {
  comment: CommentResponse;
  /** Redux bucket this comment lives in: the post's sourceId for a top-level comment, or the
   *  parent comment's id for a reply. */
  groupId: string;
  /** The post/film id - needed to create a reply (its sourceId must match the post, not the
   *  parent comment) and to stay inside the same realtime room as the rest of the thread. */
  sourceId: string;
  /** Replies are rendered one level deep only - suppresses the Reply button/nested thread. */
  isReply?: boolean;
}

const CommentItem: React.FC<CommentItemProps> = ({ comment, groupId, sourceId, isReply = false }) => {
  const { t } = useTranslation();
  const dispatch = useDispatch<AppDispatch>();
  const user = useSelector((state: RootState) => state.auth.user);
  const replies = useSelector((state: RootState) => state.comment.comments[comment.id] ?? []);
  const confirmDialog = useConfirmDialog();

  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
  const [isEditing, setIsEditing] = useState(false);
  const [editContent, setEditContent] = useState(comment.content);

  const [replyOpen, setReplyOpen] = useState(false);
  const [replyContent, setReplyContent] = useState('');

  const [repliesExpanded, setRepliesExpanded] = useState(false);
  const [repliesLoading, setRepliesLoading] = useState(false);
  const [repliesLoaded, setRepliesLoaded] = useState(false);
  const [repliesPage, setRepliesPage] = useState(1);
  const [repliesHasMore, setRepliesHasMore] = useState(true);

  const validateContent = (content: string): string | null => {
    const trimmed = content.trim();
    if (!trimmed) return null;
    if (Array.from(trimmed).length > MAX_COMMENT_LENGTH) {
      toast.error(t('commentContentTooLong'));
      return null;
    }
    return trimmed;
  };

  const handleUpdate = async () => {
    const trimmed = validateContent(editContent);
    if (!trimmed) return;
    try {
      // No local dispatch here - every viewer (including the editor) updates once the
      // "comment:updated" broadcast lands, same as create.
      await commentService.updateComment(comment.id, { content: trimmed });
      setIsEditing(false);
      toast.success(t('commentUpdated'));
    } catch {
      toast.error(t('commentUpdateFailed'));
    }
  };

  const handleDelete = async () => {
    setAnchorEl(null);
    const confirmed = await confirmDialog({
      title: t('deleteCommentTitle'),
      message: t('deleteCommentMessage'),
      confirmText: t('delete'),
    });
    if (!confirmed) return;
    try {
      await commentService.deleteComment(comment.id);
      toast.success(t('commentDeleted'));
    } catch {
      toast.error(t('commentDeleteFailed'));
    }
  };

  const handleReact = async (type: CommentReactionType) => {
    try {
      // Reactions are high-frequency and NOT broadcast (backend buffers them in Redis) - apply
      // the authoritative counts from the response immediately, for this viewer only.
      const response = await commentService.react(comment.id, type);
      dispatch(
        patchComment({
          groupId,
          commentId: comment.id,
          patch: {
            likeCount: response.result.likeCount,
            loveCount: response.result.loveCount,
            myReaction: response.result.myReaction,
          },
        }),
      );
    } catch {
      toast.error(t('reactionFailed'));
    }
  };

  const fetchReplies = async (page: number) => {
    setRepliesLoading(true);
    try {
      const response = await commentService.getReplies(comment.id, page, REPLIES_PAGE_SIZE);
      const data = response.result.data;
      if (page === 1) {
        dispatch(setComments({ groupId: comment.id, comments: data }));
      } else {
        dispatch(appendComments({ groupId: comment.id, comments: data }));
      }
      setRepliesHasMore(page < (response.result.totalPages || 0));
      setRepliesLoaded(true);
    } catch {
      toast.error(t('commentsFetchFailed'));
    } finally {
      setRepliesLoading(false);
    }
  };

  const handleToggleReplies = () => {
    const next = !repliesExpanded;
    setRepliesExpanded(next);
    if (next && !repliesLoaded) {
      setRepliesPage(1);
      fetchReplies(1);
    }
  };

  const handleLoadMoreReplies = () => {
    const next = repliesPage + 1;
    setRepliesPage(next);
    fetchReplies(next);
  };

  const handleSubmitReply = async () => {
    const trimmed = validateContent(replyContent);
    if (!trimmed) return;
    try {
      await commentService.createComment({
        sourceId,
        content: trimmed,
        type: 'TEXT',
        parentId: comment.id,
        topParentId: comment.id,
      });
      setReplyContent('');
      setReplyOpen(false);
      setRepliesExpanded(true);
      // First time opening this thread - do a real fetch so pre-existing replies show up too
      // (the reply just posted is included, since this reads Mongo directly, no broadcast lag).
      if (!repliesLoaded) {
        setRepliesPage(1);
        fetchReplies(1);
      }
      toast.success(t('commentCreated'));
    } catch {
      toast.error(t('commentCreateFailed'));
    }
  };

  return (
    <Box sx={{ display: 'flex', gap: 1.5, py: 1.25, pl: isReply ? 6 : 0 }}>
      <Avatar
        src={comment.avatar}
        slotProps={{ img: { loading: 'lazy' } }}
        sx={{ width: isReply ? 28 : 36, height: isReply ? 28 : 36 }}
      />
      <Box sx={{ flex: 1, minWidth: 0 }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
            <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
              {comment.displayName || t('anonymousUser')}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              • {comment.durationCreatedDate}
            </Typography>
            {comment.status === 'EDITED' && (
              <Typography variant="caption" sx={{ fontStyle: 'italic', color: 'text.secondary' }}>
                ({t('editedLabel')})
              </Typography>
            )}
          </Box>
          {user?.id === comment.userId && (
            <IconButton size="small" onClick={(e) => setAnchorEl(e.currentTarget)}>
              <MoreVert fontSize="small" />
            </IconButton>
          )}
        </Box>

        {isEditing ? (
          <Box sx={{ mt: 1, display: 'flex', gap: 1 }}>
            <TextField fullWidth size="small" value={editContent} onChange={(e) => setEditContent(e.target.value)} autoFocus />
            <Button size="small" onClick={handleUpdate}>{t('save')}</Button>
            <Button size="small" color="inherit" onClick={() => setIsEditing(false)}>{t('cancel')}</Button>
          </Box>
        ) : (
          <Typography variant="body2" color="text.primary" sx={{ mt: 0.5, display: 'flex', alignItems: 'center', gap: 1 }}>
            {comment.type === 'ICON' && <EmojiEmotions fontSize="small" color="primary" />}
            {comment.content}
          </Typography>
        )}

        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mt: 0.5 }}>
          <Button
            size="small"
            startIcon={comment.myReaction === 'LIKE' ? <ThumbUp fontSize="small" /> : <ThumbUpOffAlt fontSize="small" />}
            color={comment.myReaction === 'LIKE' ? 'primary' : 'inherit'}
            onClick={() => handleReact('LIKE')}
            sx={{ minWidth: 0, px: 1 }}
          >
            {comment.likeCount > 0 ? comment.likeCount : t('likeLabel')}
          </Button>
          <Button
            size="small"
            startIcon={comment.myReaction === 'LOVE' ? <Favorite fontSize="small" color="error" /> : <FavoriteBorder fontSize="small" />}
            color={comment.myReaction === 'LOVE' ? 'error' : 'inherit'}
            onClick={() => handleReact('LOVE')}
            sx={{ minWidth: 0, px: 1 }}
          >
            {comment.loveCount > 0 ? comment.loveCount : t('loveLabel')}
          </Button>
          {!isReply && (
            <Button size="small" color="inherit" sx={{ minWidth: 0, px: 1 }} onClick={() => setReplyOpen((prev) => !prev)}>
              {t('replyLabel')}
            </Button>
          )}
        </Box>

        {!isReply && replyOpen && (
          <Box sx={{ mt: 1 }}>
            <CommentComposer
              value={replyContent}
              onChange={setReplyContent}
              onSubmit={handleSubmitReply}
              placeholder={t('writeReplyPlaceholder')}
              autoFocus
            />
          </Box>
        )}

        {!isReply && comment.replyCount > 0 && (
          <Button size="small" color="inherit" onClick={handleToggleReplies} sx={{ mt: 0.5, minWidth: 0, px: 1 }}>
            {repliesExpanded ? t('hideRepliesLabel') : t('viewRepliesLabel', { count: comment.replyCount })}
          </Button>
        )}

        {!isReply && repliesExpanded && (
          <Box sx={{ mt: 0.5 }}>
            {repliesLoading && repliesPage === 1 ? (
              <Box sx={{ display: 'flex', justifyContent: 'center', py: 1 }}>
                <CircularProgress size={18} />
              </Box>
            ) : (
              replies.map((reply) => (
                <CommentItem key={reply.id} comment={reply} groupId={comment.id} sourceId={sourceId} isReply />
              ))
            )}
            {repliesHasMore && !repliesLoading && replies.length > 0 && (
              <Button size="small" onClick={handleLoadMoreReplies} sx={{ ml: 6 }}>
                {t('loadMoreComments')}
              </Button>
            )}
            {repliesLoading && repliesPage > 1 && (
              <Box sx={{ display: 'flex', justifyContent: 'center', py: 1 }}>
                <CircularProgress size={16} />
              </Box>
            )}
          </Box>
        )}
      </Box>

      <Menu anchorEl={anchorEl} open={Boolean(anchorEl)} onClose={() => setAnchorEl(null)}>
        <MenuItem
          onClick={() => {
            if (comment.type === 'TEXT') {
              setEditContent(comment.content);
              setIsEditing(true);
              setAnchorEl(null);
            } else {
              toast.error(t('textOnlyCommentsEditable'));
              setAnchorEl(null);
            }
          }}
        >
          <Edit fontSize="small" sx={{ mr: 1 }} /> {t('edit')}
        </MenuItem>
        <MenuItem onClick={handleDelete} sx={{ color: 'error.main' }}>
          <Delete fontSize="small" sx={{ mr: 1 }} /> {t('delete')}
        </MenuItem>
      </Menu>
    </Box>
  );
};

export default CommentItem;
