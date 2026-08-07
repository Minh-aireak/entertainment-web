import React, { useState, useEffect, useCallback, useMemo } from 'react';
import {
  Box,
  Typography,
  Grid,
  Card,
  Button,
  TextField,
  InputAdornment,
  Tabs,
  Tab,
  Avatar,
  Badge,
  IconButton,
  Menu,
  MenuItem,
  ListItemIcon,
  ListItemText,
  Tooltip,
  Skeleton,
  styled,
  alpha,
} from '@mui/material';
import {
  Search, Close, PersonAdd, PersonRemove, Chat, MoreVert, Check,
  HourglassTop, Groups, PersonSearch, MailOutlined, Send, SearchOff,
  Person, PeopleAlt,
} from '@mui/icons-material';
import { keyframes } from '@emotion/react';
import { toast } from 'react-hot-toast';
import { useNavigate } from 'react-router-dom';
import { useSelector } from 'react-redux';
import { friendService } from '../api/friendService';
import { REALTIME_FRIENDSHIP_EVENT } from '../contexts/WebSocketContext';
import { useConfirmDialog } from '../contexts/ConfirmDialogContext';
import type { RootState } from '../store/index';
import type { UserRelationshipResponse, FriendRequestResponse, UserProfileResponse } from '../models';

interface FriendUI {
  id: string;
  fullName: string;
  avatar?: string;
  status: 'FRIEND' | 'PENDING_SENT' | 'PENDING_RECEIVED' | 'SUGGESTION' | 'BLOCKED';
}

const AVATAR_PALETTE = ['#00A84E', '#2D88FF', '#FF6B6B', '#FFA726', '#AB47BC', '#26C6DA', '#EC407A', '#7E57C2'];
const stringToColor = (str: string) => {
  let hash = 0;
  for (let i = 0; i < str.length; i++) hash = str.charCodeAt(i) + ((hash << 5) - hash);
  return AVATAR_PALETTE[Math.abs(hash) % AVATAR_PALETTE.length];
};

// Sent requests have no listing endpoint on the backend, so we keep an optimistic,
// per-user cache in sessionStorage — survives a page refresh within the same tab/session
// without risking long-term staleness if a request later gets declined server-side.
const SENT_REQUESTS_KEY_PREFIX = 'friends:sentRequests:';

const loadSentRequests = (userId?: string | null): FriendUI[] => {
  if (!userId) return [];
  try {
    const raw = sessionStorage.getItem(SENT_REQUESTS_KEY_PREFIX + userId);
    return raw ? JSON.parse(raw) : [];
  } catch {
    return [];
  }
};

const saveSentRequests = (userId: string | undefined | null, list: FriendUI[]) => {
  if (!userId) return;
  try {
    sessionStorage.setItem(SENT_REQUESTS_KEY_PREFIX + userId, JSON.stringify(list));
  } catch {
    // ignore storage failures (private mode, quota)
  }
};

const fadeInUp = keyframes`
  from { opacity: 0; transform: translateY(14px) scale(0.98); }
  to { opacity: 1; transform: translateY(0) scale(1); }
`;

const LiveBadge = styled(Badge)(({ theme }) => ({
  '& .MuiBadge-badge': {
    backgroundColor: '#44b700',
    color: '#44b700',
    boxShadow: `0 0 0 3px ${theme.palette.background.paper}`,
    '&::after': {
      position: 'absolute',
      top: 0,
      left: 0,
      width: '100%',
      height: '100%',
      borderRadius: '50%',
      animation: 'friendsLiveRipple 1.2s infinite ease-in-out',
      border: '1px solid currentColor',
      content: '""',
    },
  },
  '@keyframes friendsLiveRipple': {
    '0%': { transform: 'scale(.8)', opacity: 1 },
    '100%': { transform: 'scale(2.2)', opacity: 0 },
  },
}));

interface TabDef {
  label: string;
  icon: typeof Groups;
  count: number;
  urgent?: boolean;
}

const tabPillSx = {
  textTransform: 'none' as const,
  fontWeight: 700,
  fontSize: '0.875rem',
  minHeight: 42,
  borderRadius: 999,
  px: 2,
  py: 1,
  color: 'text.secondary',
  gap: 1,
  transition: 'all 0.2s ease',
  '&.Mui-selected': {
    color: '#fff',
    bgcolor: 'primary.main',
  },
};

