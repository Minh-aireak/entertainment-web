// Shared between PostComposer (picking) and PostCard (rendering) so a post always
// renders the same way it was composed, keyed by a stable code rather than raw CSS.
export interface PostBackgroundOption {
  key: string;
  gradient: string;
  textColor: string;
}

export const POST_BACKGROUNDS: PostBackgroundOption[] = [
  { key: 'sunset', gradient: 'linear-gradient(135deg, #ff6a6a 0%, #ffb347 100%)', textColor: '#fff' },
  { key: 'ocean', gradient: 'linear-gradient(135deg, #2193b0 0%, #6dd5ed 100%)', textColor: '#fff' },
  { key: 'candy', gradient: 'linear-gradient(135deg, #ee9ca7 0%, #ffdde1 100%)', textColor: '#4a1942' },
  { key: 'forest', gradient: 'linear-gradient(135deg, #134e5e 0%, #71b280 100%)', textColor: '#fff' },
  { key: 'grape', gradient: 'linear-gradient(135deg, #7f00ff 0%, #e100ff 100%)', textColor: '#fff' },
  { key: 'midnight', gradient: 'linear-gradient(135deg, #0f2027 0%, #203a43 50%, #2c5364 100%)', textColor: '#fff' },
  { key: 'gold', gradient: 'linear-gradient(135deg, #f7971e 0%, #ffd200 100%)', textColor: '#402a00' },
  { key: 'aurora', gradient: 'linear-gradient(135deg, #00c9ff 0%, #92fe9d 100%)', textColor: '#04372c' },
];

export const BACKGROUND_TEXT_LIMIT = 180;

export interface PostFeelingOption {
  key: string;
  emoji: string;
  label: string;
}

export const POST_FEELINGS: PostFeelingOption[] = [
  { key: 'HAPPY', emoji: '😄', label: 'vui vẻ' },
  { key: 'EXCITED', emoji: '🤩', label: 'phấn khích' },
  { key: 'LOVED', emoji: '🥰', label: 'yêu đời' },
  { key: 'SAD', emoji: '😢', label: 'buồn' },
  { key: 'TIRED', emoji: '😴', label: 'mệt mỏi' },
  { key: 'HYPE', emoji: '🔥', label: 'hype' },
  { key: 'WATCHING', emoji: '🎬', label: 'đang xem phim' },
  { key: 'SNACKING', emoji: '🍿', label: 'đang ăn vặt' },
  { key: 'CELEBRATING', emoji: '🥳', label: 'ăn mừng' },
  { key: 'CHILL', emoji: '🧊', label: 'thư giãn' },
];

export const findBackground = (key?: string | null): PostBackgroundOption | undefined =>
  key ? POST_BACKGROUNDS.find((b) => b.key === key) : undefined;

export const findFeeling = (key?: string | null): PostFeelingOption | undefined =>
  key ? POST_FEELINGS.find((f) => f.key === key) : undefined;
