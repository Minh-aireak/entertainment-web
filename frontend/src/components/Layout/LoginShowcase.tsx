import React, { useLayoutEffect, useRef, useState } from 'react';
import { Box, Typography, alpha } from '@mui/material';
import { TrendingUp, PlayArrow } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';

const ACCENT_GREEN = '#00C75C';

// The three showcase cards orbit a shared, fixed center point (the middle of
// the left panel) on the same ellipse, evenly phase-offset by 1/3 of the
// period each — so at any instant they stay spread around the center instead
// of drifting toward one corner. The ellipse's radius is measured from the
// actual rendered title/subtitle block (not a hardcoded guess), so the path
// always clears the text regardless of locale, font wrapping, zoom, or
// viewport size — and is clamped to the panel's own bounds so it never
// overflows on a narrow window.
const CARD_HALF_W = 66; // half the widest card (Reels, 128px) plus a couple px
const CARD_HALF_H = 60; // half the tallest card's rendered height plus a couple px
const TEXT_GAP = 24; // breathing room between the text block and a card's edge
const CORNER_SAFETY = 1.45; // keeps the ellipse clear of the text block's corners, not just its edges
const EDGE_PAD = 12; // keeps the ellipse inside the panel instead of clipping at its border

const ORBIT_DURATION_S = 26;
const ORBIT_STEPS = 24;

const buildOrbitKeyframes = (rotateDeg: number, radiusX: number, radiusY: number) => {
  const frames: Record<string, { transform: string }> = {};
  for (let i = 0; i <= ORBIT_STEPS; i += 1) {
    const angle = (i / ORBIT_STEPS) * Math.PI * 2;
    const x = Math.cos(angle) * radiusX;
    const y = Math.sin(angle) * radiusY;
    const pct = (i / ORBIT_STEPS) * 100;
    frames[`${pct}%`] = {
      transform: `translate(-50%, -50%) translate(${x.toFixed(1)}px, ${y.toFixed(1)}px) rotate(${rotateDeg}deg)`,
    };
  }
  return frames;
};

const orbitCardSx = (
  keyframeName: string,
  rotateDeg: number,
  phaseIndex: number,
  radiusX: number,
  radiusY: number,
) => ({
  position: 'absolute' as const,
  top: '50%',
  left: '50%',
  animation: `${keyframeName} ${ORBIT_DURATION_S}s linear infinite`,
  animationDelay: `-${(ORBIT_DURATION_S / 3) * phaseIndex}s`,
  [`@keyframes ${keyframeName}`]: buildOrbitKeyframes(rotateDeg, radiusX, radiusY),
});

const glassCardSx = {
  position: 'absolute' as const,
  borderRadius: 3,
  border: '1px solid',
  borderColor: alpha('#fff', 0.1),
  bgcolor: alpha('#fff', 0.05),
  backdropFilter: 'blur(20px)',
  boxShadow: '0 24px 48px rgba(0,0,0,0.5)',
  p: 2,
  color: '#fff',
};

const AudioBars: React.FC = () => {
  const heights = [10, 22, 14, 26, 8, 18];
  return (
    <Box sx={{ display: 'flex', alignItems: 'flex-end', gap: 0.6, height: 28 }}>
      {heights.map((h, i) => (
        <Box
          key={i}
          sx={{
            width: 4,
            height: h,
            borderRadius: 2,
            bgcolor: ACCENT_GREEN,
            opacity: 0.55 + (i % 3) * 0.15,
          }}
        />
      ))}
    </Box>
  );
};