const EMPTY_STATE_CONFIG: Record<number, { icon: typeof Groups; title: string; subtitle: string; ctaLabel?: string }> = {
  0: {
    icon: PeopleAlt,
    title: 'Bạn chưa có người bạn nào',
    subtitle: 'Kết nối với mọi người để bắt đầu trò chuyện và chia sẻ khoảnh khắc cùng nhau.',
    ctaLabel: 'Khám phá gợi ý',
  },
  1: {
    icon: PersonSearch,
    title: 'Chưa có gợi ý nào lúc này',
    subtitle: 'Hãy quay lại sau, chúng tôi sẽ tìm thêm những người bạn có thể biết.',
  },
  2: {
    icon: MailOutlined,
    title: 'Không có lời mời kết bạn nào',
    subtitle: 'Các lời mời kết bạn gửi đến bạn sẽ xuất hiện tại đây.',
  },
  3: {
    icon: Send,
    title: 'Bạn chưa gửi lời mời nào',
    subtitle: 'Những lời mời bạn gửi trong phiên làm việc này sẽ hiển thị tại đây.',
  },
};

const FriendsPage: React.FC = React.memo(() => {
  const navigate = useNavigate();
  const confirmDialog = useConfirmDialog();
  const currentUserId = useSelector((state: RootState) => state.auth.user?.id);
  const onlineUserIds = useSelector((state: RootState) => state.chat.onlineUsers);

  const [tabValue, setTabValue] = useState(0);
  const [searchQuery, setSearchQuery] = useState('');
  const [loading, setLoading] = useState(false);

  const [friends, setFriends] = useState<FriendUI[]>([]);
  const [suggestions, setSuggestions] = useState<FriendUI[]>([]);
  const [receivedRequests, setReceivedRequests] = useState<FriendUI[]>([]);
  const [sentRequests, setSentRequests] = useState<FriendUI[]>([]);

  const [menuAnchor, setMenuAnchor] = useState<null | HTMLElement>(null);
  const [menuTargetId, setMenuTargetId] = useState<string | null>(null);

  useEffect(() => {
    setSentRequests(loadSentRequests(currentUserId));
  }, [currentUserId]);

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

        // A pending sent request resolves into a friendship once accepted —
        // drop it from the locally-cached "sent" list so it doesn't linger.
        const friendIdSet = new Set(friendData.map((f: UserRelationshipResponse) => f.friendId));
        setSentRequests(prev => {
          const stillPending = prev.filter(s => !friendIdSet.has(s.id));
          if (stillPending.length !== prev.length) saveSentRequests(currentUserId, stillPending);
          return stillPending;
        });
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
  }, [currentUserId]);

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

  const sortedFriends = useMemo(() => {
    return [...friends].sort((a, b) => {
      const aOnline = onlineUserIds.includes(a.id) ? 1 : 0;
      const bOnline = onlineUserIds.includes(b.id) ? 1 : 0;
      return bOnline - aOnline;
    });
  }, [friends, onlineUserIds]);

  const onlineFriendsCount = useMemo(
    () => friends.filter(f => onlineUserIds.includes(f.id)).length,
    [friends, onlineUserIds]
  );

  const tabSource = [friends, suggestions, receivedRequests, sentRequests];

  const getFilteredData = () => {
    let data: FriendUI[] = [];
    if (tabValue === 0) data = sortedFriends;
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
          setSentRequests(prev => {
            const next = [...prev, { ...user, status: 'PENDING_SENT' as const }];
            saveSentRequests(currentUserId, next);
            return next;
          });
        }
      }
    } catch (error) {
      toast.error('Không thể gửi lời mời');
    }
  };

  const handleDismissSuggestion = (id: string) => {
    setSuggestions(prev => prev.filter(s => s.id !== id));
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

  const openMenu = (event: React.MouseEvent<HTMLElement>, id: string) => {
    event.stopPropagation();
    setMenuAnchor(event.currentTarget);
    setMenuTargetId(id);
  };

  const closeMenu = () => {
    setMenuAnchor(null);
    setMenuTargetId(null);
  };

  const filteredData = getFilteredData();
  const currentTabHasData = tabSource[tabValue].length > 0;
  const isSearchingWithNoResults = searchQuery.trim() !== '' && currentTabHasData && filteredData.length === 0;

  const tabDefs: TabDef[] = [
    { label: 'Bạn bè', icon: PeopleAlt, count: friends.length },
    { label: 'Gợi ý', icon: PersonSearch, count: suggestions.length },
    { label: 'Lời mời đã nhận', icon: MailOutlined, count: receivedRequests.length, urgent: true },
    { label: 'Lời mời đã gửi', icon: Send, count: sentRequests.length },
  ];

  return (
    <Box>
      <Box sx={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: { xs: 'flex-start', sm: 'center' }, gap: 2, mb: 3 }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 800, letterSpacing: '-0.02em', display: 'flex', alignItems: 'center', gap: 1.5 }}>
            <Box sx={{
              width: 44, height: 44, borderRadius: '14px', display: 'grid', placeItems: 'center',
              background: 'linear-gradient(135deg, #00A84E 0%, #00c75c 100%)',
              boxShadow: '0 4px 14px rgba(0, 168, 78, 0.3)', flexShrink: 0,
            }}>
              <Groups sx={{ color: '#fff', fontSize: 24 }} />
            </Box>
            Bạn bè
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.75, ml: '58px' }}>
            {friends.length} người bạn
            {onlineFriendsCount > 0 && (
              <>
                {' • '}
                <Box component="span" sx={{ color: '#44b700', fontWeight: 700 }}>
                  {onlineFriendsCount} đang hoạt động
                </Box>
              </>
            )}
          </Typography>
        </Box>

        <TextField
          size="small"
          placeholder="Tìm kiếm bạn bè..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          slotProps={{
            input: {
              startAdornment: (
                <InputAdornment position="start">
                  <Search fontSize="small" />
                </InputAdornment>
              ),
              endAdornment: searchQuery ? (
                <InputAdornment position="end">
                  <IconButton size="small" onClick={() => setSearchQuery('')}>
                    <Close fontSize="small" />
                  </IconButton>
                </InputAdornment>
              ) : undefined,
            },
          }}
          sx={{
            width: { xs: '100%', sm: 320 },
            '& .MuiOutlinedInput-root': {
              borderRadius: 999,
              bgcolor: 'action.hover',
              '& fieldset': { border: 'none' },
              '&:hover': { bgcolor: 'action.selected' },
              '&.Mui-focused': {
                bgcolor: 'background.paper',
                boxShadow: (theme) => `0 0 0 2px ${theme.palette.primary.main}`,
              },
            },
          }}
        />
      </Box>

      <Box sx={{
        mb: 3, display: 'inline-flex', maxWidth: '100%', overflow: 'auto',
        p: 0.5, borderRadius: 999, bgcolor: 'action.hover',
      }}>
        <Tabs
          value={tabValue}
          onChange={handleTabChange}
          variant="scrollable"
          scrollButtons={false}
          slotProps={{ indicator: { style: { display: 'none' } } }}
          sx={{ minHeight: 'auto', '& .MuiTabs-flexContainer': { gap: 0.5 } }}
        >
          {tabDefs.map((tab, idx) => (
            <Tab
              key={tab.label}
              disableRipple
              icon={<tab.icon fontSize="small" />}
              iconPosition="start"
              sx={tabPillSx}
              label={
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
                  {tab.label}
                  <Box component="span" sx={{
                    px: 0.9, py: 0.1, borderRadius: 999, fontSize: '0.72rem', fontWeight: 800,
                    minWidth: 20, textAlign: 'center', lineHeight: 1.6,
                    bgcolor: tab.urgent && tab.count > 0 ? 'error.main' : (tabValue === idx ? 'rgba(255,255,255,0.25)' : 'action.selected'),
                    color: tab.urgent && tab.count > 0 ? '#fff' : (tabValue === idx ? '#fff' : 'text.secondary'),
                  }}>
                    {tab.count}
                  </Box>
                </Box>
              }
            />
          ))}
        </Tabs>
      </Box>

      <Grid container spacing={2.5}>
        {loading ? (
          Array.from({ length: 8 }).map((_, i) => (
            <Grid size={{ xs: 12, sm: 6, md: 4, lg: 3 }} key={i}>
              <Card sx={{ borderRadius: 4, p: 3, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 1.5 }}>
                <Skeleton variant="circular" width={88} height={88} />
                <Skeleton variant="text" width="70%" height={28} />
                <Skeleton variant="text" width="40%" height={18} />
                <Skeleton variant="rounded" width="100%" height={36} sx={{ borderRadius: 999, mt: 1 }} />
              </Card>
            </Grid>
          ))
        ) : filteredData.length > 0 ? (
          filteredData.map((item, idx) => {
            const isOnline = item.status === 'FRIEND' && onlineUserIds.includes(item.id);
            const avatarNode = (
              <Avatar
                src={item.avatar}
                sx={{
                  width: 88, height: 88, fontSize: '2rem', fontWeight: 700,
                  bgcolor: item.avatar ? undefined : stringToColor(item.id),
                  border: '3px solid',
                  borderColor: item.status === 'PENDING_RECEIVED' ? '#f39c12' : 'background.default',
                  boxShadow: '0 2px 8px rgba(0,0,0,0.08)',
                }}
              >
                {!item.avatar && (item.fullName?.[0]?.toUpperCase() || <Person />)}
              </Avatar>
            );

            return (
              <Grid size={{ xs: 12, sm: 6, md: 4, lg: 3 }} key={item.id}>
                <Card
                  sx={{
                    borderRadius: 4,
                    bgcolor: 'background.paper',
                    height: '100%',
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    textAlign: 'center',
                    p: 2.5,
                    pt: 3,
                    position: 'relative',
                    overflow: 'visible',
                    border: '1px solid',
                    borderColor: item.status === 'PENDING_RECEIVED' ? alpha('#f39c12', 0.4) : 'divider',
                    transition: 'all 0.25s cubic-bezier(0.4, 0, 0.2, 1)',
                    animation: `${fadeInUp} 0.45s cubic-bezier(0.4, 0, 0.2, 1) both`,
                    animationDelay: `${Math.min(idx * 35, 350)}ms`,
                    '&:hover': {
                      transform: 'translateY(-6px)',
                      boxShadow: (theme) => theme.palette.mode === 'dark'
                        ? '0 12px 28px rgba(0,168,78,0.18)'
                        : '0 12px 28px rgba(0,168,78,0.12)',
                      borderColor: 'primary.main',
                    },
                  }}
                >
                  {item.status === 'SUGGESTION' && (
                    <Tooltip title="Ẩn gợi ý này">
                      <IconButton
                        size="small"
                        onClick={() => handleDismissSuggestion(item.id)}
                        sx={{
                          position: 'absolute', top: 8, right: 8, color: 'text.disabled',
                          '&:hover': { color: 'text.secondary', bgcolor: 'action.hover' },
                        }}
                      >
                        <Close fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  )}

                  {item.status === 'FRIEND' ? (
                    <LiveBadge overlap="circular" anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }} variant="dot" invisible={!isOnline}>
                      {avatarNode}
                    </LiveBadge>
                  ) : avatarNode}

                  <Typography variant="subtitle1" noWrap sx={{ fontWeight: 700, mt: 1.5, maxWidth: '100%' }}>
                    {item.fullName}
                  </Typography>

                  {item.status === 'FRIEND' && (
                    <Typography variant="caption" sx={{ color: isOnline ? '#44b700' : 'text.secondary', fontWeight: 600, mb: 1.5 }}>
                      {isOnline ? 'Đang hoạt động' : 'Ngoại tuyến'}
                    </Typography>
                  )}
                  {item.status === 'SUGGESTION' && (
                    <Typography variant="caption" color="text.secondary" sx={{ mb: 1.5 }}>
                      Gợi ý kết bạn
                    </Typography>
                  )}
                  {item.status === 'PENDING_RECEIVED' && (
                    <Typography variant="caption" sx={{ color: '#f39c12', fontWeight: 600, mb: 1.5 }}>
                      Muốn kết bạn với bạn
                    </Typography>
                  )}
                  {item.status === 'PENDING_SENT' && (
                    <Typography variant="caption" color="text.secondary" sx={{ mb: 1.5 }}>
                      Đang chờ phản hồi
                    </Typography>
                  )}

                  <Box sx={{ mt: 'auto', width: '100%' }}>
                    {item.status === 'FRIEND' && (
                      <Box sx={{ display: 'flex', gap: 1 }}>
                        <Button
                          fullWidth
                          variant="contained"
                          startIcon={<Chat fontSize="small" />}
                          onClick={() => handleMessageFriend(item.id)}
                          size="small"
                          sx={{ borderRadius: 999 }}
                        >
                          Nhắn tin
                        </Button>
                        <IconButton
                          onClick={(e) => openMenu(e, item.id)}
                          sx={{ border: '1px solid', borderColor: 'divider', flexShrink: 0 }}
                        >
                          <MoreVert fontSize="small" />
                        </IconButton>
                      </Box>
                    )}

                    {item.status === 'SUGGESTION' && (
                      <Button
                        fullWidth
                        variant="contained"
                        startIcon={<PersonAdd fontSize="small" />}
                        onClick={() => handleAddFriend(item.id)}
                        size="small"
                        sx={{ borderRadius: 999 }}
                      >
                        Thêm bạn bè
                      </Button>
                    )}

                    {item.status === 'PENDING_RECEIVED' && (
                      <Box sx={{ display: 'flex', gap: 1 }}>
                        <Button
                          fullWidth
                          variant="contained"
                          startIcon={<Check fontSize="small" />}
                          onClick={() => handleAccept(item.id)}
                          size="small"
                          sx={{ borderRadius: 999 }}
                        >
                          Chấp nhận
                        </Button>
                        <Button
                          fullWidth
                          variant="outlined"
                          color="inherit"
                          onClick={() => handleDecline(item.id)}
                          size="small"
                          sx={{ borderRadius: 999 }}
                        >
                          Xóa
                        </Button>
                      </Box>
                    )}

                    {item.status === 'PENDING_SENT' && (
                      <Button
                        fullWidth
                        variant="outlined"
                        disabled
                        startIcon={<HourglassTop fontSize="small" />}
                        size="small"
                        sx={{ borderRadius: 999 }}
                      >
                        Đã gửi lời mời
                      </Button>
                    )}
                  </Box>
                </Card>
              </Grid>
            );
          })
        ) : (
          <Grid size={{ xs: 12 }}>
            {isSearchingWithNoResults ? (
              <Box sx={{ textAlign: 'center', py: 10, px: 2, bgcolor: 'action.hover', borderRadius: 6, border: '2px dashed', borderColor: 'divider' }}>
                <SearchOff sx={{ fontSize: 72, color: 'text.disabled', mb: 2 }} />
                <Typography variant="h6" sx={{ fontWeight: 700, mb: 1 }}>Không tìm thấy kết quả</Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
                  Không có kết quả nào khớp với "{searchQuery}".
                </Typography>
                <Button variant="outlined" onClick={() => setSearchQuery('')} sx={{ borderRadius: 999, px: 4 }}>
                  Xóa tìm kiếm
                </Button>
              </Box>
            ) : (
              <Box sx={{ textAlign: 'center', py: 10, px: 2, bgcolor: 'action.hover', borderRadius: 6, border: '2px dashed', borderColor: 'divider' }}>
                {React.createElement(EMPTY_STATE_CONFIG[tabValue].icon, { sx: { fontSize: 72, color: 'text.disabled', mb: 2 } })}
                <Typography variant="h6" sx={{ fontWeight: 700, mb: 1 }}>
                  {EMPTY_STATE_CONFIG[tabValue].title}
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ maxWidth: 380, mx: 'auto', mb: EMPTY_STATE_CONFIG[tabValue].ctaLabel ? 3 : 0 }}>
                  {EMPTY_STATE_CONFIG[tabValue].subtitle}
                </Typography>
                {EMPTY_STATE_CONFIG[tabValue].ctaLabel && (
                  <Button variant="contained" onClick={() => setTabValue(1)} sx={{ borderRadius: 999, px: 4 }}>
                    {EMPTY_STATE_CONFIG[tabValue].ctaLabel}
                  </Button>
                )}
              </Box>
            )}
          </Grid>
        )}
      </Grid>

      <Menu anchorEl={menuAnchor} open={Boolean(menuAnchor)} onClose={closeMenu}>
        <MenuItem
          onClick={() => {
            if (menuTargetId) handleRemoveFriend(menuTargetId);
            closeMenu();
          }}
          sx={{ color: 'error.main' }}
        >
          <ListItemIcon>
            <PersonRemove fontSize="small" color="error" />
          </ListItemIcon>
          <ListItemText>Hủy kết bạn</ListItemText>
        </MenuItem>
      </Menu>
    </Box>
  );
});

export default FriendsPage;
