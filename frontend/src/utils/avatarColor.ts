// Deterministic gradient per user so avatars stay visually distinct across a feed full of
// strangers, while staying within the app's green-accent brand family.
const GRADIENTS = [
  'linear-gradient(135deg, #00A84E 0%, #00C853 100%)',
  'linear-gradient(135deg, #0091EA 0%, #00C6FF 100%)',
  'linear-gradient(135deg, #7C4DFF 0%, #B388FF 100%)',
  'linear-gradient(135deg, #FF6D00 0%, #FFAB40 100%)',
  'linear-gradient(135deg, #E91E63 0%, #FF80AB 100%)',
  'linear-gradient(135deg, #00BFA5 0%, #64FFDA 100%)',
  'linear-gradient(135deg, #F9A825 0%, #FFD54F 100%)',
  'linear-gradient(135deg, #5C6BC0 0%, #9FA8DA 100%)',
];

export function getAvatarGradient(seed?: string): string {
  if (!seed) return GRADIENTS[0];
  let hash = 0;
  for (let i = 0; i < seed.length; i += 1) {
    hash = (hash * 31 + seed.charCodeAt(i)) >>> 0;
  }
  return GRADIENTS[hash % GRADIENTS.length];
}
