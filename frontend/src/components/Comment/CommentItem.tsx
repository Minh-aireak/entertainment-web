import React, { useEffect, useRef, useState } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { Avatar, Box, ButtonBase, CircularProgress, IconButton, InputBase, Menu, MenuItem, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Delete, Edit, EmojiEmotions, Favorite, FavoriteBorder, MoreVert, ThumbUp, ThumbUpOffAlt } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import { commentService, type CommentReactionType, type CommentResponse } from '../../api/commentService';
import { appendComments, patchComment, setComments } from '../../store';
import { type AppDispatch, type RootState } from '../../store';
import { useConfirmDialog } from '../../contexts/ConfirmDialogContext';
import CommentComposer from './CommentComposer';
import {
  VIBE_GRADIENT,
  VIBE_LOVE_GRADIENT,
  VIBE_TEAL,
  composerBarSx,
  pillSx,
  vibeBorder,
  vibeSurface,
  vibeSurfaceHover,
} from '../../styles/vibe';

const MAX_COMMENT_LENGTH = 2000;
const REPLIES_PAGE_SIZE = 5;
// Mirrors the backend's like/unlike debounce: each click flips the reaction instantly, but a
// burst of clicks only sends one request once the user pauses, matching whatever the final
// reaction actually settled on.
const REACTION_DEBOUNCE_MS = 400;