const LoginShowcase: React.FC = () => {
  const { t } = useTranslation();
  const containerRef = useRef<HTMLDivElement>(null);
  const textRef = useRef<HTMLDivElement>(null);
  const [radius, setRadius] = useState({ x: 240, y: 150 });

  useLayoutEffect(() => {
    const textEl = textRef.current;
    const containerEl = containerRef.current;
    if (!textEl || !containerEl) return;

    const recompute = () => {
      const textRect = textEl.getBoundingClientRect();
      const containerRect = containerEl.getBoundingClientRect();

      const wantedX = (textRect.width / 2 + CARD_HALF_W + TEXT_GAP) * CORNER_SAFETY;
      const wantedY = (textRect.height / 2 + CARD_HALF_H + TEXT_GAP) * CORNER_SAFETY;

      const maxX = containerRect.width / 2 - CARD_HALF_W - EDGE_PAD;
      const maxY = containerRect.height / 2 - CARD_HALF_H - EDGE_PAD;

      setRadius({
        x: Math.max(0, Math.min(wantedX, maxX)),
        y: Math.max(0, Math.min(wantedY, maxY)),
      });
    };

    recompute();
    const observer = new ResizeObserver(recompute);
    observer.observe(textEl);
    observer.observe(containerEl);
    return () => observer.disconnect();
  }, []);

  return (
    <Box
      ref={containerRef}
      sx={{
        position: 'relative',
        flex: 1,
        display: { xs: 'none', md: 'flex' },
        alignItems: 'center',
        justifyContent: 'center',
        overflow: 'hidden',
      }}
    >
      <Box
        sx={{
          position: 'absolute',
          width: 340,
          height: 340,
          borderRadius: '50%',
          bgcolor: alpha(ACCENT_GREEN, 0.22),
          filter: 'blur(120px)',
          top: '6%',
          left: '8%',
          pointerEvents: 'none',
        }}
      />
      <Box
        sx={{
          position: 'absolute',
          width: 300,
          height: 300,
          borderRadius: '50%',
          bgcolor: alpha(ACCENT_GREEN, 0.16),
          filter: 'blur(140px)',
          bottom: '4%',
          right: '6%',
          pointerEvents: 'none',
        }}
      />

      <Box ref={textRef} sx={{ position: 'relative', zIndex: 1, textAlign: 'center', px: 6, maxWidth: 420 }}>
        <Typography variant="h4" sx={{ fontWeight: 900, color: '#fff', mb: 2, letterSpacing: '-0.02em' }}>
          {t('authShowcaseTitle')}
        </Typography>
        <Typography sx={{ color: alpha('#fff', 0.65), fontSize: '1rem', lineHeight: 1.7 }}>
          {t('authShowcaseSubtitle')}
        </Typography>
      </Box>

      {/* Growth card */}
      <Box
        sx={{
          ...glassCardSx,
          ...orbitCardSx('authShowcaseOrbitGrowth', -4, 0, radius.x, radius.y),
          width: 110,
        }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mb: 1 }}>
          <TrendingUp sx={{ fontSize: 16, color: ACCENT_GREEN }} />
          <Typography sx={{ fontSize: '0.62rem', fontWeight: 700, letterSpacing: '0.1em', color: alpha('#fff', 0.6) }}>
            {t('authShowcaseGrowthLabel')}
          </Typography>
        </Box>
        <svg width="100%" height="32" viewBox="0 0 90 32" fill="none">
          <polyline
            points="0,26 15,20 30,23 45,12 60,15 75,4 90,7"
            stroke={ACCENT_GREEN}
            strokeWidth="2.5"
            strokeLinecap="round"
            strokeLinejoin="round"
            fill="none"
          />
        </svg>
        <Typography sx={{ fontSize: '0.85rem', fontWeight: 800, color: ACCENT_GREEN, mt: 0.5 }}>
          +38%
        </Typography>
      </Box>

      {/* Audio card */}
      <Box
        sx={{
          ...glassCardSx,
          ...orbitCardSx('authShowcaseOrbitAudio', 3, 1, radius.x, radius.y),
          width: 100,
        }}
      >
        <Typography sx={{ fontSize: '0.62rem', fontWeight: 700, letterSpacing: '0.1em', color: alpha('#fff', 0.6), mb: 1 }}>
          {t('authShowcaseAudioLabel')}
        </Typography>
        <AudioBars />
        <Typography sx={{ fontSize: '0.75rem', fontWeight: 700, color: alpha('#fff', 0.85), mt: 1 }}>
          0:42
        </Typography>
      </Box>

      {/* Reels card */}
      <Box
        sx={{
          ...glassCardSx,
          ...orbitCardSx('authShowcaseOrbitReels', -2, 2, radius.x, radius.y),
          width: 128,
          textAlign: 'center',
        }}
      >
        <Typography sx={{ fontSize: '0.62rem', fontWeight: 700, letterSpacing: '0.1em', color: alpha('#fff', 0.6), mb: 1, textAlign: 'left' }}>
          {t('authShowcaseReelsLabel')}
        </Typography>
        <Box
          sx={{
            width: 44,
            height: 44,
            borderRadius: '50%',
            border: '2px solid',
            borderColor: ACCENT_GREEN,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            mx: 'auto',
          }}
        >
          <PlayArrow sx={{ color: ACCENT_GREEN, fontSize: 22 }} />
        </Box>
        <Typography sx={{ fontSize: '0.75rem', fontWeight: 700, color: ACCENT_GREEN, mt: 1 }}>
          24K {t('authShowcaseViewsLabel')}
        </Typography>
      </Box>
    </Box>
  );
};

export default LoginShowcase;
