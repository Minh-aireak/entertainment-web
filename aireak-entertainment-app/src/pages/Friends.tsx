import React, { useState, useEffect, useCallback } from 'react';
import {
  Box,
  Typography,
  Grid,
  Card,
  CardContent,
  Button,
  TextField,
  InputAdornment,
  Tabs,
  Tab,
  CircularProgress,
} from '@mui/material';
import { Search, PersonRemove, Chat, Person, PersonAdd, Cancel } from '@mui/icons-material';
import { toast } from 'react-hot-toast';
import { useNavigate } from 'react-router-dom';
import { friendService } from '../api/friendService';
import { REALTIME_FRIENDSHIP_EVENT } from '../contexts/WebSocketContext';
import { useConfirmDialog } from '../contexts/ConfirmDialogContext';
import type { UserRelationshipResponse, FriendRequestResponse, UserProfileResponse } from '../models';

interface FriendUI {
  id: string;
  fullName: string;
  avatar?: string;
  status: 'FRIEND' | 'PENDING_SENT' | 'PENDING_RECEIVED' | 'SUGGESTION' | 'BLOCKED';
}

const FriendsPage: React.FC = React.memo(() => {
  const navigate = useNavigate();
  const confirmDialog = useConfirmDialog();
  const [tabValue, setTabValue] = useState(0);
  const [searchQuery, setSearchQuery] = useState('');
  const [loading, setLoading] = useState(false);

  const [friends, setFriends] = useState<FriendUI[]>([]);
  const [suggestions, setSuggestions] = useState<FriendUI[]>([]);
  const [receivedRequests, setReceivedRequests] = useState<FriendUI[]>([]);
  const [sentRequests, setSentRequests] = useState<FriendUI[]>([]);

  const fetchAllFriendData = useCallback(async () => {
    setLoading(true);
    try {
      const results = await Promise.allSettled([
        friendService.getMyFriends(1, 10),
        friendService.getMyFriendRequests(1, 10),
        friendService.getFriendSuggestions(1, 8),
      ]);

      // Handle Friends
      if (results[0].status === 'fulfilled' && results[0].value.code === 1000) {
        const friendData = results[0].value.result?.data ?? [];
        setFriends(friendData.map((f: UserRelationshipResponse) => ({
          id: f.friendId,
          fullName: f.displayName,
          avatar: f.friendAvatar,
          status: 'FRIEND',
        })));
      } else if (results[0].status === 'rejected') {
        console.error('Failed to fetch friends:', results[0].reason);
      }

      // Handle Requests
      if (results[1].status === 'fulfilled' && results[1].value.code === 1000) {
        const requestData = results[1].value.result?.data ?? [];
        const received = requestData.filter((r: FriendRequestResponse) => r.status === 'PENDING');
        setReceivedRequests(received.map((r: FriendRequestResponse) => ({
          id: r.senderId,
          fullName: r.displayName,
          avatar: r.avatar,
          status: 'PENDING_RECEIVED',
        })));
      } else if (results[1].status === 'rejected') {
        console.error('Failed to fetch requests:', results[1].reason);
      }

      // Handle Suggestions
      if (results[2].status === 'fulfilled' && results[2].value.code === 1000) {
        const suggestionData = results[2].value.result?.data ?? [];
        setSuggestions(suggestionData.map((p: UserProfileResponse) => ({
          id: p.userId,
          fullName: p.displayName,
          avatar: p.avatar,
          status: 'SUGGESTION',
        })));
      } else if (results[2].status === 'rejected') {
        console.error('Failed to fetch suggestions:', results[2].reason);
      }

    } catch (error) {
      console.error('Failed to fetch friend data:', error);
      toast.error('Có lỗi xảy ra khi tải dữ liệu');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchAllFriendData();
  }, [fetchAllFriendData]);

  useEffect(() => {
    const refreshFriendData = () => fetchAllFriendData();
    window.addEventListener(REALTIME_FRIENDSHIP_EVENT, refreshFriendData);
    return () => window.removeEventListener(REALTIME_FRIENDSHIP_EVENT, refreshFriendData);
  }, [fetchAllFriendData]);

  const handleTabChange = (_event: React.SyntheticEvent, newValue: number) => {
    setTabValue(newValue);
  };

  const getFilteredData = () => {
    let data: FriendUI[] = [];
    if (tabValue === 0) data = friends;
    else if (tabValue === 1) data = suggestions;
    else if (tabValue === 2) data = receivedRequests;
    else if (tabValue === 3) data = sentRequests;

    return data.filter(f =>
      f.fullName.toLowerCase().includes(searchQuery.toLowerCase())
    );
  };

  const handleAccept = async (id: string) => {
    try {
      const res = await friendService.friendRequestStatus(id, 'ACCEPTED');
      if (res.code === 1000) {
        toast.success('Đã chấp nhận lời mời kết bạn');
        fetchAllFriendData();
      }
    } catch (error) {
      toast.error('Thao tác thất bại');
    }
  };

  const handleDecline = async (id: string) => {
    try {
      const res = await friendService.friendRequestStatus(id, 'CANCEL');
      if (res.code === 1000) {
        toast.success('Đã từ chối lời mời');
        fetchAllFriendData();
      }
    } catch (error) {
      toast.error('Thao tác thất bại');
    }
  };

  const handleAddFriend = async (id: string) => {
    try {
      const res = await friendService.sendFriendRequest(id);
      if (res.code === 1000) {
        toast.success('Đã gửi lời mời kết bạn');
        // Move from suggestion to sent requests locally for immediate feedback
        const user = suggestions.find(s => s.id === id);
        if (user) {
          setSuggestions(prev => prev.filter(s => s.id !== id));
          setSentRequests(prev => [...prev, { ...user, status: 'PENDING_SENT' }]);
        }
      }
    } catch (error) {
      toast.error('Không thể gửi lời mời');
    }
  };

  const handleRemoveFriend = async (id: string) => {
    const confirmed = await confirmDialog({
      title: 'Hủy kết bạn',
      message: 'Bạn có chắc chắn muốn hủy kết bạn?',
      confirmText: 'Hủy kết bạn',
    });
    if (!confirmed) return;

    try {
      const res = await friendService.updateRelationshipStatus(id, 'UNFRIEND');
      if (res.code === 1000) {
        toast.success('Đã hủy kết bạn');
        setFriends(prev => prev.filter(f => f.id !== id));
      }
    } catch (error) {
      toast.error('Thao tác thất bại');
    }
  };

  const handleMessageFriend = (id: string) => {
    navigate('/social/chat', { state: { openWithUserId: id } });
  };

  const filteredData = getFilteredData();

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'flex-end', alignItems: 'center', mb: 4 }}>
        <Box sx={{ display: 'flex', gap: 2 }}>
          <TextField
            size="small"
            placeholder="Tìm kiếm..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            slotProps={{
              input: {
                startAdornment: (
                  <InputAdornment position="start">
                    <Search fontSize="small" />
                  </InputAdornment>
                ),
              },
            }}
            sx={{ width: 300 }}
          />
        </Box>
      </Box>

      <Box sx={{ borderBottom: 1, borderColor: 'divider', mb: 3 }}>
        <Tabs value={tabValue} onChange={handleTabChange} textColor="primary" indicatorColor="primary">
          <Tab label={`Bạn bè (${friends.length})`} />
          <Tab label={`Gợi ý (${suggestions.length})`} />
          <Tab label={`Lời mời đã nhận (${receivedRequests.length})`} />
          <Tab label={`Lời mời đã gửi (${sentRequests.length})`} />
        </Tabs>
      </Box>

      <Grid container spacing={2}>
        {loading ? (
          <Grid size={{ xs: 12 }}>
            <Box sx={{ display: 'flex', justifyContent: 'center', py: 8 }}>
              <CircularProgress />
            </Box>
          </Grid>
        ) : filteredData.length > 0 ? (
          filteredData.map((item) => (
            <Grid size={{ xs: 12, sm: 6, md: 4, lg: 3 }} key={item.id}>
              <Card
                sx={{
                  borderRadius: 3,
                  bgcolor: 'background.paper',
                  height: '100%',
                  display: 'flex',
                  flexDirection: 'column',
                  overflow: 'hidden',
                  transition: 'all 0.3s ease',
                  '&:hover': { transform: 'translateY(-4px)', boxShadow: 4 },
                }}
              >
                <Box sx={{ position: 'relative', pt: '100%', bgcolor: 'action.hover' }}>
                  {item.avatar ? (
                    <Box
                      component="img"
                      src={item.avatar}
                      sx={{ position: 'absolute', top: 0, left: 0, width: '100%', height: '100%', objectFit: 'cover' }}
                    />
                  ) : (
                    <Box sx={{ position: 'absolute', top: 0, left: 0, width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                      <Person sx={{ fontSize: 80, color: 'text.disabled' }} />
                    </Box>
                  )}
                </Box>

                <CardContent sx={{ p: 2, flexGrow: 1, display: 'flex', flexDirection: 'column', gap: 2 }}>
                  <Typography variant="subtitle1" noWrap sx={{ fontWeight: 'bold' }}>
                    {item.fullName}
                  </Typography>

                  <Box sx={{ mt: 'auto', display: 'flex', flexDirection: 'column', gap: 1 }}>
                    {item.status === 'FRIEND' && (
                      <Box sx={{ display: 'flex', gap: 1 }}>
                        <Button fullWidth variant="contained" startIcon={<Chat />} onClick={() => handleMessageFriend(item.id)} size="small">Nhắn tin</Button>
                        <Button variant="outlined" color="error" onClick={() => handleRemoveFriend(item.id)} size="small">
                          <PersonRemove fontSize="small" />
                        </Button>
                      </Box>
                    )}

                    {item.status === 'SUGGESTION' && (
                      <Button fullWidth variant="contained" startIcon={<PersonAdd />} onClick={() => handleAddFriend(item.id)} size="small">
                        Thêm bạn bè
                      </Button>
                    )}

                    {item.status === 'PENDING_RECEIVED' && (
                      <Box sx={{ display: 'flex', gap: 1 }}>
                        <Button fullWidth variant="contained" onClick={() => handleAccept(item.id)} size="small">Chấp nhận</Button>
                        <Button fullWidth variant="outlined" color="inherit" onClick={() => handleDecline(item.id)} size="small">Xóa</Button>
                      </Box>
                    )}

                    {item.status === 'PENDING_SENT' && (
                      <Button fullWidth variant="outlined" disabled startIcon={<Cancel />} size="small">
                        Đã gửi yêu cầu
                      </Button>
                    )}
                  </Box>
                </CardContent>
              </Card>
            </Grid>
          ))
        ) : (
          <Grid size={{ xs: 12 }}>
            <Box sx={{ textAlign: 'center', py: 8, color: 'text.secondary' }}>
              <Typography variant="body1">Không tìm thấy kết quả</Typography>
            </Box>
          </Grid>
        )}
      </Grid>
    </Box>
  );
});

export default FriendsPage;
