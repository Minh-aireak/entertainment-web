import React, { useCallback, useEffect, useRef } from 'react';
import { Box, CircularProgress, IconButton, Paper, Tooltip, Typography } from '@mui/material';
import { DragIndicator, OpenInFull } from '@mui/icons-material';
import { useLocation, useNavigate } from 'react-router-dom';
import VideoPlayer, { type PlaybackActionPayload, type VideoPlayerHandle } from './VideoPlayer';
import { useWatchRoomSession } from '../../contexts/watchRoomSessionContextValue';
import { useDraggableFloating } from '../../hooks/useDraggableFloating';
import { useTranslation } from 'react-i18next';

const MINI_WIDTH = 390;
const MINI_HEIGHT = 282;

const WatchRoomMiniPlayer: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { t } = useTranslation();
  const {
    room,
    episodes,
    videoSrc,
    videoError,
    syncTarget,
    hostLocalSnapshotRef,
    sendPlayback,
    refreshVideo,
  } = useWatchRoomSession();
  const viewerRef = useRef<VideoPlayerHandle>(null);
  const hostPlayerRef = useRef<VideoPlayerHandle>(null);
  const { position, dragging, dragHandleProps } = useDraggableFloating(
    'watch-room-mini-position',
    MINI_WIDTH,
    MINI_HEIGHT,
  );

  const isFullRoomPage = room ? location.pathname === `/film/watch-together/room/${room.id}` : false;
  const currentEpisode = room?.episodeId ? episodes.find((episode) => episode.id === room.episodeId) : undefined;

  useEffect(() => {
    if (!room || room.host || !videoSrc) return;
    viewerRef.current?.syncTo({
      positionSeconds: room.positionSeconds,
      playing: room.playing,
      playbackRate: room.playbackRate,
      action: 'JOIN',
    });
  }, [room, videoSrc]);

  useEffect(() => {
    if (!syncTarget) return;
    if (room?.host) {
      if (syncTarget.applyToHost) hostPlayerRef.current?.syncTo(syncTarget);
      return;
    }
    viewerRef.current?.syncTo(syncTarget);
  }, [syncTarget, room?.host]);

  // See WatchRoom.tsx's attachHostPlayerRef for why this is a callback ref rather than a
  // useEffect - it must fire exactly when this mini player's VideoPlayer instance actually
  // mounts (e.g. right after navigating away from the full room page), not on every unrelated
  // re-render, and this component in particular toggles visibility (via the isFullRoomPage early
  // return below) without unmounting the whole component tree.
  const attachHostPlayerRef = useCallback((handle: VideoPlayerHandle | null) => {
    hostPlayerRef.current = handle;
    if (handle) handle.restoreHostState(hostLocalSnapshotRef.current);
  }, [hostLocalSnapshotRef]);

  if (!room || room.status !== 'ACTIVE' || isFullRoomPage) return null;

  const handleHostAction = (action: PlaybackActionPayload) => {
    switch (action.type) {
      case 'play':
        sendPlayback({ action: 'PLAY', positionSeconds: action.positionSeconds });
        break;
      case 'pause':
        sendPlayback({ action: 'PAUSE', positionSeconds: action.positionSeconds });
        break;
      case 'seek':
        sendPlayback({ action: 'SEEK', positionSeconds: action.positionSeconds });
        break;
      case 'rate':
        sendPlayback({ action: 'HEARTBEAT', positionSeconds: action.positionSeconds, playbackRate: action.playbackRate });
        break;
      case 'heartbeat':
        sendPlayback({ action: 'HEARTBEAT', positionSeconds: action.positionSeconds });
        break;
    }
  };

  return (
    <Paper
      elevation={18}
      sx={{
        position: 'fixed',
        left: position.x,
        top: position.y,
        zIndex: (theme) => theme.zIndex.modal + 1,
        width: { xs: 'calc(100vw - 24px)', sm: MINI_WIDTH },
        maxWidth: MINI_WIDTH,
        overflow: 'hidden',
        borderRadius: 2.5,
        border: '1px solid',
        borderColor: 'primary.main',
        boxShadow: '0 18px 50px rgba(0,0,0,0.48)',
      }}
    >
      <Box
        {...dragHandleProps}
        sx={{
          height: 42,
          px: 1,
          display: 'flex',
          alignItems: 'center',
          gap: 0.75,
          bgcolor: 'background.paper',
          cursor: dragging ? 'grabbing' : 'grab',
          touchAction: 'none',
          userSelect: 'none',
        }}
      >
        <DragIndicator color="action" fontSize="small" />
        <Box sx={{ minWidth: 0, flex: 1 }}>
          <Typography variant="caption" noWrap sx={{ display: 'block', fontWeight: 800 }}>
            {room.filmTitle || room.name}
          </Typography>
          <Typography variant="caption" color="text.secondary" noWrap sx={{ display: 'block', lineHeight: 1 }}>
            {currentEpisode ? `${t('episodeLabel', { episode: currentEpisode.episodeNumber })} · ${currentEpisode.title}` : t('waitingForHostEpisode')}
          </Typography>
        </Box>
        <Tooltip title={t('backToWatchRoom')}>
          <IconButton
            size="small"
            onPointerDown={(event) => event.stopPropagation()}
            onClick={() => navigate(`/film/watch-together/room/${room.id}`)}
          >
            <OpenInFull fontSize="small" />
          </IconButton>
        </Tooltip>
      </Box>

      <Box sx={{ height: 220, bgcolor: '#000', position: 'relative' }}>
        {videoError ? (
          <Box sx={{ position: 'absolute', inset: 0, display: 'grid', placeItems: 'center', p: 2 }}>
            <Typography variant="body2" color="text.secondary" align="center">{videoError}</Typography>
          </Box>
        ) : videoSrc && room.episodeId ? (
          <VideoPlayer
            key={room.episodeId}
            ref={room.host ? attachHostPlayerRef : viewerRef}
            src={videoSrc}
            title={currentEpisode?.title || room.filmTitle}
            role={room.host ? 'host' : 'viewer'}
            autoPlay={room.host ? false : room.playing}
            onPlaybackAction={room.host ? handleHostAction : undefined}
            onStalledError={refreshVideo}
            onLocalTimeUpdate={room.host ? (state) => { hostLocalSnapshotRef.current = state; } : undefined}
            style={{ width: '100%', height: '100%', maxWidth: '100%', aspectRatio: 'auto' }}
          />
        ) : room.episodeId ? (
          <Box sx={{ position: 'absolute', inset: 0, display: 'grid', placeItems: 'center' }}>
            <CircularProgress size={28} />
          </Box>
        ) : (
          <Box sx={{ position: 'absolute', inset: 0, display: 'grid', placeItems: 'center', p: 2 }}>
            <Typography variant="body2" color="text.secondary" align="center">
              {t('returnToChooseEpisode')}
            </Typography>
          </Box>
        )}
      </Box>
    </Paper>
  );
};

export default WatchRoomMiniPlayer;
