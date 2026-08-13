import React from 'react';
import { act, fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import '../../i18n';

// @mui/icons-material's barrel export re-exports ~8000 individual icon modules; resolving it via
// the named-import style this file uses (matching VideoPlayer.tsx's own imports) can exceed this
// machine's open-file limit during Vite's dependency scan. Stubbing it out avoids touching the
// real package at all - icon rendering isn't under test here.
vi.mock('@mui/icons-material', () => {
  const Stub = () => null;
  return {
    Fullscreen: Stub,
    FullscreenExit: Stub,
    Forward5: Stub,
    Pause: Stub,
    PlayArrow: Stub,
    Replay5: Stub,
    Settings: Stub,
    SkipNext: Stub,
    VolumeDown: Stub,
    VolumeOff: Stub,
    VolumeUp: Stub,
  };
});

import VideoPlayer, { type PlaybackActionPayload, type VideoPlayerHandle } from './VideoPlayer';

// jsdom's HTMLMediaElement doesn't implement playback - play()/pause() are wired here to flip
// `paused` and dispatch real events, so VideoPlayer's own native play/pause listeners (which the
// suppression-ref and autoplay-blocked-overlay behavior depend on) fire exactly like a browser.
function makeVideoControllable(video: HTMLVideoElement) {
  let paused = true;
  Object.defineProperty(video, 'paused', { configurable: true, get: () => paused });
  video.play = vi.fn(() => {
    paused = false;
    video.dispatchEvent(new Event('play'));
    return Promise.resolve();
  });
  video.pause = vi.fn(() => {
    paused = true;
    video.dispatchEvent(new Event('pause'));
  });
}

const SRC = 'https://example.com/video.mp4';

describe('VideoPlayer syncTo/restoreHostState', () => {
  let ref: React.RefObject<VideoPlayerHandle | null>;
  let video: HTMLVideoElement;
  let onPlaybackAction: ReturnType<typeof vi.fn<(action: PlaybackActionPayload) => void>>;

  beforeEach(() => {
    ref = React.createRef<VideoPlayerHandle>();
    onPlaybackAction = vi.fn();
    const { container } = render(
      <VideoPlayer
        ref={ref}
        role="viewer"
        src={SRC}
        autoPlay={false}
        onPlaybackAction={onPlaybackAction}
      />,
    );
    video = container.querySelector('video') as HTMLVideoElement;
    makeVideoControllable(video);
    video.currentTime = 10;
  });

  it('HEARTBEAT while paused ignores drift under the loose threshold (no catch-up while paused)', () => {
    act(() => {
      ref.current!.syncTo({ positionSeconds: 10.5, playing: false, playbackRate: 1, action: 'HEARTBEAT' });
    });
    expect(video.currentTime).toBe(10);
  });

  it('HEARTBEAT while paused still hard-snaps drift over the loose threshold', () => {
    act(() => {
      ref.current!.syncTo({ positionSeconds: 12.5, playing: false, playbackRate: 1, action: 'HEARTBEAT' });
    });
    expect(video.currentTime).toBe(12.5);
  });

  it('HEARTBEAT while playing ignores negligible drift entirely (no seek, no rate change)', () => {
    act(() => {
      ref.current!.syncTo({ positionSeconds: 10.2, playing: true, playbackRate: 1, action: 'HEARTBEAT' });
    });
    expect(video.currentTime).toBe(10);
    expect(video.playbackRate).toBe(1);
  });

  it('HEARTBEAT while playing closes moderate drift with a rate nudge instead of seeking', () => {
    act(() => {
      // 0.6s behind - within the soft zone (0.35 < drift <= 1.2)
      ref.current!.syncTo({ positionSeconds: 10.6, playing: true, playbackRate: 1, action: 'HEARTBEAT' });
    });
    expect(video.currentTime).toBe(10); // no seek - the whole point
    expect(video.playbackRate).toBeCloseTo(1.04); // nudged up since the viewer is behind

    // Simulate playback having advanced past the host's reported position - viewer is now ahead.
    video.currentTime = 10.5;
    act(() => {
      ref.current!.syncTo({ positionSeconds: 9.9, playing: true, playbackRate: 1, action: 'HEARTBEAT' });
    });
    expect(video.currentTime).toBe(10.5); // still no seek
    expect(video.playbackRate).toBeCloseTo(0.96);
  });

  it('HEARTBEAT while playing still hard-snaps drift too large to close smoothly', () => {
    act(() => {
      ref.current!.syncTo({ positionSeconds: 12.5, playing: true, playbackRate: 1, action: 'HEARTBEAT' });
    });
    expect(video.currentTime).toBe(12.5);
    expect(video.playbackRate).toBe(1); // no lingering nudge from a hard snap
  });

  it('reverts a stale rate nudge if drift closes back to negligible on the next heartbeat', () => {
    act(() => {
      ref.current!.syncTo({ positionSeconds: 10.6, playing: true, playbackRate: 1, action: 'HEARTBEAT' });
    });
    expect(video.playbackRate).toBeCloseTo(1.04);

    // Drift closed (e.g. the nudge worked) - currentTime now matches what the next heartbeat reports.
    video.currentTime = 15;
    act(() => {
      ref.current!.syncTo({ positionSeconds: 15.1, playing: true, playbackRate: 1, action: 'HEARTBEAT' });
    });
    expect(video.playbackRate).toBe(1);
  });

  it('auto-reverts the nudge after the safety timeout if no further heartbeat arrives', () => {
    vi.useFakeTimers();
    try {
      act(() => {
        ref.current!.syncTo({ positionSeconds: 10.6, playing: true, playbackRate: 1, action: 'HEARTBEAT' });
      });
      expect(video.playbackRate).toBeCloseTo(1.04);

      act(() => {
        vi.advanceTimersByTime(5000);
      });
      expect(video.playbackRate).toBe(1);
    } finally {
      vi.useRealTimers();
    }
  });

  it('PAUSE always snaps to the exact position even for a tiny drift', () => {
    act(() => {
      ref.current!.syncTo({ positionSeconds: 10.2, playing: false, playbackRate: 1, action: 'PAUSE' });
    });
    expect(video.currentTime).toBe(10.2);
  });

  it('SEEK always snaps to the exact position even for a tiny drift', () => {
    act(() => {
      ref.current!.syncTo({ positionSeconds: 10.05, playing: false, playbackRate: 1, action: 'SEEK' });
    });
    expect(video.currentTime).toBe(10.05);
  });

  it('PLAY corrects drift past its small threshold but ignores a negligible one', () => {
    act(() => {
      ref.current!.syncTo({ positionSeconds: 10.1, playing: true, playbackRate: 1, action: 'PLAY' });
    });
    expect(video.currentTime).toBe(10); // 0.1s drift is below PLAY's 0.35s threshold

    act(() => {
      ref.current!.syncTo({ positionSeconds: 11, playing: true, playbackRate: 1, action: 'PLAY' });
    });
    expect(video.currentTime).toBe(11);
  });

  it('applies play/pause according to the target playing state', () => {
    act(() => {
      ref.current!.syncTo({ positionSeconds: 10, playing: true, playbackRate: 1, action: 'JOIN' });
    });
    expect(video.play).toHaveBeenCalledTimes(1);

    act(() => {
      ref.current!.syncTo({ positionSeconds: 10, playing: false, playbackRate: 1, action: 'PAUSE' });
    });
    expect(video.pause).toHaveBeenCalledTimes(1);
  });
});

describe('VideoPlayer restoreHostState suppression', () => {
  it('does not report a play/pause host action for the state change it causes itself', () => {
    const ref = React.createRef<VideoPlayerHandle>();
    const onPlaybackAction = vi.fn<(action: PlaybackActionPayload) => void>();
    const { container } = render(
      <VideoPlayer ref={ref} role="host" src={SRC} autoPlay={false} onPlaybackAction={onPlaybackAction} />,
    );
    const video = container.querySelector('video') as HTMLVideoElement;
    makeVideoControllable(video);

    act(() => {
      ref.current!.restoreHostState({ positionSeconds: 42, playing: true, playbackRate: 1 });
    });

    expect(video.currentTime).toBe(42);
    expect(video.play).toHaveBeenCalledTimes(1);
    expect(onPlaybackAction).not.toHaveBeenCalled();

    // A real, subsequent user-driven pause (not caused by restoreHostState) must still report
    // normally - the suppression flag is one-shot.
    act(() => {
      video.pause();
    });
    expect(onPlaybackAction).toHaveBeenCalledWith(
      expect.objectContaining({ type: 'pause' } satisfies Partial<PlaybackActionPayload>),
    );
  });

  it('does not leave the suppression flag armed when already in the target state', () => {
    const ref = React.createRef<VideoPlayerHandle>();
    const onPlaybackAction = vi.fn<(action: PlaybackActionPayload) => void>();
    const { container } = render(
      <VideoPlayer ref={ref} role="host" src={SRC} autoPlay={false} onPlaybackAction={onPlaybackAction} />,
    );
    const video = container.querySelector('video') as HTMLVideoElement;
    makeVideoControllable(video);

    // Already paused (jsdom default) and target is also paused - no native event will fire.
    act(() => {
      ref.current!.restoreHostState({ positionSeconds: 5, playing: false, playbackRate: 1 });
    });
    expect(video.pause).not.toHaveBeenCalled();

    // A real user-driven play right after must still report normally, proving the suppression
    // flag didn't leak into this unrelated action.
    act(() => {
      video.play();
    });
    expect(onPlaybackAction).toHaveBeenCalledWith(
      expect.objectContaining({ type: 'play' } satisfies Partial<PlaybackActionPayload>),
    );
  });
});

describe('VideoPlayer autoplay-blocked overlay', () => {
  it('shows a start-watching overlay when syncTo play() is blocked, and starting it clears the overlay', async () => {
    const ref = React.createRef<VideoPlayerHandle>();
    const { container } = render(
      <VideoPlayer ref={ref} role="viewer" src={SRC} autoPlay={false} />,
    );
    const video = container.querySelector('video') as HTMLVideoElement;
    let paused = true;
    Object.defineProperty(video, 'paused', { configurable: true, get: () => paused });
    video.play = vi.fn()
      .mockRejectedValueOnce(new DOMException('blocked', 'NotAllowedError'))
      .mockImplementationOnce(() => {
        paused = false;
        return Promise.resolve();
      });
    video.pause = vi.fn(() => { paused = true; });

    await act(async () => {
      ref.current!.syncTo({ positionSeconds: 0, playing: true, playbackRate: 1, action: 'JOIN' });
      await Promise.resolve().then(() => Promise.resolve());
    });

    expect(await screen.findByText(/(Start watching|Bắt đầu xem)/)).toBeInTheDocument();

    await act(async () => {
      fireEvent.click(screen.getByText(/(Start watching|Bắt đầu xem)/));
      await Promise.resolve();
    });

    expect(video.play).toHaveBeenCalledTimes(2);
    expect(screen.queryByText(/(Start watching|Bắt đầu xem)/)).not.toBeInTheDocument();
  });
});
