import React, { useEffect, useRef } from 'react';
import { Box, CircularProgress, IconButton, Paper, Tooltip, Typography } from '@mui/material';
import { DragIndicator, OpenInFull } from '@mui/icons-material';
import { useLocation, useNavigate } from 'react-router-dom';
import VideoPlayer, { type PlaybackActionPayload, type VideoPlayerHandle } from './VideoPlayer';
import { useWatchRoomSession } from '../../contexts/watchRoomSessionContextValue';
import { useDraggableFloating } from '../../hooks/useDraggableFloating';

const MINI_WIDTH = 390;
const MINI_HEIGHT = 282;

const computeLivePosition = (at: string, position: number, playing: boolean, rate: number) => {
  if (!playing) return position;
  return Math.max(0, position + (Date.now() - new Date(at).getTime()) / 1000 * rate);
};

const WatchRoomMiniPlayer: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const {
    room,
    episodes,
    videoSrc,
    videoError,
    playbackEvent,
    sendPlayback,
    refreshVideo,
  } = useWatchRoomSession();
  const viewerRef = useRef<VideoPlayerHandle>(null);
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
    });
  }, [room, videoSrc]);

  useEffect(() => {
    if (!playbackEvent || room?.host) return;
    viewerRef.current?.syncTo({
      positionSeconds: computeLivePosition(
        playbackEvent.at,
        playbackEvent.positionSeconds,
        playbackEvent.playing,
        playbackEvent.playbackRate,
      ),
      playing: playbackEvent.playing,
      playbackRate: playbackEvent.playbackRate,
    });
  }, [playbackEvent, room?.host]);

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
            {currentEpisode ? `Tập ${currentEpisode.episodeNumber} · ${currentEpisode.title}` : 'Đang chờ chủ phòng chọn tập'}
          </Typography>
        </Box>
        <Tooltip title="Quay lại phòng xem chung">
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
            ref={room.host ? undefined : viewerRef}
            src={videoSrc}
            title={currentEpisode?.title || room.filmTitle}
            role={room.host ? 'host' : 'viewer'}
            autoPlay={room.playing}
            onPlaybackAction={room.host ? handleHostAction : undefined}
            onStalledError={refreshVideo}
            style={{ width: '100%', height: '100%', maxWidth: '100%', aspectRatio: 'auto' }}
          />
        ) : room.episodeId ? (
          <Box sx={{ position: 'absolute', inset: 0, display: 'grid', placeItems: 'center' }}>
            <CircularProgress size={28} />
          </Box>
        ) : (
          <Box sx={{ position: 'absolute', inset: 0, display: 'grid', placeItems: 'center', p: 2 }}>
            <Typography variant="body2" color="text.secondary" align="center">
              Hãy quay lại phòng để chọn tập bắt đầu xem.
            </Typography>
          </Box>
        )}
      </Box>
    </Paper>
  );
};

export default WatchRoomMiniPlayer;
