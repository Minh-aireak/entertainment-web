import React, { useEffect, useRef } from 'react';
import Hls from 'hls.js';

interface VideoPlayerProps {
  src: string;
  autoPlay?: boolean;
  controls?: boolean;
  style?: React.CSSProperties;
}

const VideoPlayer: React.FC<VideoPlayerProps> = ({ src, autoPlay = true, controls = true, style }) => {
  const videoRef = useRef<HTMLVideoElement>(null);

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
      // Non-HLS source (or Safari, which plays .m3u8 natively) — B2 serves the raw file directly.
      video.src = src;
    }

    return () => {
      hls?.destroy();
    };
  }, [src]);

  return <video ref={videoRef} controls={controls} autoPlay={autoPlay} style={style} />;
};

export default VideoPlayer;
