import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate, useParams, useSearchParams, Link as RouterLink } from 'react-router-dom';
import { useSelector } from 'react-redux';
import {
  Avatar,
  AvatarGroup,
  Autocomplete,
  Box,
  Chip,
  CircularProgress,
  Container,
  Grid,
  IconButton,
  Link,
  ListItemIcon,
  ListItemText,
  Menu,
  MenuItem,
  Paper,
  TextField,
  Tooltip,
  Typography,
  alpha,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import {
  ArrowBack,
  Close as CloseIcon,
  ContentCopy,
  ExitToApp,
  Groups,
  PostAdd as PostAddIcon,
  Send as SendIcon,
  ShareOutlined as ShareIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import { roomService } from '../../api/roomService';
import { filmService } from '../../api/filmService';
import { postService } from '../../api/postService';
import VideoPlayer, { type VideoPlayerHandle, type PlaybackActionPayload } from '../../components/Film/VideoPlayer';
import { useWatchRoomSession } from '../../contexts/watchRoomSessionContextValue';
import { useWebSocket } from '../../contexts/WebSocketContext';
import type {
  PlaybackUpdateRequest,
  RoomMessageResponse,
  RoomParticipantChangedEvent,
  RoomParticipantResponse,
} from '../../models';
import type { RootState } from '../../store';

// Chat feed mixes real persisted messages with ephemeral join/leave notices derived from
// "room:participants" broadcasts - the latter are never persisted/fetched from history, just
// appended locally as they happen.
type ChatFeedItem =
  | { kind: 'message'; id: string; data: RoomMessageResponse }
  | { kind: 'system'; id: string; text: string };

const INITIALS = (name?: string) =>
  (name ?? '?')
    .split(' ')
    .filter(Boolean)
    .slice(-2)
    .map((s) => s[0])
    .join('')
    .toUpperCase();

const WatchRoom: React.FC = () => {
  const { t } = useTranslation();
  const theme = useTheme();
  const { roomId } = useParams<{ roomId: string }>();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const currentUser = useSelector((state: RootState) => state.auth.user);

  const {
    room,
    episodes,
    videoSrc,
    videoError,
    loading,
    errorMessage,
    changingEpisode,
    playbackEvent,
    closedEvent,
    openRoom,
    changeEpisode,
    sendPlayback,
    refreshVideo,
    leaveSession,
    closeSession,
  } = useWatchRoomSession();
  const { subscribe } = useWebSocket();

  const [participants, setParticipants] = useState<RoomParticipantResponse[]>([]);
  const [participantCount, setParticipantCount] = useState(0);
  const [messages, setMessages] = useState<ChatFeedItem[]>([]);
  const [messageInput, setMessageInput] = useState('');

  const [shareAnchorEl, setShareAnchorEl] = useState<null | HTMLElement>(null);
  const [sharingPost, setSharingPost] = useState(false);

  const viewerPlayerRef = useRef<VideoPlayerHandle>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const isHost = !!room?.host;
  const activeRoomId = room?.id;

  // Session state lives above the router. Navigating to another feature therefore leaves this
  // session joined and the global mini-player takes over instead of closing/leaving the room.
  useEffect(() => {
    if (!roomId) return;
    void openRoom(roomId, searchParams.get('code') ?? undefined);
  }, [openRoom, roomId, searchParams]);

  // Initial seed for a viewer's player, once it mounts (src resolved) - room.positionSeconds
  // from the join response is already server-computed live, no elapsed-time math needed here.
  useEffect(() => {
    if (!room || room.host || !videoSrc) return;
    viewerPlayerRef.current?.syncTo({
      positionSeconds: room.positionSeconds,
      playing: room.playing,
      playbackRate: room.playbackRate,
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [videoSrc]);

  useEffect(() => {
    if (!playbackEvent || isHost) return;
    viewerPlayerRef.current?.syncTo({
      positionSeconds: room?.positionSeconds ?? playbackEvent.positionSeconds,
      playing: playbackEvent.playing,
      playbackRate: playbackEvent.playbackRate,
    });
  }, [isHost, playbackEvent, room?.positionSeconds]);

  useEffect(() => {
    if (!roomId || activeRoomId !== roomId) return;
    roomService
      .listParticipants(roomId)
      .then((res) => {
        const list = res.result ?? [];
        setParticipants(list);
        setParticipantCount(list.length);
      })
      .catch(() => {});
  }, [activeRoomId, roomId]);

  useEffect(() => {
    if (!roomId || activeRoomId !== roomId) return;
    roomService
      .listMessages(roomId, 1, 30)
      .then((res) => {
        const history = [...(res.result.data ?? [])].reverse();
        setMessages(history.map((m) => ({ kind: 'message', id: m.id, data: m })));
      })
      .catch(() => {});
  }, [activeRoomId, roomId]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages.length]);

  useEffect(() => {
    if (!roomId) return;
    const unsubscribeParticipants = subscribe('room:participants', (event: RoomParticipantChangedEvent) => {
      if (event?.roomId !== roomId) return;
      setParticipantCount(event.participantCount);
      const name = event.participant?.displayName || 'Một người xem';
      if (event.eventType === 'JOINED' && event.participant) {
        const joined = event.participant;
        setParticipants((prev) => (prev.some((participant) => participant.userId === joined.userId)
          ? prev
          : [...prev, joined]));
        setMessages((prev) => [
          ...prev,
          { kind: 'system', id: `sys-${joined.userId}-${Date.now()}`, text: `${name} đã tham gia phòng` },
        ]);
      } else if (event.eventType === 'LEFT' && event.participant) {
        const left = event.participant;
        setParticipants((prev) => prev.filter((participant) => participant.userId !== left.userId));
        setMessages((prev) => [
          ...prev,
          { kind: 'system', id: `sys-${left.userId}-${Date.now()}`, text: `${name} đã rời phòng` },
        ]);
      }
    });
    const unsubscribeMessages = subscribe('room:message', (message: RoomMessageResponse) => {
      if (message?.roomId !== roomId) return;
      setMessages((prev) => prev.some((item) => item.id === message.id)
        ? prev
        : [...prev, { kind: 'message', id: message.id, data: message }]);
    });
    return () => {
      unsubscribeParticipants();
      unsubscribeMessages();
    };
  }, [roomId, subscribe]);

  useEffect(() => {
    if (!closedEvent || closedEvent.roomId !== roomId) return;
    toast(isHost ? 'Bạn đã đóng phòng.' : 'Chủ phòng đã đóng phòng xem chung.', { icon: '👋' });
    navigate('/film/watch-together');
  }, [closedEvent, isHost, navigate, roomId]);

  const handleHostAction = useCallback(
    (action: PlaybackActionPayload) => {
      if (!roomId) return;
      let payload: PlaybackUpdateRequest;
      switch (action.type) {
        case 'play':
          payload = { action: 'PLAY', positionSeconds: action.positionSeconds };
          break;
        case 'pause':
          payload = { action: 'PAUSE', positionSeconds: action.positionSeconds };
          break;
        case 'seek':
          payload = { action: 'SEEK', positionSeconds: action.positionSeconds };
          break;
        case 'rate':
          payload = {
            action: 'HEARTBEAT',
            positionSeconds: action.positionSeconds,
            playbackRate: action.playbackRate,
          };
          break;
        case 'heartbeat':
        default:
          payload = { action: 'HEARTBEAT', positionSeconds: action.positionSeconds };
          break;
      }
      sendPlayback(payload);
    },
    [roomId, sendPlayback],
  );

  const handleLeave = async () => {
    try {
      await leaveSession();
      navigate('/film/watch-together');
    } catch {
      toast.error('Không thể rời phòng.');
    }
  };

  const handleCloseRoom = async () => {
    if (!roomId) return;
    try {
      await closeSession();
      toast.success('Đã đóng phòng.');
      navigate('/film/watch-together');
    } catch {
      toast.error('Không thể đóng phòng.');
    }
  };

  const handleEpisodeChange = async (episodeId: string) => {
    if (!episodeId) return;
    try {
      await changeEpisode(episodeId);
    } catch {
      toast.error('Không thể đổi tập phim.');
    }
  };

  const handleCopyInviteLink = async () => {
    if (!room) return;
    const link = `${window.location.origin}/film/watch-together/room/${room.id}?code=${room.inviteCode}`;
    try {
      if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(link);
      }
      toast.success(t('linkCopied'));
    } catch {
      toast.error('Không thể sao chép link');
    }
  };

  const handleShareToFeed = async () => {
    if (!room) return;
    setShareAnchorEl(null);
    setSharingPost(true);
    try {
      let thumbnailFileId: string | undefined;
      try {
        const filmRes = await filmService.getFilmAggregate(room.filmId);
        thumbnailFileId = filmRes?.result?.film?.thumbnailFileId;
      } catch {
        // thumbnail is optional, ignore resolve failure
      }
      const ep = episodes.find((e) => e.id === room.episodeId);
      const defaultTitle = `Xem cùng: ${room.filmTitle || room.name}` + (ep ? ` - Tập ${ep.episodeNumber}` : '');
      await postService.createPost({
        postType: 'WATCH_TOGETHER',
        title: defaultTitle,
        content: 'Ai cùng xem?',
        watchRoomId: room.id,
        watchFilmId: room.filmId,
        watchEpisodeId: room.episodeId,
        watchInviteCode: room.inviteCode,
        watchFilmTitle: room.filmTitle,
        watchFilmThumbnailFileId: thumbnailFileId,
      });
      toast.success(t('roomSharedToFeed'));
    } catch {
      toast.error(t('roomShareFailed'));
    } finally {
      setSharingPost(false);
    }
  };

  const handleSendMessage = async () => {
    const trimmed = messageInput.trim();
    if (!trimmed || !roomId) return;
    setMessageInput('');
    try {
      await roomService.sendMessage(roomId, { content: trimmed });
    } catch {
      toast.error('Không thể gửi tin nhắn');
    }
  };

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '70vh' }}>
        <CircularProgress color="primary" />
      </Box>
    );
  }

  if (errorMessage || !room) {
    return (
      <Container sx={{ mt: 6, textAlign: 'center' }}>
        <Typography variant="h5" sx={{ mb: 2 }}>
          {errorMessage || 'Không tìm thấy phòng xem chung này.'}
        </Typography>
        <Link component={RouterLink} to="/film/watch-together" sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5 }}>
          <ArrowBack fontSize="small" /> {t('watchTogetherTitle')}
        </Link>
      </Container>
    );
  }

  const currentEpisode = episodes.find((e) => e.id === room.episodeId);

  return (
    <Box sx={{ minHeight: '100vh', pb: 4 }}>
      <Container maxWidth="xl" sx={{ pt: 2 }}>
        <Paper
          sx={{
            borderRadius: 3,
            p: 2,
            mb: 2,
            display: 'flex',
            alignItems: 'center',
            flexWrap: 'wrap',
            gap: 1.5,
            bgcolor: alpha(theme.palette.text.primary, 0.03),
            border: '1px solid',
            borderColor: alpha(theme.palette.text.primary, 0.06),
          }}
        >
          <Groups sx={{ color: 'primary.main' }} />
          <Box sx={{ minWidth: 0 }}>
            <Typography variant="subtitle1" noWrap sx={{ fontWeight: 800 }}>
              {room.name}
            </Typography>
            <Typography variant="caption" color="text.secondary" noWrap sx={{ display: 'block' }}>
              {room.filmTitle || room.filmId}
              {currentEpisode ? ` · Tập ${currentEpisode.episodeNumber}` : ''}
            </Typography>
          </Box>

          <Box sx={{ flexGrow: 1 }} />

          <AvatarGroup max={5} sx={{ '& .MuiAvatar-root': { width: 30, height: 30, fontSize: '0.75rem' } }}>
            {participants.map((p) => (
              <Tooltip key={p.userId} title={p.displayName || 'Người xem'}>
                <Avatar src={p.avatar} sx={{ bgcolor: 'primary.main' }}>
                  {INITIALS(p.displayName)}
                </Avatar>
              </Tooltip>
            ))}
          </AvatarGroup>
          <Chip size="small" label={`${participantCount} ${t('viewers')}`} sx={{ fontWeight: 700 }} />

          {isHost && (
            <>
              <Tooltip title={t('shareRoom')}>
                <IconButton
                  onClick={(e) => setShareAnchorEl(e.currentTarget)}
                  disabled={sharingPost}
                  sx={{ border: '1px solid', borderColor: alpha(theme.palette.text.primary, 0.1) }}
                >
                  <ShareIcon fontSize="small" />
                </IconButton>
              </Tooltip>
              <Menu anchorEl={shareAnchorEl} open={Boolean(shareAnchorEl)} onClose={() => setShareAnchorEl(null)}>
                <MenuItem
                  onClick={() => {
                    setShareAnchorEl(null);
                    handleCopyInviteLink();
                  }}
                >
                  <ListItemIcon>
                    <ContentCopy fontSize="small" />
                  </ListItemIcon>
                  <ListItemText>{t('copyLink')}</ListItemText>
                </MenuItem>
                <MenuItem onClick={handleShareToFeed} disabled={sharingPost}>
                  <ListItemIcon>
                    <PostAddIcon fontSize="small" />
                  </ListItemIcon>
                  <ListItemText>{t('shareToFeed')}</ListItemText>
                </MenuItem>
              </Menu>
            </>
          )}
          {isHost ? (
            <Tooltip title="Đóng phòng">
              <IconButton onClick={handleCloseRoom} color="error" sx={{ border: '1px solid', borderColor: alpha(theme.palette.text.primary, 0.1) }}>
                <CloseIcon fontSize="small" />
              </IconButton>
            </Tooltip>
          ) : (
            <Tooltip title="Rời phòng">
              <IconButton onClick={handleLeave} sx={{ border: '1px solid', borderColor: alpha(theme.palette.text.primary, 0.1) }}>
                <ExitToApp fontSize="small" />
              </IconButton>
            </Tooltip>
          )}
        </Paper>

        <Paper
          sx={{
            borderRadius: 3,
            p: 2,
            mb: 2,
            display: 'flex',
            alignItems: { xs: 'stretch', sm: 'center' },
            flexDirection: { xs: 'column', sm: 'row' },
            gap: 1.5,
            bgcolor: alpha(theme.palette.text.primary, 0.03),
            border: '1px solid',
            borderColor: alpha(theme.palette.text.primary, 0.06),
          }}
        >
          <Box sx={{ minWidth: { sm: 220 } }}>
            <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>Tập đang xem</Typography>
            <Typography variant="caption" color="text.secondary">
              {isHost ? 'Chủ phòng có thể đổi tập cho tất cả người xem.' : 'Tập phim được điều khiển bởi chủ phòng.'}
            </Typography>
          </Box>
          <Autocomplete
            fullWidth
            size="small"
            options={episodes}
            disabled={!isHost || changingEpisode || episodes.length === 0}
            value={currentEpisode ?? null}
            isOptionEqualToValue={(option, value) => option.id === value.id}
            getOptionLabel={(episode) => `Mùa ${episode.seasonNumber} · Tập ${episode.episodeNumber} — ${episode.title}`}
            groupBy={(episode) => `Mùa ${episode.seasonNumber}`}
            onChange={(_event, episode) => {
              if (episode) void handleEpisodeChange(episode.id);
            }}
            noOptionsText="Phim này chưa có tập"
            loading={changingEpisode}
            renderInput={(params) => (
              <TextField
                {...params}
                label={room.episodeId ? 'Đổi tập phim' : 'Chọn tập bắt đầu xem'}
                placeholder="Chọn tập phim"
              />
            )}
          />
        </Paper>

        <Grid container spacing={2}>
          <Grid size={{ xs: 12, md: 8 }}>
            <Box sx={{ position: 'relative', width: '100%', aspectRatio: '16 / 9', bgcolor: '#000', borderRadius: 2, overflow: 'hidden' }}>
              {!room.episodeId ? (
                <Box sx={{ position: 'absolute', inset: 0, display: 'grid', placeItems: 'center', p: 3 }}>
                  <Typography color="text.secondary" align="center">
                    {isHost ? 'Hãy chọn một tập phim để bắt đầu xem chung.' : 'Đang chờ chủ phòng chọn tập phim.'}
                  </Typography>
                </Box>
              ) : videoError ? (
                <Box sx={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  <Typography color="text.secondary">{videoError}</Typography>
                </Box>
              ) : videoSrc ? (
                isHost ? (
                  <VideoPlayer
                    key={room.episodeId}
                    src={videoSrc}
                    title={currentEpisode ? `Tập ${currentEpisode.episodeNumber} - ${currentEpisode.title}` : room.filmTitle}
                    role="host"
                    onPlaybackAction={handleHostAction}
                    onStalledError={refreshVideo}
                    style={{ maxWidth: '100%', aspectRatio: 'auto', height: '100%' }}
                  />
                ) : (
                  <VideoPlayer
                    key={room.episodeId}
                    ref={viewerPlayerRef}
                    src={videoSrc}
                    title={currentEpisode ? `Tập ${currentEpisode.episodeNumber} - ${currentEpisode.title}` : room.filmTitle}
                    role="viewer"
                    autoPlay={room.playing}
                    onStalledError={refreshVideo}
                    style={{ maxWidth: '100%', aspectRatio: 'auto', height: '100%' }}
                  />
                )
              ) : (
                <Box sx={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  <CircularProgress color="primary" />
                </Box>
              )}
            </Box>
          </Grid>

          <Grid size={{ xs: 12, md: 4 }}>
            <Paper
              sx={{
                borderRadius: 3,
                height: { xs: 420, md: 'calc(100vh - 190px)' },
                display: 'flex',
                flexDirection: 'column',
                bgcolor: alpha(theme.palette.text.primary, 0.03),
                border: '1px solid',
                borderColor: alpha(theme.palette.text.primary, 0.06),
              }}
            >
              <Box sx={{ p: 1.5, borderBottom: '1px solid', borderColor: alpha(theme.palette.text.primary, 0.06) }}>
                <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>
                  {t('chat')}
                </Typography>
              </Box>

              <Box sx={{ flex: 1, overflowY: 'auto', p: 1.5, display: 'flex', flexDirection: 'column', gap: 1 }}>
                {messages.length === 0 && (
                  <Typography variant="body2" color="text.secondary" sx={{ textAlign: 'center', mt: 2 }}>
                    Chưa có tin nhắn nào. Hãy là người đầu tiên bắt chuyện!
                  </Typography>
                )}
                {messages.map((item) => {
                  if (item.kind === 'system') {
                    return (
                      <Typography
                        key={item.id}
                        variant="caption"
                        color="text.secondary"
                        sx={{ textAlign: 'center', display: 'block', my: 0.5 }}
                      >
                        {item.text}
                      </Typography>
                    );
                  }

                  const message = item.data;
                  const isMine = message.senderId === currentUser?.id;
                  return (
                    <Box
                      key={item.id}
                      sx={{
                        display: 'flex',
                        flexDirection: isMine ? 'row-reverse' : 'row',
                        alignItems: 'flex-end',
                        gap: 0.75,
                      }}
                    >
                      <Avatar src={message.senderAvatar} sx={{ width: 24, height: 24, fontSize: '0.65rem', bgcolor: 'primary.main' }}>
                        {INITIALS(message.senderName)}
                      </Avatar>
                      <Box sx={{ maxWidth: '75%' }}>
                        {!isMine && (
                          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', ml: 0.5 }}>
                            {message.senderName || 'Người xem'}
                          </Typography>
                        )}
                        <Box
                          sx={{
                            px: 1.5,
                            py: 0.75,
                            borderRadius: 2.5,
                            bgcolor: isMine ? 'primary.main' : alpha(theme.palette.text.primary, 0.08),
                            color: isMine ? '#fff' : 'text.primary',
                            wordBreak: 'break-word',
                          }}
                        >
                          <Typography variant="body2">{message.content}</Typography>
                        </Box>
                      </Box>
                    </Box>
                  );
                })}
                <div ref={messagesEndRef} />
              </Box>

              <Box sx={{ p: 1.5, borderTop: '1px solid', borderColor: alpha(theme.palette.text.primary, 0.06), display: 'flex', gap: 1 }}>
                <TextField
                  fullWidth
                  size="small"
                  placeholder="Nhắn gì đó..."
                  value={messageInput}
                  onChange={(e) => setMessageInput(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' && !e.shiftKey) {
                      e.preventDefault();
                      handleSendMessage();
                    }
                  }}
                />
                <IconButton color="primary" onClick={handleSendMessage} disabled={!messageInput.trim()}>
                  <SendIcon />
                </IconButton>
              </Box>
            </Paper>
          </Grid>
        </Grid>
      </Container>
    </Box>
  );
};

export default WatchRoom;
