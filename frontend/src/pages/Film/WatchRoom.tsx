import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate, useParams, useSearchParams, Link as RouterLink } from 'react-router-dom';
import { useSelector } from 'react-redux';
import {
  Avatar,
  AvatarGroup,
  Box,
  Chip,
  CircularProgress,
  Container,
  Grid,
  IconButton,
  InputBase,
  Link,
  ListItemIcon,
  ListItemText,
  Menu,
  MenuItem,
  Paper,
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
  ShareOutlined as ShareIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import { roomService } from '../../api/roomService';
import { filmService } from '../../api/filmService';
import { postService } from '../../api/postService';
import VideoPlayer, { type VideoPlayerHandle, type PlaybackActionPayload } from '../../components/Film/VideoPlayer';
import EpisodePicker from '../../components/Film/EpisodePicker';
import GradientSendButton from '../../components/common/GradientSendButton';
import {
  VIBE_GLOW,
  VIBE_GRADIENT,
  VIBE_TEAL,
  bubbleSx,
  chatCanvasSx,
  composerBarSx,
  vibeBorder,
  vibeSurface,
} from '../../styles/vibe';
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
    syncTarget,
    closedEvent,
    hostLocalSnapshotRef,
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
  const hostPlayerRef = useRef<VideoPlayerHandle>(null);
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
      action: 'JOIN',
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [videoSrc]);

  useEffect(() => {
    if (!syncTarget) return;
    if (isHost) {
      if (syncTarget.applyToHost) hostPlayerRef.current?.syncTo(syncTarget);
      return;
    }
    viewerPlayerRef.current?.syncTo(syncTarget);
  }, [isHost, syncTarget]);

  // Restore the host's own player state (position/playing/rate) the moment its VideoPlayer
  // instance actually mounts, before any user interaction - this is what keeps switching between
  // this full room page and the floating mini player from losing currentTime, restarting at 0,
  // or auto-unpausing. A callback ref (rather than a useEffect keyed on videoSrc) fires exactly
  // once per real mount/unmount, so it isn't fooled by a same-episode presigned-URL refresh
  // (which also changes videoSrc but doesn't remount the component - already handled correctly
  // by VideoPlayer's own internal resume logic) or by this component's visibility toggling
  // without actually unmounting.
  const attachHostPlayerRef = useCallback((handle: VideoPlayerHandle | null) => {
    hostPlayerRef.current = handle;
    if (handle) handle.restoreHostState(hostLocalSnapshotRef.current);
  }, [hostLocalSnapshotRef]);

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
      const name = event.participant?.displayName || t('roomViewer');
      if (event.eventType === 'JOINED' && event.participant) {
        const joined = event.participant;
        setParticipants((prev) => (prev.some((participant) => participant.userId === joined.userId)
          ? prev
          : [...prev, joined]));
        setMessages((prev) => [
          ...prev,
          { kind: 'system', id: `sys-${joined.userId}-${Date.now()}`, text: t('participantJoined', { name }) },
        ]);
      } else if (event.eventType === 'LEFT' && event.participant) {
        const left = event.participant;
        setParticipants((prev) => prev.filter((participant) => participant.userId !== left.userId));
        setMessages((prev) => [
          ...prev,
          { kind: 'system', id: `sys-${left.userId}-${Date.now()}`, text: t('participantLeft', { name }) },
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
    toast(isHost ? t('youClosedRoom') : t('hostClosedRoom'), { icon: '👋' });
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
      toast.error(t('leaveRoomFailed'));
    }
  };

  const handleCloseRoom = async () => {
    if (!roomId) return;
    try {
      await closeSession();
      toast.success(t('roomClosed'));
      navigate('/film/watch-together');
    } catch {
      toast.error(t('closeRoomFailed'));
    }
  };

  const handleEpisodeChange = async (episodeId: string) => {
    if (!episodeId) return;
    // sendPlayback (which changeEpisode routes through) already surfaces a failure via toast and
    // rolls the player back to server state - nothing left to do here.
    await changeEpisode(episodeId);
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
      toast.error(t('copyFailed'));
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
      const defaultTitle = t('watchWithTitle', { title: room.filmTitle || room.name }) + (ep ? ` - ${t('episodeLabel', { episode: ep.episodeNumber })}` : '');
      await postService.createPost({
        postType: 'WATCH_TOGETHER',
        title: defaultTitle,
        content: t('watchTogetherPostContent'),
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
      toast.error(t('sendMessageFailed'));
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
          {errorMessage || t('roomNotFound')}
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
              {currentEpisode ? ` · ${t('episodeLabel', { episode: currentEpisode.episodeNumber })}` : ''}
            </Typography>
          </Box>

          <Box sx={{ flexGrow: 1 }} />

          <AvatarGroup max={5} sx={{ '& .MuiAvatar-root': { width: 30, height: 30, fontSize: '0.75rem' } }}>
            {participants.map((p) => (
              <Tooltip key={p.userId} title={p.displayName || t('roomViewer')}>
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
            <Tooltip title={t('closeRoom')}>
              <IconButton onClick={handleCloseRoom} color="error" sx={{ border: '1px solid', borderColor: alpha(theme.palette.text.primary, 0.1) }}>
                <CloseIcon fontSize="small" />
              </IconButton>
            </Tooltip>
          ) : (
            <Tooltip title={t('leaveRoom')}>
              <IconButton onClick={handleLeave} sx={{ border: '1px solid', borderColor: alpha(theme.palette.text.primary, 0.1) }}>
                <ExitToApp fontSize="small" />
              </IconButton>
            </Tooltip>
          )}
        </Paper>

        <Grid container spacing={2}>
          <Grid size={{ xs: 12, md: 8 }}>
            <Box sx={{ position: 'relative', width: '100%', aspectRatio: '16 / 9', bgcolor: '#000', borderRadius: 2, overflow: 'hidden' }}>
              {!room.episodeId ? (
                <Box sx={{ position: 'absolute', inset: 0, display: 'grid', placeItems: 'center', p: 3 }}>
                  <Typography color="text.secondary" align="center">
                    {isHost ? t('hostSelectEpisodePrompt') : t('waitingForHostEpisode')}
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
                    ref={attachHostPlayerRef}
                    src={videoSrc}
                    title={currentEpisode ? `${t('episodeLabel', { episode: currentEpisode.episodeNumber })} - ${currentEpisode.title}` : room.filmTitle}
                    role="host"
                    autoPlay={false}
                    onPlaybackAction={handleHostAction}
                    onStalledError={refreshVideo}
                    onLocalTimeUpdate={(state) => { hostLocalSnapshotRef.current = state; }}
                    style={{ maxWidth: '100%', aspectRatio: 'auto', height: '100%' }}
                  />
                ) : (
                  <VideoPlayer
                    key={room.episodeId}
                    ref={viewerPlayerRef}
                    src={videoSrc}
                    title={currentEpisode ? `${t('episodeLabel', { episode: currentEpisode.episodeNumber })} - ${currentEpisode.title}` : room.filmTitle}
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
              elevation={0}
              sx={{
                borderRadius: '24px',
                height: { xs: 420, md: 'calc(100vh - 190px)' },
                display: 'flex',
                flexDirection: 'column',
                overflow: 'hidden',
                bgcolor: 'background.paper',
                border: '1px solid',
                borderColor: vibeBorder(theme),
              }}
            >
              <Box
                sx={{
                  px: 2,
                  py: 1.5,
                  display: 'flex',
                  alignItems: 'center',
                  gap: 1.25,
                  borderBottom: '1px solid',
                  borderColor: vibeBorder(theme),
                }}
              >
                <Box
                  sx={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 0.75,
                    px: 1.1,
                    py: 0.35,
                    borderRadius: '999px',
                    background: VIBE_GRADIENT,
                    boxShadow: VIBE_GLOW,
                    color: '#fff',
                    fontSize: '0.66rem',
                    fontWeight: 900,
                    letterSpacing: '0.12em',
                  }}
                >
                  <Box
                    sx={{
                      width: 6,
                      height: 6,
                      borderRadius: '50%',
                      bgcolor: '#fff',
                      animation: 'roomLivePulse 1.4s ease-in-out infinite',
                      '@keyframes roomLivePulse': {
                        '0%, 100%': { opacity: 1, transform: 'scale(1)' },
                        '50%': { opacity: 0.35, transform: 'scale(0.7)' },
                      },
                      '@media (prefers-reduced-motion: reduce)': { animation: 'none' },
                    }}
                  />
                  {t('liveLabel')}
                </Box>
                <Typography sx={{ fontWeight: 900, flex: 1, letterSpacing: '-0.01em' }}>
                  {t('chat')}
                </Typography>
                <Typography variant="caption" sx={{ fontWeight: 700, color: 'text.secondary' }}>
                  {participantCount} {t('viewers')}
                </Typography>
              </Box>

              <Box sx={{ flex: 1, overflowY: 'auto', p: 1.5, display: 'flex', flexDirection: 'column', gap: 1, ...chatCanvasSx(theme) }}>
                {messages.length === 0 && (
                  <Box sx={{ m: 'auto', textAlign: 'center', px: 2 }}>
                    <Typography sx={{ fontSize: '2rem', lineHeight: 1, mb: 1 }} aria-hidden>🍿</Typography>
                    <Typography variant="body2" color="text.secondary">
                      {t('noRoomMessages')}
                    </Typography>
                  </Box>
                )}
                {messages.map((item) => {
                  if (item.kind === 'system') {
                    return (
                      <Box key={item.id} sx={{ display: 'flex', justifyContent: 'center', my: 0.25 }}>
                        <Typography
                          variant="caption"
                          sx={{
                            px: 1.25,
                            py: 0.25,
                            borderRadius: '999px',
                            bgcolor: vibeSurface(theme),
                            border: '1px solid',
                            borderColor: vibeBorder(theme),
                            color: 'text.secondary',
                            fontWeight: 600,
                          }}
                        >
                          {item.text}
                        </Typography>
                      </Box>
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
                        animation: 'roomMessageIn 0.22s cubic-bezier(.2,.8,.2,1)',
                        '@keyframes roomMessageIn': {
                          from: { opacity: 0, transform: 'translateY(8px) scale(0.98)' },
                          to: { opacity: 1, transform: 'translateY(0) scale(1)' },
                        },
                      }}
                    >
                      <Avatar
                        src={message.senderAvatar}
                        sx={{
                          width: 28,
                          height: 28,
                          fontSize: '0.68rem',
                          fontWeight: 800,
                          background: VIBE_GRADIENT,
                          color: '#fff',
                          border: '2px solid',
                          borderColor: 'background.paper',
                        }}
                      >
                        {INITIALS(message.senderName)}
                      </Avatar>
                      <Box sx={{ maxWidth: '78%', display: 'flex', flexDirection: 'column', alignItems: isMine ? 'flex-end' : 'flex-start' }}>
                        {!isMine && (
                          <Typography variant="caption" sx={{ display: 'block', ml: 1, mb: 0.25, fontWeight: 700, color: VIBE_TEAL }}>
                            {message.senderName || t('roomViewer')}
                          </Typography>
                        )}
                        <Box sx={bubbleSx(theme, isMine, true)}>
                          <Typography variant="body2" sx={{ lineHeight: 1.45 }}>{message.content}</Typography>
                        </Box>
                      </Box>
                    </Box>
                  );
                })}
                <div ref={messagesEndRef} />
              </Box>

              <Box sx={{ p: 1.5, borderTop: '1px solid', borderColor: vibeBorder(theme), display: 'flex', alignItems: 'center', gap: 1 }}>
                <Box sx={{ ...composerBarSx(theme), flex: 1 }}>
                  <InputBase
                    fullWidth
                    placeholder={t('chatPlaceholder')}
                    value={messageInput}
                    onChange={(e) => setMessageInput(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' && !e.shiftKey) {
                        e.preventDefault();
                        handleSendMessage();
                      }
                    }}
                    inputProps={{ 'aria-label': t('chatPlaceholder') }}
                    sx={{ fontSize: '0.92rem' }}
                  />
                </Box>
                <GradientSendButton onClick={handleSendMessage} disabled={!messageInput.trim()} />
              </Box>
            </Paper>
          </Grid>
        </Grid>

        {/* Below the player + chat (playlist-style) so the video and the chat composer always fit
            on screen; the host scrolls down to switch episodes. */}
        <Box sx={{ mt: 2 }}>
          <EpisodePicker
            episodes={episodes}
            currentEpisodeId={room.episodeId}
            onSelect={(episode) => void handleEpisodeChange(episode.id)}
            title={isHost ? (room.episodeId ? t('changeEpisode') : t('selectStartingEpisode')) : (room.filmTitle || t('chooseEpisode'))}
            subtitle={isHost ? t('hostCanChangeEpisode') : t('episodeControlledByHost')}
            disabled={!isHost}
            busy={changingEpisode}
            maxGridHeight={196}
            emptyText={t('noRoomEpisodes')}
          />
        </Box>
      </Container>
    </Box>
  );
};

export default WatchRoom;
