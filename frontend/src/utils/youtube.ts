const YOUTUBE_ID_PATTERN = /^[a-zA-Z0-9_-]{11}$/;

// Nhận nhiều định dạng: link watch?v=, youtu.be/, embed/, shorts/, live/, hoặc chỉ dán thẳng video ID
// (11 ký tự). Trả về null nếu không nhận diện được, để nơi gọi tự quyết định fallback (vd mở tab mới).
export function extractYouTubeVideoId(url: string | null | undefined): string | null {
  if (!url) return null;
  const trimmed = url.trim();
  if (YOUTUBE_ID_PATTERN.test(trimmed)) return trimmed;

  let parsed: URL;
  try {
    parsed = new URL(trimmed);
  } catch {
    return null;
  }

  const host = parsed.hostname.replace(/^www\./, '').replace(/^m\./, '');

  if (host === 'youtu.be') {
    const id = parsed.pathname.slice(1).split('/')[0];
    return YOUTUBE_ID_PATTERN.test(id) ? id : null;
  }

  if (host === 'youtube.com' || host === 'music.youtube.com') {
    if (parsed.pathname === '/watch') {
      const id = parsed.searchParams.get('v');
      return id && YOUTUBE_ID_PATTERN.test(id) ? id : null;
    }
    const match = parsed.pathname.match(/^\/(?:embed|shorts|live)\/([a-zA-Z0-9_-]{11})/);
    if (match) return match[1];
  }

  return null;
}
