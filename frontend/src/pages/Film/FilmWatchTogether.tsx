import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Box,
  Container,
  Typography,
  Paper,
  Button,
  TextField,
  InputAdornment,
  Avatar,
  Chip,
  Grid,
  Divider,
  Tooltip,
  IconButton,
  Link as MuiLink,
  Autocomplete,
  Stack,
  Switch,
  FormControlLabel,
  CircularProgress,
  alpha,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import {
  Groups,
  AddCircleOutlined,
  Movie as MovieIcon,
  PlayCircleFilled,
  ChevronRight,
  People,
  Search as SearchIcon,
  ConnectedTv,
  LocalMovies,
  Refresh,
} from '@mui/icons-material';
import { Link, useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { useTranslation } from 'react-i18next';
import { filmService } from '../../api/filmService';
import { friendService } from '../../api/friendService';
import { roomService } from '../../api/roomService';
import { useWebSocket } from '../../contexts/WebSocketContext';
import type {
  EpisodeResponse,
  FilmSummaryResponse,
  RoomClosedEvent,
  RoomListItemResponse,
  UserRelationshipResponse,
} from '../../models';

interface InviteFriend {
  id: string;
  name: string;
  avatar?: string;
}

// Fixed WS room every client browsing this page joins (see socket-service's
// RoomEventKafkaService, LOBBY_ROOM_ID) so the public room list updates live instead of
// requiring the manual refresh button.
const LOBBY_ROOM_ID = 'watch-together-lobby';
const PUBLIC_ROOMS_PAGE_SIZE = 6;

const INITIALS = (name: string) =>
  name
    .split(' ')
    .filter(Boolean)
    .slice(-2)
    .map((s) => s[0])
    .join('')
    .toUpperCase();

const FilmWatchTogether: React.FC = React.memo(() => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const theme = useTheme();
  const { send, subscribe, isConnected } = useWebSocket();

  const [friends, setFriends] = useState<InviteFriend[]>([]);
  const [invitees, setInvitees] = useState<InviteFriend[]>([]);
  const [inviteInput, setInviteInput] = useState('');

  const [films, setFilms] = useState<FilmSummaryResponse[]>([]);
  const [selectedFilmId, setSelectedFilmId] = useState<string>('');
  const [episodeById, setEpisodeById] = useState<Record<string, EpisodeResponse>>({});
  const loadedRoomFilmIdsRef = useRef(new Set<string>());
  const [isPublicRoom, setIsPublicRoom] = useState(true);
  const [creating, setCreating] = useState(false);

  const [publicRooms, setPublicRooms] = useState<RoomListItemResponse[]>([]);
  const [myRooms, setMyRooms] = useState<RoomListItemResponse[]>([]);
  const [refreshingPublic, setRefreshingPublic] = useState(true);
  const [refreshingMy, setRefreshingMy] = useState(true);

  useEffect(() => {
    filmService
      .getPageFilms(1, 60)
      .then((res) => setFilms(res.result?.data ?? []))
      .catch(() => toast.error('Không thể tải danh sách phim'));

    friendService
      .getMyFriends(1, 50)
      .then((res) => {
        const list: UserRelationshipResponse[] = res.result?.data ?? [];
        setFriends(list.map((f) => ({ id: f.friendId, name: f.displayName, avatar: f.friendAvatar })));
      })
      .catch(() => {});
  }, []);

  const loadPublicRooms = useCallback(() =>
    roomService
      .listPublicRooms(1, PUBLIC_ROOMS_PAGE_SIZE)
      .then((res) => setPublicRooms(res.result?.data ?? []))
      .catch(() => toast.error('Không thể tải danh sách phòng công cộng')),
  []);

  const fetchPublicRooms = useCallback(() => {
    setRefreshingPublic(true);
    void loadPublicRooms().finally(() => setRefreshingPublic(false));
  }, [loadPublicRooms]);

  const loadMyRooms = useCallback(() =>
    roomService
      .listMyRooms(1, 6)
      .then((res) => setMyRooms(res.result?.data ?? []))
      .catch(() => toast.error('Không thể tải danh sách phòng của bạn')),
  []);

  const fetchMyRooms = useCallback(() => {
    setRefreshingMy(true);
    void loadMyRooms().finally(() => setRefreshingMy(false));
  }, [loadMyRooms]);

  useEffect(() => {
    void loadPublicRooms().finally(() => setRefreshingPublic(false));
    void loadMyRooms().finally(() => setRefreshingMy(false));
  }, [loadPublicRooms, loadMyRooms]);

  // Public room list stays live: everyone browsing this page joins a shared "lobby" WS room and
  // gets pushed new/closed public rooms instead of needing to press refresh.
  useEffect(() => {
    if (!isConnected) return;
    send({ type: 'join-room', roomId: LOBBY_ROOM_ID });
    return () => {
      send({ type: 'leave-room', roomId: LOBBY_ROOM_ID });
    };
  }, [isConnected, send]);

  useEffect(() => {
    const unsubscribers = [
      subscribe('lobby:room-created', (room: RoomListItemResponse) => {
        setPublicRooms((prev) => {
          if (prev.some((r) => r.id === room.id)) return prev;
          return [room, ...prev].slice(0, PUBLIC_ROOMS_PAGE_SIZE);
        });
      }),
      subscribe('lobby:room-closed', (event: RoomClosedEvent) => {
        setPublicRooms((prev) => prev.filter((r) => r.id !== event.roomId));
      }),
      subscribe('lobby:room-updated', (room: RoomListItemResponse) => {
        setPublicRooms((prev) => prev.map((current) => current.id === room.id ? room : current));
        setMyRooms((prev) => prev.map((current) => current.id === room.id
          ? { ...room, alreadyJoined: current.alreadyJoined }
          : current));
      }),
    ];
    return () => unsubscribers.forEach((unsubscribe) => unsubscribe());
  }, [subscribe]);

  // Room-service broadcasts the current episodeId. Resolve each room film once so cards can
  // show the episode number and immediately reflect lobby:room-updated events.
  useEffect(() => {
    const missingFilmIds = [...publicRooms, ...myRooms]
      .filter((room) => room.episodeId)
      .map((room) => room.filmId)
      .filter((filmId) => !loadedRoomFilmIdsRef.current.has(filmId));

    [...new Set(missingFilmIds)].forEach((filmId) => {
      loadedRoomFilmIdsRef.current.add(filmId);
      filmService.getEpisodesByFilm(filmId)
        .then((response) => {
          setEpisodeById((current) => {
            const next = { ...current };
            (response.result ?? []).forEach((episode) => { next[episode.id] = episode; });
            return next;
          });
        })
        .catch(() => loadedRoomFilmIdsRef.current.delete(filmId));
    });
  }, [myRooms, publicRooms]);

  const handleCreateRoom = useCallback(async () => {
    if (!selectedFilmId) {
      toast.error('Vui lòng chọn phim để tạo phòng xem chung');
      return;
    }
    setCreating(true);
    try {
      const selectedFilm = films.find((f) => f.id === selectedFilmId);
      const response = await roomService.createRoom({
        filmId: selectedFilmId,
        name: selectedFilm ? `Xem chung: ${selectedFilm.title}` : undefined,
        publicRoom: isPublicRoom,
        inviteeUserIds: invitees.map((f) => f.id),
      });
      navigate(`/film/watch-together/room/${response.result.id}`);
    } catch {
      toast.error('Không thể tạo phòng, vui lòng thử lại');
    } finally {
      setCreating(false);
    }
  }, [selectedFilmId, films, isPublicRoom, invitees, navigate]);

  const handleJoinRoom = useCallback(
    (roomId: string) => {
      navigate(`/film/watch-together/room/${roomId}`);
    },
    [navigate],
  );

  const toggleFriend = useCallback((friend: InviteFriend) => {
    setInvitees((cur) =>
      cur.find((f) => f.id === friend.id) ? cur.filter((f) => f.id !== friend.id) : [...cur, friend],
    );
  }, []);

  const suggestionFriends = useMemo(
    () =>
      friends
        .filter((f) => !invitees.find((i) => i.id === f.id))
        .filter((f) => f.name.toLowerCase().includes(inviteInput.trim().toLowerCase())),
    [friends, invitees, inviteInput],
  );

  const renderRoomCard = (room: RoomListItemResponse, variant: 'public' | 'my') => {
    const currentEpisode = room.episodeId ? episodeById[room.episodeId] : undefined;

    return (
      <Paper
      key={room.id}
      sx={{
        borderRadius: 3,
        overflow: 'hidden',
        bgcolor: alpha(theme.palette.text.primary, 0.03),
        border: '1px solid',
        borderColor: alpha(theme.palette.text.primary, 0.06),
        transition: 'all 0.25s ease',
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        '&:hover': {
          transform: 'translateY(-3px)',
          boxShadow: '0 16px 32px rgba(0,0,0,0.4)',
          borderColor: 'rgba(0,168,78,0.35)',
        },
      }}
    >
      <Box sx={{ position: 'relative', aspectRatio: '16/9', overflow: 'hidden', bgcolor: '#111' }}>
        {room.filmThumbnail && (
          <Box
            component="img"
            src={room.filmThumbnail}
            alt={room.name}
            sx={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }}
          />
        )}
        <Box
          sx={{
            position: 'absolute',
            inset: 0,
            background: 'linear-gradient(180deg, transparent 40%, rgba(0,0,0,0.85) 100%)',
          }}
        />
        <Chip
          label={room.publicRoom ? 'Công cộng' : 'Riêng tư'}
          size="small"
          sx={{
            position: 'absolute',
            top: 12,
            left: 12,
            bgcolor: room.publicRoom ? 'rgba(0,168,78,0.88)' : 'rgba(120,80,255,0.88)',
            color: '#fff',
            fontWeight: 700,
            backdropFilter: 'blur(6px)',
          }}
        />
        <Box sx={{ position: 'absolute', top: 12, right: 12, display: 'flex', gap: 1 }}>
          {variant === 'my' && !room.alreadyJoined && (
            <Chip
              label="Được mời"
              size="small"
              sx={{ bgcolor: 'rgba(255,193,7,0.9)', color: '#000', fontWeight: 700, backdropFilter: 'blur(6px)' }}
            />
          )}
          <Chip
            icon={<People sx={{ fontSize: 14, color: '#fff !important' }} />}
            label={`${room.participantCount} ${t('viewers')}`}
            size="small"
            sx={{ bgcolor: 'rgba(0,0,0,0.6)', color: '#fff', backdropFilter: 'blur(6px)' }}
          />
        </Box>
        <Box sx={{ position: 'absolute', left: 16, right: 16, bottom: 14 }}>
          <Typography variant="h6" sx={{ fontWeight: 800, color: '#fff', mb: 0.5 }} noWrap>
            {room.name}
          </Typography>
          {room.filmTitle && (
            <Box sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.75, color: 'rgba(255,255,255,0.85)' }}>
              <LocalMovies sx={{ color: '#00A84E', fontSize: 16 }} />
              <Typography variant="body2" sx={{ color: 'inherit' }} noWrap>
                {room.filmTitle}
                {currentEpisode ? ` · Tập ${currentEpisode.episodeNumber}` : ' · Chưa chọn tập'}
              </Typography>
            </Box>
          )}
        </Box>
      </Box>
      <Box sx={{ p: 3, flex: 1, display: 'flex', flexDirection: 'column', gap: 2 }}>
        <Typography variant="body2" color="text.secondary">
          Chủ phòng: <strong>{room.hostDisplayName || '—'}</strong>
        </Typography>
        <Box sx={{ mt: 'auto', display: 'flex', gap: 1.5 }}>
          <Button
            fullWidth
            variant="contained"
            startIcon={variant === 'my' ? <PlayCircleFilled /> : <ConnectedTv />}
            onClick={() => handleJoinRoom(room.id)}
            sx={{
              borderRadius: 2.5,
              bgcolor: variant === 'my' ? 'primary.main' : alpha(theme.palette.text.primary, 0.08),
              color: variant === 'my' ? '#fff' : 'text.primary',
              '&:hover': {
                bgcolor: variant === 'my' ? '#008F41' : 'rgba(0,168,78,0.18)',
              },
            }}
          >
            {t('joinRoom')}
          </Button>
        </Box>
      </Box>
      </Paper>
    );
  };

  return (
    <Box sx={{ minHeight: '100vh', pb: 8 }}>
      <Box
        sx={{
          position: 'relative',
          overflow: 'hidden',
          pt: { xs: 4, md: 6 },
          pb: { xs: 5, md: 7 },
          mb: 6,
          background:
            `linear-gradient(135deg, rgba(120,80,255,0.22) 0%, rgba(0,168,78,0.15) 40%, ${theme.palette.background.default} 85%)`,
          borderBottom: '1px solid',
          borderColor: 'divider',
        }}
      >
        <Box
          sx={{
            position: 'absolute',
            inset: 0,
            opacity: 0.2,
            backgroundImage:
              'radial-gradient(circle at 20% 20%, rgba(0,168,78,0.45) 0%, transparent 40%), radial-gradient(circle at 80% 80%, rgba(120,80,255,0.4) 0%, transparent 40%)',
            pointerEvents: 'none',
          }}
        />
        <Container maxWidth="xl" sx={{ position: 'relative' }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2 }}>
            <Groups sx={{ fontSize: 40, color: 'primary.main' }} />
            <Typography variant="h3" sx={{ fontWeight: 900, letterSpacing: '-0.03em' }}>
              {t('watchTogetherTitle')}
            </Typography>
          </Box>
          <Typography variant="body1" color="text.secondary" sx={{ maxWidth: 620 }}>
            {t('watchTogetherSubtitle')}
          </Typography>
        </Container>
      </Box>

      <Container maxWidth="xl">
        <Grid container spacing={4}>
          <Grid size={{ xs: 12, md: 5, lg: 4 }}>
            <Paper
              sx={{
                borderRadius: 4,
                p: { xs: 3, md: 4 },
                position: 'sticky',
                top: 96,
                backgroundImage:
                  `linear-gradient(180deg, rgba(0,168,78,0.08) 0%, ${alpha(theme.palette.text.primary, 0.03)} 100%)`,
                border: '1px solid',
                borderColor: alpha(theme.palette.text.primary, 0.08),
                boxShadow: theme.palette.mode === 'dark' ? '0 20px 50px rgba(0,0,0,0.35)' : '0 20px 50px rgba(0,0,0,0.1)',
              }}
            >
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 3 }}>
                <AddCircleOutlined color="primary" sx={{ fontSize: 28 }} />
                <Typography variant="h5" sx={{ fontWeight: 800 }}>
                  {t('newRoom')}
                </Typography>
              </Box>

              <Stack spacing={3}>
                <Box>
                  <Typography variant="subtitle2" sx={{ mb: 1.25, fontWeight: 700 }}>
                    {t('selectFilm')} <span style={{ color: '#EF5350' }}>*</span>
                  </Typography>
                  <Autocomplete
                    fullWidth
                    size="small"
                    options={films}
                    getOptionLabel={(film) => film.title}
                    isOptionEqualToValue={(option, value) => option.id === value.id}
                    value={films.find((f) => f.id === selectedFilmId) ?? null}
                    onChange={(_, newValue) => setSelectedFilmId(newValue?.id ?? '')}
                    noOptionsText="Không tìm thấy phim phù hợp"
                    slotProps={{
                      listbox: { sx: { maxHeight: 320 } },
                    }}
                    renderOption={({ key, ...optionProps }, film) => (
                      <Box
                        component="li"
                        key={key}
                        {...optionProps}
                        sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}
                      >
                        <LocalMovies sx={{ color: 'primary.main', fontSize: 18 }} />
                        {film.title}
                      </Box>
                    )}
                    renderInput={(params) => (
                      <TextField {...params} label={t('selectFilmPlaceholder')} placeholder={t('selectFilmPlaceholder')} />
                    )}
                  />
                  <Box sx={{ mt: 1.5 }}>
                    <MuiLink
                      component={Link}
                      to="/film"
                      sx={{
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: 0.5,
                        color: 'primary.main',
                        textDecoration: 'none',
                        fontWeight: 600,
                        fontSize: '0.9rem',
                        '&:hover': { textDecoration: 'underline' },
                      }}
                    >
                      <MovieIcon sx={{ fontSize: 16 }} />
                      Khám phá kho phim <ChevronRight sx={{ fontSize: 16 }} />
                    </MuiLink>
                  </Box>
                </Box>

                <Divider />

                <Box>
                  <FormControlLabel
                    control={<Switch checked={isPublicRoom} onChange={(e) => setIsPublicRoom(e.target.checked)} color="primary" />}
                    label={
                      <Box>
                        <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                          {isPublicRoom ? 'Phòng công cộng' : 'Phòng riêng tư'}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          {isPublicRoom
                            ? 'Ai cũng có thể tìm thấy và tham gia'
                            : 'Chỉ vào được qua lời mời hoặc link mời'}
                        </Typography>
                      </Box>
                    }
                  />
                </Box>

                <Divider />

                <Box>
                  <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1.25 }}>
                    <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
                      {t('inviteFriends')}
                    </Typography>
                    <Chip
                      size="small"
                      label={`${invitees.length} người`}
                      sx={{
                        bgcolor: 'rgba(0,168,78,0.15)',
                        color: 'primary.main',
                        fontWeight: 700,
                      }}
                    />
                  </Box>

                  {invitees.length > 0 && (
                    <Box sx={{ mb: 2, display: 'flex', flexWrap: 'wrap', gap: 1 }}>
                      {invitees.map((f) => (
                        <Chip
                          key={f.id}
                          avatar={
                            <Avatar
                              src={f.avatar}
                              sx={{
                                bgcolor: 'primary.main',
                                color: '#fff',
                                fontWeight: 800,
                                fontSize: '0.72rem',
                                width: 26,
                                height: 26,
                              }}
                            >
                              {INITIALS(f.name)}
                            </Avatar>
                          }
                          label={f.name}
                          onDelete={() => toggleFriend(f)}
                          size="small"
                          sx={{
                            bgcolor: alpha(theme.palette.text.primary, 0.05),
                            border: '1px solid',
                            borderColor: alpha(theme.palette.text.primary, 0.08),
                          }}
                        />
                      ))}
                    </Box>
                  )}

                  <TextField
                    fullWidth
                    size="small"
                    placeholder={t('typeToInvite')}
                    value={inviteInput}
                    onChange={(e) => setInviteInput(e.target.value)}
                    sx={{
                      '& .MuiOutlinedInput-root': {
                        borderRadius: 2,
                        bgcolor: alpha(theme.palette.text.primary, 0.04),
                      },
                    }}
                    slotProps={{
                      input: {
                        startAdornment: (
                          <InputAdornment position="start">
                            <SearchIcon sx={{ color: 'text.secondary', fontSize: 18 }} />
                          </InputAdornment>
                        ),
                      },
                    }}
                  />

                  {suggestionFriends.length > 0 && (
                    <Box sx={{ mt: 2 }}>
                      <Typography variant="caption" color="text.secondary" sx={{ mb: 1, display: 'block' }}>
                        Gợi ý nhanh:
                      </Typography>
                      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
                        {suggestionFriends.map((f) => (
                          <Tooltip key={f.id} title="Mời vào phòng">
                            <Chip
                              avatar={
                                <Avatar
                                  src={f.avatar}
                                  sx={{
                                    bgcolor: 'primary.main',
                                    color: '#fff',
                                    fontWeight: 800,
                                    fontSize: '0.68rem',
                                    width: 22,
                                    height: 22,
                                  }}
                                >
                                  {INITIALS(f.name)}
                                </Avatar>
                              }
                              label={f.name}
                              onClick={() => toggleFriend(f)}
                              size="small"
                              variant="outlined"
                              sx={{
                                borderColor: alpha(theme.palette.text.primary, 0.1),
                                color: 'text.secondary',
                                '&:hover': {
                                  bgcolor: 'rgba(0,168,78,0.12)',
                                  color: 'primary.main',
                                  borderColor: 'rgba(0,168,78,0.3)',
                                },
                              }}
                            />
                          </Tooltip>
                        ))}
                      </Box>
                    </Box>
                  )}
                </Box>

                <Divider />

                <Button
                  fullWidth
                  variant="contained"
                  size="large"
                  startIcon={creating ? <CircularProgress size={18} color="inherit" /> : <AddCircleOutlined />}
                  onClick={handleCreateRoom}
                  disabled={!selectedFilmId || creating}
                  sx={{
                    borderRadius: 3,
                    py: 1.5,
                    fontSize: '1rem',
                    fontWeight: 800,
                    letterSpacing: '0.02em',
                    bgcolor: 'primary.main',
                    color: '#fff',
                    boxShadow: '0 12px 32px rgba(0,168,78,0.35)',
                    '&:hover': {
                      bgcolor: '#008F41',
                      transform: 'translateY(-1px)',
                      boxShadow: '0 16px 36px rgba(0,168,78,0.45)',
                    },
                    '&:disabled': {
                      bgcolor: 'action.disabledBackground',
                      color: 'text.disabled',
                      boxShadow: 'none',
                    },
                    transition: 'all 0.2s ease',
                  }}
                >
                  {t('createRoom')}
                </Button>
              </Stack>
            </Paper>
          </Grid>

          <Grid size={{ xs: 12, md: 7, lg: 8 }}>
            <Box sx={{ mb: 6 }}>
              <Box
                sx={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  mb: 3,
                  flexWrap: 'wrap',
                  gap: 2,
                }}
              >
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                  <People sx={{ color: 'primary.main' }} />
                  <Typography variant="h5" sx={{ fontWeight: 800 }}>
                    {t('publicRooms')}
                  </Typography>
                  <Chip
                    size="small"
                    label={publicRooms.length}
                    sx={{ bgcolor: alpha(theme.palette.text.primary, 0.08), color: 'text.secondary', fontWeight: 700 }}
                  />
                </Box>
                <Tooltip title="Làm mới danh sách phòng">
                  <IconButton
                    size="small"
                    onClick={fetchPublicRooms}
                    sx={{
                      bgcolor: alpha(theme.palette.text.primary, 0.06),
                      border: '1px solid',
                      borderColor: alpha(theme.palette.text.primary, 0.1),
                      '&:hover': { bgcolor: 'rgba(0,168,78,0.15)' },
                    }}
                  >
                    <Refresh
                      fontSize="small"
                      sx={{
                        animation: refreshingPublic ? 'watch-together-spin 0.6s linear' : 'none',
                        '@keyframes watch-together-spin': {
                          from: { transform: 'rotate(0deg)' },
                          to: { transform: 'rotate(360deg)' },
                        },
                      }}
                    />
                  </IconButton>
                </Tooltip>
              </Box>
              {publicRooms.length === 0 ? (
                <Paper sx={{ p: 4, textAlign: 'center', borderRadius: 3, bgcolor: alpha(theme.palette.text.primary, 0.03) }}>
                  <Typography color="text.secondary">Chưa có phòng công cộng nào đang mở.</Typography>
                </Paper>
              ) : (
                <Grid container spacing={3}>
                  {publicRooms.map((r) => (
                    <Grid size={{ xs: 12, sm: 6, lg: 4 }} key={r.id}>
                      {renderRoomCard(r, 'public')}
                    </Grid>
                  ))}
                </Grid>
              )}
            </Box>

            <Box>
              <Box
                sx={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  mb: 3,
                  flexWrap: 'wrap',
                  gap: 2,
                }}
              >
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                  <ConnectedTv sx={{ color: '#FF7043' }} />
                  <Typography variant="h5" sx={{ fontWeight: 800 }}>
                    {t('myRooms')}
                  </Typography>
                  <Tooltip title="Làm mới danh sách phòng">
                    <IconButton
                      size="small"
                      onClick={fetchMyRooms}
                      sx={{
                        bgcolor: alpha(theme.palette.text.primary, 0.06),
                        border: '1px solid',
                        borderColor: alpha(theme.palette.text.primary, 0.1),
                        '&:hover': { bgcolor: 'rgba(0,168,78,0.15)' },
                      }}
                    >
                      <Refresh
                        fontSize="small"
                        sx={{
                          animation: refreshingMy ? 'watch-together-spin 0.6s linear' : 'none',
                          '@keyframes watch-together-spin': {
                            from: { transform: 'rotate(0deg)' },
                            to: { transform: 'rotate(360deg)' },
                          },
                        }}
                      />
                    </IconButton>
                  </Tooltip>
                </Box>
              </Box>
              {myRooms.length === 0 ? (
                <Paper sx={{ p: 4, textAlign: 'center', borderRadius: 3, bgcolor: alpha(theme.palette.text.primary, 0.03) }}>
                  <Typography color="text.secondary">Bạn chưa tạo hoặc tham gia phòng nào.</Typography>
                </Paper>
              ) : (
                <Grid container spacing={3}>
                  {myRooms.map((r) => (
                    <Grid size={{ xs: 12, sm: 6, lg: 4 }} key={r.id}>
                      {renderRoomCard(r, 'my')}
                    </Grid>
                  ))}
                </Grid>
              )}
            </Box>
          </Grid>
        </Grid>
      </Container>
    </Box>
  );
});

FilmWatchTogether.displayName = 'FilmWatchTogether';

export default FilmWatchTogether;
