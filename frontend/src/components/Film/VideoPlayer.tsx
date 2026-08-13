import React, { useCallback, useEffect, useImperativeHandle, useRef, useState } from 'react';
import Hls from 'hls.js';
import { Box, CircularProgress, Fade, IconButton, Menu, MenuItem, Tooltip, Typography } from '@mui/material';
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
import { useTranslation } from 'react-i18next';
import type { SyncAction } from '../../models';

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
  /** Applied by a 'viewer' player to follow a host's broadcast state. `action` picks the
   *  correction strategy: PAUSE/SEEK/CHANGE_EPISODE/JOIN always snap to the exact position
   *  before applying play/pause (hard sync); PLAY only skips the seek if already very close;
   *  HEARTBEAT never seeks - a moderate drift is closed with a brief, subtle playbackRate nudge
   *  (see applyHeartbeatCatchup) instead of a visible jump, only falling back to a hard snap once
   *  drift is too large to close invisibly. */
  syncTo: (state: { positionSeconds: number; playing: boolean; playbackRate: number; action: SyncAction }) => void;
  /** Applied by a 'host' player right after mount (before any user interaction) to restore the
   *  position/playing/rate it had before an unmount (e.g. switching between the full room page
   *  and the mini player) - unlike syncTo, this always hard-sets state and suppresses the native
   *  play/pause event it causes from being reported as a real host action. */
  restoreHostState: (state: { positionSeconds: number; playing: boolean; playbackRate: number }) => void;
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
  /** Called when playback breaks in a way the player can't recover from on its own - a native
   *  `error` event (e.g. an expired B2 presigned URL) or the browser coming back online while
   *  still stuck buffering. The caller is expected to resolve a fresh `src` (new presigned URL)
   *  and pass it back down; the player preserves the current position/playing state across the
   *  `src` swap so playback resumes where it left off instead of restarting from 0. */
  onStalledError?: () => void;
  /** Local-only echo of the current position/playing/rate on every timeupdate/play/pause -
   *  never sent to the server. Lets a 'host' caller keep a live snapshot (e.g.
   *  WatchRoomSessionContext's hostLocalSnapshotRef) so a later remount (full page <-> mini
   *  player) can call restoreHostState with fresh values instead of restarting from 0. */
  onLocalTimeUpdate?: (state: { positionSeconds: number; durationSeconds: number; playing: boolean; playbackRate: number }) => void;
  /** Seconds to resume from on first mount (e.g. a saved "Continue Watching" position) - only
   *  read once, on mount, exactly like the internal resume-after-src-swap it reuses. */
  initialPositionSeconds?: number;
}

