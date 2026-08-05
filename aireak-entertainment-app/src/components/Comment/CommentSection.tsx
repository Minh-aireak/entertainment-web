import React, { useEffect, useState } from 'react';
import { useSelector } from 'react-redux';
import {
  Box,
  Typography,
  Avatar,
  TextField,
  Button,
  List,
  ListItem,
  ListItemAvatar,
  ListItemText,
  Divider,
  CircularProgress,
  IconButton,
  Menu,
  MenuItem,
  Tooltip,
  Collapse,
} from '@mui/material';
import {
  Send,
  ChatBubbleOutlined,
  MoreVert,
  Edit,
  Delete,
  EmojiEmotions,
  TextSnippet,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { commentService, type CommentResponse, type CommentType } from '../../api/commentService';
import { type RootState } from '../../store';
import { useConfirmDialog } from '../../contexts/ConfirmDialogContext';
import toast from 'react-hot-toast';

interface CommentSectionProps {
  sourceId: string;
  /** Controlled mode: parent owns the expand/collapse state (e.g. PostCard's Comment action)
   *  and hides the internal toggle button. Omit for the standalone/uncontrolled usage. */
  open?: boolean;
}

const CommentSection: React.FC<CommentSectionProps> = ({ sourceId, open }) => {
  const { t } = useTranslation();
  const user = useSelector((state: RootState) => state.auth.user);
  const confirmDialog = useConfirmDialog();
  const isControlled = open !== undefined;

  const [comments, setComments] = useState<CommentResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const [uncontrolledOpen, setUncontrolledOpen] = useState(false);
  const [newComment, setNewComment] = useState('');
  const [commentType, setCommentType] = useState<CommentType>('TEXT');
  const [page, setPage] = useState(1);
  const [hasMore, setHasMore] = useState(true);

  const [editingId, setEditingId] = useState<string | null>(null);
  const [editContent, setEditContent] = useState('');
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
  const [selectedCommentId, setSelectedCommentId] = useState<string | null>(null);

  const showComments = isControlled ? Boolean(open) : uncontrolledOpen;

  const fetchComments = async (pageNum: number) => {
    setLoading(true);
    try {
      const response = await commentService.getComments(sourceId, pageNum, 10);
      const newComments = response.result.data;

      if (pageNum === 1) {
        setComments(newComments);
      } else {
        setComments((prev) => [...prev, ...newComments]);
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
    if (!newComment.trim()) return;

    try {
      const response = await commentService.createComment({
        sourceId,
        content: newComment,
        type: commentType,
        listIdsJoin: [],
      });

      setComments((prev) => [response.result, ...prev]);
      setNewComment('');
      toast.success(t('commentCreated'));
    } catch {
      toast.error(t('commentCreateFailed'));
    }
  };

  const handleUpdateComment = async () => {
    if (!editingId || !editContent.trim()) return;

    try {
      const response = await commentService.updateComment(editingId, {
        content: editContent,
      });

      setComments((prev) => prev.map((c) => (c.id === editingId ? response.result : c)));
      setEditingId(null);
      setEditContent('');
      toast.success(t('commentUpdated'));
    } catch {
      toast.error(t('commentUpdateFailed'));
    }
  };

  const handleDeleteComment = async (id: string) => {
    const confirmed = await confirmDialog({
      title: t('deleteCommentTitle'),
      message: t('deleteCommentMessage'),
      confirmText: t('delete'),
    });
    if (!confirmed) return;

    try {
      await commentService.deleteComment(id);
      setComments((prev) => prev.filter((c) => c.id !== id));
      toast.success(t('commentDeleted'));
    } catch {
      toast.error(t('commentDeleteFailed'));
    }
  };

  const handleMenuOpen = (event: React.MouseEvent<HTMLElement>, commentId: string) => {
    setAnchorEl(event.currentTarget);
    setSelectedCommentId(commentId);
  };

  const handleMenuClose = () => {
    setAnchorEl(null);
    setSelectedCommentId(null);
  };

  const handleEditClick = (comment: CommentResponse) => {
    setEditingId(comment.id);
    setEditContent(comment.content);
    handleMenuClose();
  };

  const loadMore = () => {
    const nextPage = page + 1;
    setPage(nextPage);
    fetchComments(nextPage);
  };

  const getStatusLabel = (status: string) => {
    switch (status) {
      case 'EDITED':
        return t('editedLabel');
      default:
        return '';
    }
  };

  return (
    <Box sx={{ mt: isControlled ? 0 : 2 }}>
      {!isControlled && (
        <Button startIcon={<ChatBubbleOutlined />} onClick={handleToggleComments} sx={{ mb: 1 }}>
          {t('commentsLabel')} ({comments.length || 0})
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
            <Box sx={{ display: 'flex', gap: 2 }}>
              <TextField
                fullWidth
                size="small"
                placeholder={commentType === 'TEXT' ? t('writeCommentPlaceholder') : t('writeIconCommentPlaceholder')}
                value={newComment}
                onChange={(e) => setNewComment(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && handleCreateComment()}
              />
              <IconButton color="primary" onClick={handleCreateComment} disabled={!newComment.trim()}>
                <Send />
              </IconButton>
            </Box>
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
            <List>
              {comments.map((comment) => (
                <React.Fragment key={comment.id}>
                  <ListItem alignItems="flex-start" sx={{ px: 0 }}>
                    <ListItemAvatar>
                      <Avatar src={comment.avatar} slotProps={{ img: { loading: 'lazy' } }} />
                    </ListItemAvatar>
                    <ListItemText
                      primary={
                        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                            <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                              {comment.displayName || t('anonymousUser')}
                            </Typography>
                            <Typography variant="caption" color="text.secondary">
                              • {comment.durationCreatedDate}
                            </Typography>
                            {comment.status === 'EDITED' && (
                              <Typography variant="caption" sx={{ fontStyle: 'italic', color: 'text.secondary' }}>
                                ({getStatusLabel(comment.status)})
                              </Typography>
                            )}
                          </Box>
                          {user?.id === comment.userId && (
                            <IconButton size="small" onClick={(e) => handleMenuOpen(e, comment.id)}>
                              <MoreVert fontSize="small" />
                            </IconButton>
                          )}
                        </Box>
                      }
                      secondary={
                        editingId === comment.id ? (
                          <Box sx={{ mt: 1, display: 'flex', gap: 1 }}>
                            <TextField
                              fullWidth
                              size="small"
                              value={editContent}
                              onChange={(e) => setEditContent(e.target.value)}
                              autoFocus
                            />
                            <Button size="small" onClick={handleUpdateComment}>{t('save')}</Button>
                            <Button size="small" color="inherit" onClick={() => setEditingId(null)}>{t('cancel')}</Button>
                          </Box>
                        ) : (
                          <Typography
                            variant="body2"
                            color="text.primary"
                            sx={{ mt: 0.5, display: 'flex', alignItems: 'center', gap: 1 }}
                          >
                            {comment.type === 'ICON' && <EmojiEmotions fontSize="small" color="primary" />}
                            {comment.content}
                          </Typography>
                        )
                      }
                    />
                  </ListItem>
                  <Divider variant="inset" component="li" sx={{ ml: 7 }} />
                </React.Fragment>
              ))}
            </List>
          )}

          <Menu anchorEl={anchorEl} open={Boolean(anchorEl)} onClose={handleMenuClose}>
            <MenuItem
              onClick={() => {
                const comment = comments.find((c) => c.id === selectedCommentId);
                if (comment && comment.type === 'TEXT') {
                  handleEditClick(comment);
                } else {
                  toast.error(t('textOnlyCommentsEditable'));
                  handleMenuClose();
                }
              }}
            >
              <Edit fontSize="small" sx={{ mr: 1 }} /> {t('edit')}
            </MenuItem>
            <MenuItem
              onClick={() => {
                if (selectedCommentId) handleDeleteComment(selectedCommentId);
                handleMenuClose();
              }}
              sx={{ color: 'error.main' }}
            >
              <Delete fontSize="small" sx={{ mr: 1 }} /> {t('delete')}
            </MenuItem>
          </Menu>

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
