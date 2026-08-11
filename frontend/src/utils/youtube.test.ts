import { describe, expect, it } from 'vitest';
import { extractYouTubeVideoId } from './youtube';

describe('extractYouTubeVideoId', () => {
  const videoId = 'dQw4w9WgXcQ';

  it.each([
    videoId,
    `https://www.youtube.com/watch?v=${videoId}`,
    `https://m.youtube.com/watch?v=${videoId}&feature=share`,
    `https://music.youtube.com/watch?v=${videoId}`,
    `https://youtu.be/${videoId}?si=demo`,
    `https://www.youtube.com/embed/${videoId}`,
    `https://youtube.com/shorts/${videoId}`,
    `https://youtube.com/live/${videoId}`,
  ])('extracts the video ID from %s', (input) => {
    expect(extractYouTubeVideoId(input)).toBe(videoId);
  });

  it.each([
    null,
    undefined,
    '',
    'not-a-url',
    'too-short',
    'https://example.com/watch?v=dQw4w9WgXcQ',
    'https://youtube.example.com/watch?v=dQw4w9WgXcQ',
    'https://youtube.com/watch?v=invalid',
    'https://youtu.be/dQw4w9WgXcQextra',
  ])('returns null for unsupported input %s', (input) => {
    expect(extractYouTubeVideoId(input)).toBeNull();
  });

  it('trims a directly pasted video ID', () => {
    expect(extractYouTubeVideoId(`  ${videoId}  `)).toBe(videoId);
  });
});
