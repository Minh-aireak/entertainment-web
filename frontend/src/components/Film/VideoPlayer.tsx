import React, { useCallback, useEffect, useImperativeHandle, useRef, useState } from 'react';
import Hls from 'hls.js';
import { Box, Fade, IconButton, Menu, MenuItem, Tooltip, Typography } from '@mui/material';
import {
  Fullscreen,
  FullscreenExit,
  Forward5,
  Pause,
  PlayArrow,
  Replay5,
  Settings,
  SkipNext,
  VolumeDown,
  VolumeOff,
  VolumeUp,
} from '@mui/icons-material';

export type PlaybackActionPayload =
  | { type: 'play'; positionSeconds: number }
  | { type: 'pause'; positionSeconds: number }
  | { type: 'seek'; positionSeconds: number }
  // Carries positionSeconds too (not just the new rate) so the position/timestamp the server
  // stores stays mutually consistent - otherwise a pure rate change would refresh lastActionAt
  // without refreshing positionSeconds, snapping viewers back to a stale position on next sync.
  | { type: 'rate'; playbackRate: number; positionSeconds: number }
  // Periodic resync ping while a host player is playing - lets late joiners (and any viewer
  // whose local clock has drifted) correct without the host needing to pause/seek.
  | { type: 'heartbeat'; positionSeconds: number };

export interface VideoPlayerHandle {
  /** Applied by a 'viewer' player to follow a host's broadcast state. Uses a soft-correction
   *  threshold on position so periodic heartbeats don't cause visible jitter for a viewer whose
   *  local playback is already close enough. */
  syncTo: (state: { positionSeconds: number; playing: boolean; playbackRate: number }) => void;
}

interface VideoPlayerProps {
  src: string;
  poster?: string;
  title?: string;
  autoPlay?: boolean;
  onNextEpisode?: () => void;
  hasNextEpisode?: boolean;
  style?: React.CSSProperties;
  /** Omit for a normal standalone player (unchanged behavior). 'host' surfaces every
   *  play/pause/seek/rate-change through onPlaybackAction so a caller can broadcast it.
   *  'viewer' disables playback-affecting controls (progress bar, play/pause, skip, speed) -
   *  volume and fullscreen stay local-only either way since they aren't shared state. */
  role?: 'host' | 'viewer';
  onPlaybackAction?: (action: PlaybackActionPayload) => void;
}

const SKIP_SECONDS = 5;
const PLAYBACK_RATES = [0.5, 0.75, 1, 1.25, 1.5, 2];
const CONTROLS_HIDE_DELAY_MS = 2500;
const SYNC_DRIFT_THRESHOLD_SECONDS = 1.5;

function formatTime(totalSeconds: number): string {
  if (!Number.isFinite(totalSeconds) || totalSeconds < 0) return '0:00';
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = Math.floor(totalSeconds % 60);
  const secondsStr = String(seconds).padStart(2, '0');
  if (hours > 0) return `${hours}:${String(minutes).padStart(2, '0')}:${secondsStr}`;
  return `${minutes}:${secondsStr}`;
}