interface PendingReaction {
  timer: number;
  reaction: CommentReactionType | null;
  likeCount: number;
  loveCount: number;
}

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
  const theme = useTheme();
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

  const pendingReactionRef = useRef<PendingReaction | null>(null);

  useEffect(() => {
    return () => {
      if (pendingReactionRef.current) {
        window.clearTimeout(pendingReactionRef.current.timer);
      }
    };
  }, []);

  const flushReaction = async (baseline: PendingReaction, finalReaction: CommentReactionType | null) => {
    pendingReactionRef.current = null;
    if (finalReaction === baseline.reaction) {
      // Net no-op for this burst of clicks - already matches the server, skip the request.
      return;
    }

    // Whatever type gets sent, the backend toggles it: sending the reaction we're leaving
    // clears it, sending a new one sets it - so if the burst nets out to "no reaction", we send
    // back the baseline type specifically to trigger that toggle-off.
    const requestType = finalReaction ?? baseline.reaction;
    if (!requestType) return;

    try {
      // Apply the authoritative response for the actor immediately. The backend also broadcasts
      // the count change so every other viewer in this post's realtime room stays in sync.
      const response = await commentService.react(comment.id, requestType);
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
      dispatch(
        patchComment({
          groupId,
          commentId: comment.id,
          patch: {
            myReaction: baseline.reaction,
            likeCount: baseline.likeCount,
            loveCount: baseline.loveCount,
          },
        }),
      );
      toast.error(t('reactionFailed'));
    }
  };

  const handleReact = (type: CommentReactionType) => {
    const currentReaction = comment.myReaction ?? null;
    const nextReaction = currentReaction === type ? null : type;

    let likeDelta = 0;
    let loveDelta = 0;
    if (currentReaction === 'LIKE') likeDelta -= 1;
    if (currentReaction === 'LOVE') loveDelta -= 1;
    if (nextReaction === 'LIKE') likeDelta += 1;
    if (nextReaction === 'LOVE') loveDelta += 1;

    dispatch(
      patchComment({
        groupId,
        commentId: comment.id,
        patch: {
          myReaction: nextReaction,
          likeCount: Math.max(0, comment.likeCount + likeDelta),
          loveCount: Math.max(0, comment.loveCount + loveDelta),
        },
      }),
    );

    const existing = pendingReactionRef.current;
    if (existing) {
      window.clearTimeout(existing.timer);
    }
    const baseline: PendingReaction = existing ?? {
      timer: 0,
      reaction: currentReaction,
      likeCount: comment.likeCount,
      loveCount: comment.loveCount,
    };
    const timer = window.setTimeout(() => flushReaction(baseline, nextReaction), REACTION_DEBOUNCE_MS);
    pendingReactionRef.current = { ...baseline, timer };
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

  const isLiked = comment.myReaction === 'LIKE';
  const isLoved = comment.myReaction === 'LOVE';

  const reactionPillSx = (active: boolean, activeBackground: string) => ({
    display: 'inline-flex',
    alignItems: 'center',
    gap: 0.5,
    px: 1.1,
    py: 0.35,
    borderRadius: '999px',
    fontSize: '0.78rem',
    fontWeight: 800,
    color: active ? '#fff' : 'text.secondary',
    background: active ? activeBackground : 'transparent',
    border: '1px solid',
    borderColor: active ? 'transparent' : vibeBorder(theme),
    transition: 'transform 0.15s ease, background-color 0.15s ease, color 0.15s ease',
    '&:hover': {
      transform: 'translateY(-1px)',
      ...(active ? {} : { bgcolor: vibeSurfaceHover(theme), color: 'text.primary' }),
    },
    '&:active': { transform: 'scale(0.94)' },
  });

  const textActionSx = {
    px: 0.75,
    py: 0.35,
    borderRadius: '999px',
    fontSize: '0.78rem',
    fontWeight: 800,
    '&:hover': { color: VIBE_TEAL },
  };

  return (
    <Box
      sx={{
        display: 'flex',
        gap: 1.25,
        py: 1,
        animation: 'commentIn 0.25s ease-out',
        '@keyframes commentIn': {
          from: { opacity: 0, transform: 'translateY(6px)' },
          to: { opacity: 1, transform: 'translateY(0)' },
        },
      }}
    >
      <Avatar
        src={comment.avatar}
        slotProps={{ img: { loading: 'lazy' } }}
        sx={{ width: isReply ? 30 : 38, height: isReply ? 30 : 38, mt: 0.25, border: '2px solid', borderColor: vibeBorder(theme) }}
      />
      <Box sx={{ flex: 1, minWidth: 0 }}>
        {isEditing ? (
          <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
            <Box sx={{ ...composerBarSx(theme), flex: 1, minWidth: 200 }}>
              <InputBase
                fullWidth
                value={editContent}
                onChange={(e) => setEditContent(e.target.value)}
                autoFocus
                inputProps={{ 'aria-label': t('edit') }}
                sx={{ fontSize: '0.92rem' }}
              />
            </Box>
            <ButtonBase onClick={handleUpdate} sx={pillSx(theme, true)}>{t('save')}</ButtonBase>
            <ButtonBase onClick={() => setIsEditing(false)} sx={pillSx(theme, false)}>{t('cancel')}</ButtonBase>
          </Box>
        ) : (
          <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 0.5, maxWidth: '100%' }}>
            <Box
              sx={{
                px: 1.5,
                py: 1,
                minWidth: 0,
                borderRadius: '18px',
                borderTopLeftRadius: '6px',
                bgcolor: vibeSurface(theme),
                border: '1px solid',
                borderColor: vibeBorder(theme),
              }}
            >
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75, flexWrap: 'wrap' }}>
                <Typography sx={{ fontWeight: 800, fontSize: '0.86rem' }}>
                  {comment.displayName || t('anonymousUser')}
                </Typography>
                {comment.status === 'EDITED' && (
                  <Typography variant="caption" sx={{ fontStyle: 'italic', color: 'text.secondary' }}>
                    ({t('editedLabel')})
                  </Typography>
                )}
              </Box>
              <Typography
                variant="body2"
                color="text.primary"
                sx={{ mt: 0.25, display: 'flex', alignItems: 'center', gap: 0.75, wordBreak: 'break-word', lineHeight: 1.5 }}
              >
                {comment.type === 'ICON' && <EmojiEmotions fontSize="small" sx={{ color: VIBE_TEAL }} />}
                {comment.content}
              </Typography>
            </Box>
            {user?.id === comment.userId && (
              <IconButton size="small" onClick={(e) => setAnchorEl(e.currentTarget)} sx={{ mt: 0.25, color: 'text.secondary' }}>
                <MoreVert fontSize="small" />
              </IconButton>
            )}
          </Box>
        )}

        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75, mt: 0.75, flexWrap: 'wrap' }}>
          <Typography variant="caption" sx={{ color: 'text.secondary', fontWeight: 600, mr: 0.25 }}>
            {comment.durationCreatedDate}
          </Typography>
          <ButtonBase onClick={() => handleReact('LIKE')} aria-pressed={isLiked} sx={reactionPillSx(isLiked, VIBE_GRADIENT)}>
            {isLiked ? <ThumbUp sx={{ fontSize: 15 }} /> : <ThumbUpOffAlt sx={{ fontSize: 15 }} />}
            {comment.likeCount > 0 ? comment.likeCount : t('likeLabel')}
          </ButtonBase>
          <ButtonBase onClick={() => handleReact('LOVE')} aria-pressed={isLoved} sx={reactionPillSx(isLoved, VIBE_LOVE_GRADIENT)}>
            {isLoved ? <Favorite sx={{ fontSize: 15 }} /> : <FavoriteBorder sx={{ fontSize: 15 }} />}
            {comment.loveCount > 0 ? comment.loveCount : t('loveLabel')}
          </ButtonBase>
          {!isReply && (
            <ButtonBase
              onClick={() => setReplyOpen((prev) => !prev)}
              sx={{ ...textActionSx, color: replyOpen ? VIBE_TEAL : 'text.secondary' }}
            >
              {t('replyLabel')}
            </ButtonBase>
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
          <ButtonBase onClick={handleToggleReplies} sx={{ ...textActionSx, mt: 0.75, gap: 0.75, color: VIBE_TEAL }}>
            <Box sx={{ width: 18, height: 2, borderRadius: 1, background: VIBE_GRADIENT }} />
            {repliesExpanded ? t('hideRepliesLabel') : t('viewRepliesLabel', { count: comment.replyCount })}
          </ButtonBase>
        )}

        {!isReply && repliesExpanded && (
          <Box
            sx={{
              mt: 0.5,
              pl: { xs: 1, sm: 1.5 },
              borderLeft: '2px solid',
              borderImage: `${VIBE_GRADIENT} 1`,
            }}
          >
            {repliesLoading && repliesPage === 1 ? (
              <Box sx={{ display: 'flex', justifyContent: 'center', py: 1 }}>
                <CircularProgress size={18} sx={{ color: VIBE_TEAL }} />
              </Box>
            ) : (
              replies.map((reply) => (
                <CommentItem key={reply.id} comment={reply} groupId={comment.id} sourceId={sourceId} isReply />
              ))
            )}
            {repliesHasMore && !repliesLoading && replies.length > 0 && (
              <ButtonBase onClick={handleLoadMoreReplies} sx={{ ...textActionSx, color: VIBE_TEAL }}>
                {t('loadMoreComments')}
              </ButtonBase>
            )}
            {repliesLoading && repliesPage > 1 && (
              <Box sx={{ display: 'flex', justifyContent: 'center', py: 1 }}>
                <CircularProgress size={16} sx={{ color: VIBE_TEAL }} />
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
