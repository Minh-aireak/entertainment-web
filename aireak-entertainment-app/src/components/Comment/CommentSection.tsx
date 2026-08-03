import React, { useState } from 'react';
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
  Tooltip
} from '@mui/material';
import { 
  Send, 
  ChatBubbleOutlined, 
  MoreVert, 
  Edit, 
  Delete,
  EmojiEmotions,
  TextSnippet
} from '@mui/icons-material';
import { commentService, type CommentResponse, type CommentType } from '../../api/commentService';
import { type RootState } from '../../store';
import toast from 'react-hot-toast';

interface CommentSectionProps {
  sourceId: string;
}

const CommentSection: React.FC<CommentSectionProps> = ({ sourceId }) => {
  const user = useSelector((state: RootState) => state.auth.user);
  const [comments, setComments] = useState<CommentResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [showComments, setShowComments] = useState(false);
  const [newComment, setNewComment] = useState('');
  const [commentType, setCommentType] = useState<CommentType>('TEXT');
  const [page, setPage] = useState(1);
  const [hasMore, setHasMore] = useState(true);
  
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editContent, setEditContent] = useState('');
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
  const [selectedCommentId, setSelectedCommentId] = useState<string | null>(null);

  const fetchComments = async (pageNum: number) => {
    setLoading(true);
    try {
      const response = await commentService.getComments(sourceId, pageNum, 10);
      const newComments = response.result.data;
      
      if (pageNum === 1) {
        setComments(newComments);
      } else {
        setComments(prev => [...prev, ...newComments]);
      }
      
      setHasMore(pageNum < (response?.result?.totalPages || 0));
    } catch (error) {
      console.error('Failed to fetch comments:', error);
      toast.error('Không thể tải bình luận');
    } finally {
      setLoading(false);
    }
  };

  const handleToggleComments = () => {
    if (!showComments) {
      fetchComments(1);
      setPage(1);
    }
    setShowComments(!showComments);
  };

  const handleCreateComment = async () => {
    if (!newComment.trim()) return;
    
    try {
      const response = await commentService.createComment({
        sourceId,
        content: newComment,
        type: commentType,
        listIdsJoin: []
      });
      
      setComments(prev => [response.result, ...prev]);
      setNewComment('');
      toast.success('Đã gửi bình luận');
    } catch (error) {
      toast.error('Không thể gửi bình luận');
    }
  };

  const handleUpdateComment = async () => {
    if (!editingId || !editContent.trim()) return;
    
    try {
      const response = await commentService.updateComment(editingId, {
        content: editContent
      });
      
      setComments(prev => prev.map(c => c.id === editingId ? response.result : c));
      setEditingId(null);
      setEditContent('');
      toast.success('Đã cập nhật bình luận');
    } catch (error) {
      toast.error('Không thể cập nhật bình luận');
    }
  };

  const handleDeleteComment = async (id: string) => {
    if (!window.confirm('Bạn có chắc chắn muốn xóa bình luận này?')) return;
    
    try {
      await commentService.deleteComment(id);
      setComments(prev => prev.filter(c => c.id !== id));
      toast.success('Đã xóa bình luận');
    } catch (error) {
      toast.error('Không thể xóa bình luận');
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
      case 'EDITED': return 'Đã chỉnh sửa';
      default: return '';
    }
  };

  return (
    <Box sx={{ mt: 2 }}>
      <Button 
        startIcon={<ChatBubbleOutlined />} 
        onClick={handleToggleComments}
        sx={{ mb: 1 }}
      >
        Bình luận ({comments.length || 0})
      </Button>

      {showComments && (
        <Box sx={{ mt: 2, pl: 2, borderLeft: '2px solid rgba(255,255,255,0.1)' }}>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1, mb: 3 }}>
            <Box sx={{ display: 'flex', gap: 1, mb: 1 }}>
              <Tooltip title="Bình luận văn bản">
                <IconButton 
                  size="small" 
                  color={commentType === 'TEXT' ? 'primary' : 'default'}
                  onClick={() => setCommentType('TEXT')}
                >
                  <TextSnippet />
                </IconButton>
              </Tooltip>
              <Tooltip title="Bình luận biểu tượng">
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
                placeholder={commentType === 'TEXT' ? "Viết bình luận..." : "Nhập mã biểu tượng..."}
                value={newComment}
                onChange={(e) => setNewComment(e.target.value)}
                onKeyPress={(e) => e.key === 'Enter' && handleCreateComment()}
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
          ) : (
            <List>
              {comments.map((comment) => (
                <React.Fragment key={comment.id}>
                  <ListItem alignItems="flex-start" sx={{ px: 0 }}>
                    <ListItemAvatar>
                      <Avatar src={comment.avatar} />
                    </ListItemAvatar>
                    <ListItemText
                      primary={
                        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                            <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                              {comment.displayName || 'Người dùng'}
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
                            <Button size="small" onClick={handleUpdateComment}>Lưu</Button>
                            <Button size="small" color="inherit" onClick={() => setEditingId(null)}>Hủy</Button>
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

          <Menu
            anchorEl={anchorEl}
            open={Boolean(anchorEl)}
            onClose={handleMenuClose}
          >
            <MenuItem onClick={() => {
              const comment = comments.find(c => c.id === selectedCommentId);
              if (comment && comment.type === 'TEXT') {
                handleEditClick(comment);
              } else {
                toast.error('Chỉ có thể chỉnh sửa bình luận văn bản');
                handleMenuClose();
              }
            }}>
              <Edit fontSize="small" sx={{ mr: 1 }} /> Sửa
            </MenuItem>
            <MenuItem onClick={() => {
              if (selectedCommentId) handleDeleteComment(selectedCommentId);
              handleMenuClose();
            }} sx={{ color: 'error.main' }}>
              <Delete fontSize="small" sx={{ mr: 1 }} /> Xóa
            </MenuItem>
          </Menu>

          {hasMore && !loading && (
            <Button size="small" onClick={loadMore} sx={{ mt: 1 }}>
              Xem thêm bình luận
            </Button>
          )}
          
          {loading && page > 1 && (
             <Box sx={{ display: 'flex', justifyContent: 'center', py: 1 }}>
               <CircularProgress size={20} />
             </Box>
          )}
        </Box>
      )}
    </Box>
  );
};

export default CommentSection;