const VideoPlayer = React.forwardRef<VideoPlayerHandle, VideoPlayerProps>(({
  src,
  poster,
  title,
  autoPlay = true,
  onNextEpisode,
  hasNextEpisode = false,
  style,
  role,
  onPlaybackAction,
}, ref) => {
  const videoRef = useRef<HTMLVideoElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const progressBarRef = useRef<HTMLDivElement>(null);
  const hideControlsTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const [playing, setPlaying] = useState(autoPlay);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [bufferedEnd, setBufferedEnd] = useState(0);
  const [volume, setVolume] = useState(1);
  const [muted, setMuted] = useState(false);
  const [showControls, setShowControls] = useState(true);
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [isScrubbing, setIsScrubbing] = useState(false);
  const [playbackRate, setPlaybackRate] = useState(1);
  const [settingsAnchor, setSettingsAnchor] = useState<null | HTMLElement>(null);

  // Only a 'viewer' player is driven externally; a 'host' (or a plain standalone) player keeps
  // full local control and, for 'host', also reports every change via onPlaybackAction.
  const interactive = role !== 'viewer';

  const notifyHostAction = useCallback(
    (action: PlaybackActionPayload) => {
      if (role === 'host') onPlaybackAction?.(action);
    },
    [role, onPlaybackAction],
  );

  // Load the source (HLS via hls.js, or let the browser/B2 handle it natively).
  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;

    const isHlsSource = src.endsWith('.m3u8');
    let hls: Hls | null = null;

    if (isHlsSource && Hls.isSupported()) {
      hls = new Hls();
      hls.loadSource(src);
      hls.attachMedia(video);
    } else {
      video.src = src;
    }

    return () => {
      hls?.destroy();
    };
  }, [src]);

  const resetHideTimer = useCallback(() => {
    setShowControls(true);
    if (hideControlsTimer.current) clearTimeout(hideControlsTimer.current);
    hideControlsTimer.current = setTimeout(() => {
      if (videoRef.current && !videoRef.current.paused) setShowControls(false);
    }, CONTROLS_HIDE_DELAY_MS);
  }, []);

  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;

    const onTimeUpdate = () => setCurrentTime(video.currentTime);
    const onDurationChange = () => setDuration(video.duration || 0);
    const onProgress = () => {
      if (video.buffered.length > 0) {
        setBufferedEnd(video.buffered.end(video.buffered.length - 1));
      }
    };
    const onPlay = () => {
      setPlaying(true);
      resetHideTimer();
      notifyHostAction({ type: 'play', positionSeconds: video.currentTime });
    };
    const onPause = () => {
      setPlaying(false);
      if (hideControlsTimer.current) clearTimeout(hideControlsTimer.current);
      setShowControls(true);
      notifyHostAction({ type: 'pause', positionSeconds: video.currentTime });
    };
    const onVolumeChange = () => {
      setVolume(video.volume);
      setMuted(video.muted);
    };
    const onEnded = () => {
      if (hasNextEpisode) onNextEpisode?.();
    };

    video.addEventListener('timeupdate', onTimeUpdate);
    video.addEventListener('durationchange', onDurationChange);
    video.addEventListener('loadedmetadata', onDurationChange);
    video.addEventListener('progress', onProgress);
    video.addEventListener('play', onPlay);
    video.addEventListener('pause', onPause);
    video.addEventListener('volumechange', onVolumeChange);
    video.addEventListener('ended', onEnded);

    return () => {
      video.removeEventListener('timeupdate', onTimeUpdate);
      video.removeEventListener('durationchange', onDurationChange);
      video.removeEventListener('loadedmetadata', onDurationChange);
      video.removeEventListener('progress', onProgress);
      video.removeEventListener('play', onPlay);
      video.removeEventListener('pause', onPause);
      video.removeEventListener('volumechange', onVolumeChange);
      video.removeEventListener('ended', onEnded);
    };
  }, [hasNextEpisode, onNextEpisode, resetHideTimer, notifyHostAction]);

  useEffect(() => {
    const onFullscreenChange = () => setIsFullscreen(document.fullscreenElement === containerRef.current);
    document.addEventListener('fullscreenchange', onFullscreenChange);
    return () => document.removeEventListener('fullscreenchange', onFullscreenChange);
  }, []);

  useEffect(() => {
    if (role !== 'host') return;
    const interval = setInterval(() => {
      const video = videoRef.current;
      if (!video || video.paused) return;
      notifyHostAction({ type: 'heartbeat', positionSeconds: video.currentTime });
    }, 5000);
    return () => clearInterval(interval);
  }, [role, notifyHostAction]);

  const togglePlay = useCallback(() => {
    if (!interactive) return;
    const video = videoRef.current;
    if (!video) return;
    if (video.paused) video.play();
    else video.pause();
  }, [interactive]);

  const seekTo = useCallback(
    (time: number, notify = false) => {
      const video = videoRef.current;
      if (!video || !Number.isFinite(duration) || duration <= 0) return;
      const clamped = Math.min(Math.max(time, 0), duration);
      video.currentTime = clamped;
      setCurrentTime(clamped);
      if (notify) notifyHostAction({ type: 'seek', positionSeconds: clamped });
    },
    [duration, notifyHostAction],
  );

  const skip = useCallback(
    (deltaSeconds: number) => {
      if (!interactive) return;
      const video = videoRef.current;
      if (!video) return;
      seekTo(video.currentTime + deltaSeconds, true);
    },
    [interactive, seekTo],
  );

  const toggleMute = useCallback(() => {
    const video = videoRef.current;
    if (!video) return;
    video.muted = !video.muted;
  }, []);

  const changeVolume = useCallback((next: number) => {
    const video = videoRef.current;
    if (!video) return;
    const clamped = Math.min(Math.max(next, 0), 1);
    video.volume = clamped;
    video.muted = clamped === 0;
  }, []);

  const toggleFullscreen = useCallback(() => {
    const container = containerRef.current;
    if (!container) return;
    if (document.fullscreenElement) {
      document.exitFullscreen();
    } else {
      container.requestFullscreen();
    }
  }, []);

  const changePlaybackRate = useCallback((rate: number) => {
    if (!interactive) return;
    const video = videoRef.current;
    if (!video) return;
    video.playbackRate = rate;
    setPlaybackRate(rate);
    setSettingsAnchor(null);
    notifyHostAction({ type: 'rate', playbackRate: rate, positionSeconds: video.currentTime });
  }, [interactive, notifyHostAction]);

  useImperativeHandle(ref, () => ({
    syncTo: ({ positionSeconds, playing: shouldPlay, playbackRate: rate }) => {
      const video = videoRef.current;
      if (!video) return;

      if (Number.isFinite(positionSeconds) && Math.abs(video.currentTime - positionSeconds) > SYNC_DRIFT_THRESHOLD_SECONDS) {
        video.currentTime = positionSeconds;
        setCurrentTime(positionSeconds);
      }

      if (Number.isFinite(rate) && rate > 0 && video.playbackRate !== rate) {
        video.playbackRate = rate;
        setPlaybackRate(rate);
      }

      if (shouldPlay && video.paused) {
        video.play().catch(() => {
          // Autoplay can be blocked before the viewer has interacted with the page - the play
          // overlay/button stays visible so they can start it manually, next syncTo retries.
        });
      } else if (!shouldPlay && !video.paused) {
        video.pause();
      }
    },
  }), []);

  useEffect(
    () => () => {
      if (hideControlsTimer.current) clearTimeout(hideControlsTimer.current);
    },
    [],
  );

  // Keyboard shortcuts: Left/Right arrows seek, Space toggles play/pause.
  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if (!interactive) return;
      const target = e.target as HTMLElement | null;
      const tag = target?.tagName;
      const isTyping = tag === 'INPUT' || tag === 'TEXTAREA' || target?.isContentEditable;
      if (isTyping || !videoRef.current) return;

      if (e.code === 'Space') {
        e.preventDefault();
        togglePlay();
        resetHideTimer();
      } else if (e.code === 'ArrowRight') {
        e.preventDefault();
        skip(SKIP_SECONDS);
        resetHideTimer();
      } else if (e.code === 'ArrowLeft') {
        e.preventDefault();
        skip(-SKIP_SECONDS);
        resetHideTimer();
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [interactive, togglePlay, skip, resetHideTimer]);

  const handleSeekPointer = useCallback(
    (clientX: number) => {
      const bar = progressBarRef.current;
      if (!bar || duration <= 0) return;
      const rect = bar.getBoundingClientRect();
      const ratio = Math.min(Math.max((clientX - rect.left) / rect.width, 0), 1);
      seekTo(ratio * duration);
    },
    [duration, seekTo],
  );

  const handleSeekMouseDown = (e: React.MouseEvent) => {
    if (!interactive) return;
    setIsScrubbing(true);
    handleSeekPointer(e.clientX);
  };

  useEffect(() => {
    if (!isScrubbing) return;
    const onMove = (e: MouseEvent) => handleSeekPointer(e.clientX);
    const onUp = () => {
      setIsScrubbing(false);
      if (videoRef.current) {
        notifyHostAction({ type: 'seek', positionSeconds: videoRef.current.currentTime });
      }
    };
    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
    return () => {
      window.removeEventListener('mousemove', onMove);
      window.removeEventListener('mouseup', onUp);
    };
  }, [isScrubbing, handleSeekPointer, notifyHostAction]);

  const playedRatio = duration > 0 ? currentTime / duration : 0;
  const bufferedRatio = duration > 0 ? bufferedEnd / duration : 0;
  const VolumeIcon = muted || volume === 0 ? VolumeOff : volume < 0.5 ? VolumeDown : VolumeUp;

  return (
    <Box
      ref={containerRef}
      onMouseMove={resetHideTimer}
      onMouseLeave={() => playing && setShowControls(false)}
      onDoubleClick={toggleFullscreen}
      sx={{
        position: 'relative',
        width: '100%',
        maxWidth: isFullscreen ? '100%' : 1120,
        mx: 'auto',
        aspectRatio: isFullscreen ? 'auto' : '16 / 9',
        height: isFullscreen ? '100%' : 'auto',
        bgcolor: '#000',
        overflow: 'hidden',
        borderRadius: isFullscreen ? 0 : 2,
        cursor: showControls ? 'default' : 'none',
        ...style,
      }}
    >
      <video
        ref={videoRef}
        poster={poster}
        autoPlay={autoPlay}
        onClick={interactive ? togglePlay : undefined}
        style={{ width: '100%', height: '100%', display: 'block', objectFit: 'contain', backgroundColor: '#000' }}
      />

      <Box
        onClick={interactive ? togglePlay : undefined}
        sx={{
          position: 'absolute',
          inset: 0,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          cursor: interactive ? 'pointer' : 'default',
          opacity: playing ? 0 : 1,
          pointerEvents: playing ? 'none' : 'auto',
          transition: 'opacity 0.2s',
        }}
      >
        <Box
          sx={{
            width: 72,
            height: 72,
            borderRadius: '50%',
            bgcolor: 'rgba(0,0,0,0.5)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <PlayArrow sx={{ fontSize: 40, color: '#fff' }} />
        </Box>
      </Box>

      <Fade in={showControls}>
        <Box
          sx={{
            position: 'absolute',
            top: 0,
            left: 0,
            right: 0,
            p: 1.5,
            background: 'linear-gradient(to bottom, rgba(0,0,0,0.65), transparent)',
            pointerEvents: showControls ? 'auto' : 'none',
          }}
        >
          {title && (
            <Typography noWrap sx={{ color: '#fff', fontWeight: 600, fontSize: '0.95rem' }}>
              {title}
            </Typography>
          )}
        </Box>
      </Fade>

      <Fade in={showControls}>
        <Box
          sx={{
            position: 'absolute',
            bottom: 0,
            left: 0,
            right: 0,
            px: 1.5,
            pb: 1,
            pt: 3,
            background: 'linear-gradient(to top, rgba(0,0,0,0.8), transparent)',
            pointerEvents: showControls ? 'auto' : 'none',
          }}
        >
          <Box
            ref={progressBarRef}
            onMouseDown={handleSeekMouseDown}
            sx={{
              position: 'relative',
              height: 5,
              borderRadius: 3,
              bgcolor: 'rgba(255,255,255,0.25)',
              cursor: interactive ? 'pointer' : 'default',
              mb: 1,
              '&:hover': interactive ? { height: 7 } : undefined,
            }}
          >
            <Box
              sx={{
                position: 'absolute',
                top: 0,
                left: 0,
                height: '100%',
                width: `${bufferedRatio * 100}%`,
                bgcolor: 'rgba(255,255,255,0.4)',
                borderRadius: 3,
              }}
            />
            <Box
              sx={{
                position: 'absolute',
                top: 0,
                left: 0,
                height: '100%',
                width: `${playedRatio * 100}%`,
                bgcolor: '#e50914',
                borderRadius: 3,
              }}
            />
            <Box
              sx={{
                position: 'absolute',
                top: '50%',
                left: `${playedRatio * 100}%`,
                width: 12,
                height: 12,
                borderRadius: '50%',
                bgcolor: '#e50914',
                transform: 'translate(-50%, -50%)',
              }}
            />
          </Box>

          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
            <IconButton size="small" onClick={togglePlay} disabled={!interactive} sx={{ color: '#fff' }}>
              {playing ? <Pause /> : <PlayArrow />}
            </IconButton>
            <IconButton size="small" onClick={() => skip(-SKIP_SECONDS)} disabled={!interactive} sx={{ color: '#fff' }}>
              <Replay5 />
            </IconButton>
            <IconButton size="small" onClick={() => skip(SKIP_SECONDS)} disabled={!interactive} sx={{ color: '#fff' }}>
              <Forward5 />
            </IconButton>

            <Box sx={{ display: 'flex', alignItems: 'center', '&:hover .volume-slider': { width: 60, opacity: 1 } }}>
              <IconButton size="small" onClick={toggleMute} sx={{ color: '#fff' }}>
                <VolumeIcon fontSize="small" />
              </IconButton>
              <Box
                className="volume-slider"
                sx={{ width: 0, opacity: 0, transition: 'all 0.2s', overflow: 'hidden', display: 'flex', alignItems: 'center' }}
              >
                <input
                  type="range"
                  min={0}
                  max={1}
                  step={0.05}
                  value={muted ? 0 : volume}
                  onChange={(e) => changeVolume(Number(e.target.value))}
                  style={{ width: 60, accentColor: '#e50914' }}
                />
              </Box>
            </Box>

            <Typography sx={{ color: '#fff', fontSize: '0.8rem', ml: 0.5, whiteSpace: 'nowrap' }}>
              {formatTime(currentTime)} / {formatTime(duration)}
            </Typography>

            <Box sx={{ flexGrow: 1 }} />

            {!interactive && (
              <Typography sx={{ color: 'rgba(255,255,255,0.6)', fontSize: '0.75rem', mr: 1, whiteSpace: 'nowrap' }}>
                Chủ phòng đang điều khiển
              </Typography>
            )}

            {onNextEpisode && (
              <Tooltip title="Tập tiếp theo">
                <span>
                  <IconButton size="small" onClick={onNextEpisode} disabled={!interactive || !hasNextEpisode} sx={{ color: '#fff' }}>
                    <SkipNext />
                  </IconButton>
                </span>
              </Tooltip>
            )}

            <Tooltip title="Tốc độ phát">
              <span>
                <IconButton size="small" onClick={(e) => setSettingsAnchor(e.currentTarget)} disabled={!interactive} sx={{ color: '#fff' }}>
                  <Settings fontSize="small" />
                </IconButton>
              </span>
            </Tooltip>
            <Menu anchorEl={settingsAnchor} open={!!settingsAnchor} onClose={() => setSettingsAnchor(null)}>
              {PLAYBACK_RATES.map((rate) => (
                <MenuItem key={rate} selected={rate === playbackRate} onClick={() => changePlaybackRate(rate)}>
                  {rate === 1 ? 'Bình thường' : `${rate}x`}
                </MenuItem>
              ))}
            </Menu>

            <IconButton size="small" onClick={toggleFullscreen} sx={{ color: '#fff' }}>
              {isFullscreen ? <FullscreenExit /> : <Fullscreen />}
            </IconButton>
          </Box>
        </Box>
      </Fade>
    </Box>
  );
});

VideoPlayer.displayName = 'VideoPlayer';

export default VideoPlayer;