const SKIP_SECONDS = 5;
const PLAYBACK_RATES = [0.5, 0.75, 1, 1.25, 1.5, 2];
const CONTROLS_HIDE_DELAY_MS = 2500;
// Per-action correction threshold for syncTo, in seconds. 0 means always snap to the exact
// position before applying play/pause (hard sync) - PAUSE/SEEK/CHANGE_EPISODE/JOIN must always
// land viewers on the host's exact position. PLAY only skips the seek if already very close.
// HEARTBEAT uses a looser threshold so periodic resyncs don't cause visible jitter for a viewer
// whose local playback already tracks closely enough.
const SYNC_ACTION_THRESHOLDS_SECONDS: Record<SyncAction, number> = {
  PAUSE: 0,
  SEEK: 0,
  CHANGE_EPISODE: 0,
  JOIN: 0,
  PLAY: 0.35,
  HEARTBEAT: 1.2,
};
// HEARTBEAT drift below this is imperceptible - leave the rate alone entirely rather than
// nudging for no visible benefit.
const SOFT_CATCHUP_MIN_DRIFT_SECONDS = 0.35;
// A subtle, roughly-imperceptible speed nudge (+/-4%) used to close HEARTBEAT drift gradually
// instead of a hard seek. Deliberately small and slow rather than sized to close the gap within
// one heartbeat cycle - each heartbeat re-measures actual drift and re-nudges, so a large initial
// drift just converges over several cycles, invisibly, rather than jumping.
const SOFT_CATCHUP_RATE_DELTA = 0.04;
// Safety revert: if no further HEARTBEAT arrives (host paused, network issue) while a nudge is
// active, don't leave playbackRate altered forever. Set just under the ~5s heartbeat cadence so
// under normal conditions the next heartbeat always refreshes the nudge before this fires.
const SOFT_CATCHUP_REVERT_MS = 4500;
const SEEK_THROTTLE_MS = 150;
const STALLED_ERROR_COOLDOWN_MS = 4000;

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
  onStalledError,
  onLocalTimeUpdate,
  initialPositionSeconds,
}, ref) => {
  const { t } = useTranslation();
  const videoRef = useRef<HTMLVideoElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const progressBarRef = useRef<HTMLDivElement>(null);
  const hideControlsTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  // Position/playing snapshot kept up to date on every tick so a src swap (fresh presigned URL
  // after an error/reconnect) can resume where playback left off instead of restarting at 0.
  // Seeded from initialPositionSeconds so a fresh mount (e.g. opening from "Continue Watching")
  // resumes there via the same loadedmetadata resume logic below, instead of restarting at 0.
  const lastTimeRef = useRef(initialPositionSeconds && initialPositionSeconds > 0 ? initialPositionSeconds : 0);
  const wasPlayingRef = useRef(autoPlay);
  const lastSeekCommitRef = useRef(0);
  const scrubTimeRef = useRef(0);
  const lastStalledErrorRef = useRef(0);
  // One-shot: set by restoreHostState right before it calls play()/pause() so the native
  // play/pause event that call causes isn't reported to the server as a real host action.
  const suppressNextNotifyRef = useRef(false);
  // Pending "revert to normal speed" timer for an in-progress HEARTBEAT soft catch-up nudge.
  const catchupRevertTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const [playing, setPlaying] = useState(autoPlay);
  const [showStartOverlay, setShowStartOverlay] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [bufferedEnd, setBufferedEnd] = useState(0);
  const [buffering, setBuffering] = useState(false);
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

  // Load the source (HLS via hls.js, or let the browser/B2 handle it natively). Beyond the
  // initial mount, `src` only ever changes because a caller refreshed a stale/expired presigned
  // URL for the SAME episode (episode switches remount this component via a `key` change), so
  // restoring the last known position/playing state here is always correct - never a stale replay
  // across a genuine episode change.
  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;

    const resumeTime = lastTimeRef.current;
    const shouldResume = wasPlayingRef.current;

    const onLoadedMeta = () => {
      if (resumeTime > 0) video.currentTime = resumeTime;
      if (shouldResume) video.play().catch(() => {});
    };
    video.addEventListener('loadedmetadata', onLoadedMeta, { once: true });

    const isHlsSource = src.endsWith('.m3u8');
    let hls: Hls | null = null;

    if (isHlsSource && Hls.isSupported()) {
      hls = new Hls();
      hls.loadSource(src);
      hls.attachMedia(video);
      // hls.js has its own fragment/manifest retry logic, more robust for mid-playback network
      // blips than the generic native-<video> `error`/`online` recovery below (which just swaps
      // `src` wholesale) - only fall through to that for errors hls.js itself can't recover from.
      hls.on(Hls.Events.ERROR, (_event, data) => {
        if (!data.fatal) return;
        switch (data.type) {
          case Hls.ErrorTypes.NETWORK_ERROR:
            hls?.startLoad();
            break;
          case Hls.ErrorTypes.MEDIA_ERROR:
            hls?.recoverMediaError();
            break;
          default:
            onStalledError?.();
            break;
        }
      });
    } else {
      video.src = src;
    }

    return () => {
      video.removeEventListener('loadedmetadata', onLoadedMeta);
      hls?.destroy();
    };
  }, [src, onStalledError]);

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

    const onTimeUpdate = () => {
      setCurrentTime(video.currentTime);
      lastTimeRef.current = video.currentTime;
      onLocalTimeUpdate?.({ positionSeconds: video.currentTime, durationSeconds: video.duration || 0, playing: !video.paused, playbackRate: video.playbackRate });
    };
    const onDurationChange = () => setDuration(video.duration || 0);
    const onProgress = () => {
      if (video.buffered.length > 0) {
        setBufferedEnd(video.buffered.end(video.buffered.length - 1));
      }
    };
    const onPlay = () => {
      setPlaying(true);
      wasPlayingRef.current = true;
      resetHideTimer();
      if (suppressNextNotifyRef.current) {
        suppressNextNotifyRef.current = false;
      } else {
        notifyHostAction({ type: 'play', positionSeconds: video.currentTime });
      }
      onLocalTimeUpdate?.({ positionSeconds: video.currentTime, durationSeconds: video.duration || 0, playing: true, playbackRate: video.playbackRate });
    };
    const onPause = () => {
      setPlaying(false);
      wasPlayingRef.current = false;
      if (hideControlsTimer.current) clearTimeout(hideControlsTimer.current);
      setShowControls(true);
      if (suppressNextNotifyRef.current) {
        suppressNextNotifyRef.current = false;
      } else {
        notifyHostAction({ type: 'pause', positionSeconds: video.currentTime });
      }
      onLocalTimeUpdate?.({ positionSeconds: video.currentTime, durationSeconds: video.duration || 0, playing: false, playbackRate: video.playbackRate });
    };
    const onVolumeChange = () => {
      setVolume(video.volume);
      setMuted(video.muted);
    };
    const onEnded = () => {
      if (hasNextEpisode) onNextEpisode?.();
    };
    // Buffering feedback: B2 is fetched directly over HTTP with no backend in between, so any
    // rebuffer (scrub landing ahead of what's downloaded, a dropped connection mid-playback,
    // etc.) surfaces here as `waiting`/`stalled` with nothing else in the DOM changing - without
    // this the player just freezes on the last decoded frame with no visual feedback at all.
    const onWaiting = () => setBuffering(true);
    const onStalled = () => setBuffering(true);
    const onCanPlay = () => setBuffering(false);
    const onPlaying = () => setBuffering(false);

    video.addEventListener('timeupdate', onTimeUpdate);
    video.addEventListener('durationchange', onDurationChange);
    video.addEventListener('loadedmetadata', onDurationChange);
    video.addEventListener('progress', onProgress);
    video.addEventListener('play', onPlay);
    video.addEventListener('pause', onPause);
    video.addEventListener('volumechange', onVolumeChange);
    video.addEventListener('ended', onEnded);
    video.addEventListener('waiting', onWaiting);
    video.addEventListener('stalled', onStalled);
    video.addEventListener('canplay', onCanPlay);
    video.addEventListener('playing', onPlaying);

    return () => {
      video.removeEventListener('timeupdate', onTimeUpdate);
      video.removeEventListener('durationchange', onDurationChange);
      video.removeEventListener('loadedmetadata', onDurationChange);
      video.removeEventListener('progress', onProgress);
      video.removeEventListener('play', onPlay);
      video.removeEventListener('pause', onPause);
      video.removeEventListener('volumechange', onVolumeChange);
      video.removeEventListener('ended', onEnded);
      video.removeEventListener('waiting', onWaiting);
      video.removeEventListener('stalled', onStalled);
      video.removeEventListener('canplay', onCanPlay);
      video.removeEventListener('playing', onPlaying);
    };
  }, [hasNextEpisode, onNextEpisode, resetHideTimer, notifyHostAction, onLocalTimeUpdate]);

  // Error recovery: a native `error` (e.g. an expired 1h B2 presigned URL) or the browser coming
  // back `online` while still stuck buffering are both cases the player can't fix by itself - it
  // has no way to mint a new presigned URL. Hand off to the caller via onStalledError, which is
  // expected to refetch and pass down a fresh `src`; cooldown avoids hammering the caller if
  // connectivity is flapping or the refresh itself keeps failing.
  useEffect(() => {
    const video = videoRef.current;
    if (!video || !onStalledError) return;

    const triggerRecovery = () => {
      const now = Date.now();
      if (now - lastStalledErrorRef.current < STALLED_ERROR_COOLDOWN_MS) return;
      lastStalledErrorRef.current = now;
      onStalledError();
    };

    const onError = () => triggerRecovery();
    const onOnline = () => {
      if (video.error || buffering) triggerRecovery();
    };

    video.addEventListener('error', onError);
    window.addEventListener('online', onOnline);
    return () => {
      video.removeEventListener('error', onError);
      window.removeEventListener('online', onOnline);
    };
  }, [onStalledError, buffering]);

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

  // Local-only resume for a viewer whose browser blocked autoplay - never reports a host action
  // (viewers have no host-control path in the first place), and does not seek/change episode/rate
  // on its own. The next syncTo (heartbeat or event) still applies normally afterward.
  const startWatching = useCallback(() => {
    const video = videoRef.current;
    if (!video) return;
    video.play().then(() => setShowStartOverlay(false)).catch(() => {});
  }, []);

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

  // Commits an actual `video.currentTime` change (i.e. a real B2 range fetch) without touching
  // the displayed time - used by the scrub-drag handler below, which calls this throttled while
  // `setCurrentTime` for the handle/label position updates on every mousemove unthrottled.
  const commitSeek = useCallback(
    (time: number) => {
      const video = videoRef.current;
      if (!video || !Number.isFinite(duration) || duration <= 0) return;
      video.currentTime = Math.min(Math.max(time, 0), duration);
    },
    [duration],
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

  const clearCatchup = useCallback(() => {
    if (catchupRevertTimerRef.current !== null) {
      clearTimeout(catchupRevertTimerRef.current);
      catchupRevertTimerRef.current = null;
    }
  }, []);

  // Handles a HEARTBEAT's position drift without seeking, so a viewer that's merely a little
  // behind/ahead doesn't visibly jump every 5s. Returns true if it fully handled the drift
  // (caller should skip its own seek/rate logic); false if the drift is too large to close
  // smoothly, in which case the caller falls back to a normal hard snap.
  const applyHeartbeatCatchup = useCallback((video: HTMLVideoElement, positionSeconds: number, targetRate: number) => {
    const drift = positionSeconds - video.currentTime; // + = viewer behind, - = viewer ahead
    const absDrift = Math.abs(drift);

    if (absDrift > SYNC_ACTION_THRESHOLDS_SECONDS.HEARTBEAT) {
      return false; // too far to catch up invisibly - let the caller snap directly
    }

    if (absDrift > SOFT_CATCHUP_MIN_DRIFT_SECONDS) {
      const nudgedRate = Math.max(0.1, targetRate + Math.sign(drift) * SOFT_CATCHUP_RATE_DELTA);
      video.playbackRate = nudgedRate;
      catchupRevertTimerRef.current = setTimeout(() => {
        catchupRevertTimerRef.current = null;
        const current = videoRef.current;
        if (current) current.playbackRate = targetRate;
      }, SOFT_CATCHUP_REVERT_MS);
    } else if (video.playbackRate !== targetRate) {
      // Drift already negligible (or a previous nudge already closed it) - make sure a stale
      // nudge isn't still sitting on the rate.
      video.playbackRate = targetRate;
      setPlaybackRate(targetRate);
    }

    return true;
  }, []);

  useImperativeHandle(ref, () => ({
    syncTo: ({ positionSeconds, playing: shouldPlay, playbackRate: rate, action }) => {
      const video = videoRef.current;
      if (!video) return;

      const targetRate = Number.isFinite(rate) && rate > 0 ? rate : video.playbackRate;
      clearCatchup();

      const handledByCatchup = action === 'HEARTBEAT' && shouldPlay && Number.isFinite(positionSeconds)
        && applyHeartbeatCatchup(video, positionSeconds, targetRate);

      if (!handledByCatchup) {
        const threshold = SYNC_ACTION_THRESHOLDS_SECONDS[action] ?? SYNC_ACTION_THRESHOLDS_SECONDS.HEARTBEAT;
        if (Number.isFinite(positionSeconds) && Math.abs(video.currentTime - positionSeconds) > threshold) {
          video.currentTime = positionSeconds;
          setCurrentTime(positionSeconds);
        }
        if (video.playbackRate !== targetRate) {
          video.playbackRate = targetRate;
          setPlaybackRate(targetRate);
        }
      }

      if (shouldPlay && video.paused) {
        video.play().then(() => setShowStartOverlay(false)).catch(() => {
          // Autoplay can be blocked before the viewer has interacted with the page - show the
          // "start watching" overlay so they can start it manually; the next syncTo/event still
          // applies normally once playback begins.
          setShowStartOverlay(true);
        });
      } else if (!shouldPlay && !video.paused) {
        video.pause();
      }
    },
    restoreHostState: ({ positionSeconds, playing: shouldPlay, playbackRate: rate }) => {
      const video = videoRef.current;
      if (!video) return;

      clearCatchup();
      if (Number.isFinite(positionSeconds)) {
        video.currentTime = positionSeconds;
        setCurrentTime(positionSeconds);
      }
      if (Number.isFinite(rate) && rate > 0) {
        video.playbackRate = rate;
        setPlaybackRate(rate);
      }

      if (shouldPlay === video.paused) {
        // A native play/pause event is about to fire from the call below - suppress the one
        // notifyHostAction it would otherwise trigger, since this is a local restore, not a real
        // host action.
        suppressNextNotifyRef.current = true;
        if (shouldPlay) {
          video.play().catch(() => {
            suppressNextNotifyRef.current = false;
          });
        } else {
          video.pause();
        }
      }
      // else: already in the target play/pause state, so no native event will fire - nothing to
      // suppress.
    },
  }), []);

  useEffect(
    () => () => {
      if (hideControlsTimer.current) clearTimeout(hideControlsTimer.current);
      clearCatchup();
    },
    [clearCatchup],
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

  // Dragging fires `mousemove` far faster than B2 can serve fresh byte ranges, so the actual
  // `video.currentTime` write (commitSeek) is throttled - each one cancels/restarts the browser's
  // in-flight range fetch, and doing that on every pixel of movement is what made scrubbing look
  // "stuck" (looked frozen because it was permanently re-buffering, with no indicator saying so;
  // that part is now covered by the `buffering` overlay). The displayed time/handle position
  // (setCurrentTime) still updates on every event so the drag itself still feels responsive.
  const handleSeekPointer = useCallback(
    (clientX: number) => {
      const bar = progressBarRef.current;
      if (!bar || duration <= 0) return;
      const rect = bar.getBoundingClientRect();
      const ratio = Math.min(Math.max((clientX - rect.left) / rect.width, 0), 1);
      const time = ratio * duration;

      scrubTimeRef.current = time;
      setCurrentTime(time);

      const now = Date.now();
      if (now - lastSeekCommitRef.current >= SEEK_THROTTLE_MS) {
        lastSeekCommitRef.current = now;
        commitSeek(time);
      }
    },
    [duration, commitSeek],
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
      lastSeekCommitRef.current = 0;
      // Final commit lands the exact release position even if it fell inside the last throttle
      // window and got skipped.
      commitSeek(scrubTimeRef.current);
      notifyHostAction({ type: 'seek', positionSeconds: scrubTimeRef.current });
    };
    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
    return () => {
      window.removeEventListener('mousemove', onMove);
      window.removeEventListener('mouseup', onUp);
    };
  }, [isScrubbing, handleSeekPointer, commitSeek, notifyHostAction]);

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

      {!interactive && showStartOverlay && (
        <Box
          sx={{
            position: 'absolute',
            inset: 0,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            bgcolor: 'rgba(0,0,0,0.6)',
            zIndex: 1,
          }}
        >
          <Box
            component="button"
            onClick={startWatching}
            sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 1,
              px: 3,
              py: 1.25,
              borderRadius: 999,
              border: 'none',
              bgcolor: '#e50914',
              color: '#fff',
              fontWeight: 700,
              fontSize: '0.95rem',
              cursor: 'pointer',
            }}
          >
            <PlayArrow fontSize="small" />
            {t('startWatching')}
          </Box>
        </Box>
      )}

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

      {playing && buffering && (
        <Box
          sx={{
            position: 'absolute',
            inset: 0,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            pointerEvents: 'none',
          }}
        >
          <CircularProgress sx={{ color: '#fff' }} />
        </Box>
      )}

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
                {t('hostControlling')}
              </Typography>
            )}

            {onNextEpisode && (
              <Tooltip title={t('nextEpisode')}>
                <span>
                  <IconButton size="small" onClick={onNextEpisode} disabled={!interactive || !hasNextEpisode} sx={{ color: '#fff' }}>
                    <SkipNext />
                  </IconButton>
                </span>
              </Tooltip>
            )}

            <Tooltip title={t('playbackSpeed')}>
              <span>
                <IconButton size="small" onClick={(e) => setSettingsAnchor(e.currentTarget)} disabled={!interactive} sx={{ color: '#fff' }}>
                  <Settings fontSize="small" />
                </IconButton>
              </span>
            </Tooltip>
            <Menu anchorEl={settingsAnchor} open={!!settingsAnchor} onClose={() => setSettingsAnchor(null)}>
              {PLAYBACK_RATES.map((rate) => (
                <MenuItem key={rate} selected={rate === playbackRate} onClick={() => changePlaybackRate(rate)}>
                  {rate === 1 ? t('normalSpeed') : `${rate}x`}
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
