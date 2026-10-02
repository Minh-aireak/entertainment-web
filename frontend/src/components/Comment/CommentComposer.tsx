import React, { useRef, useState } from 'react';
import { Avatar, Box, IconButton, InputBase, Popover, Tooltip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { InsertEmoticon } from '@mui/icons-material';
import { useSelector } from 'react-redux';
import { useTranslation } from 'react-i18next';
import EmojiPicker, { Theme, type EmojiClickData } from 'emoji-picker-react';
import { type RootState } from '../../store';
import GradientSendButton from '../common/GradientSendButton';
import { VIBE_GRADIENT, VIBE_TEAL, composerBarSx } from '../../styles/vibe';

interface CommentComposerProps {
  value: string;
  onChange: (value: string) => void;
  onSubmit: () => void;
  placeholder: string;
  autoFocus?: boolean;
}

/** Shared input + emoji-picker + send button row, used for both the top-level compose box
 *  and each comment's reply box - keeps cursor-aware emoji insertion in one place. */
const CommentComposer: React.FC<CommentComposerProps> = ({ value, onChange, onSubmit, placeholder, autoFocus }) => {
  const { t } = useTranslation();
  const theme = useTheme();
  const themeMode = useSelector((state: RootState) => state.ui.themeMode);
  const avatar = useSelector((state: RootState) => state.profile.profileData?.avatar);
  const [emojiAnchorEl, setEmojiAnchorEl] = useState<null | HTMLElement>(null);
  const inputRef = useRef<HTMLInputElement | null>(null);

  const handleEmojiClick = (emojiData: EmojiClickData) => {
    const emoji = emojiData.emoji;
    const input = inputRef.current;
    const start = input?.selectionStart ?? value.length;
    const end = input?.selectionEnd ?? value.length;

    onChange(value.slice(0, start) + emoji + value.slice(end));

    requestAnimationFrame(() => {
      if (!input) return;
      const cursor = start + emoji.length;
      input.focus();
      input.setSelectionRange(cursor, cursor);
    });
  };

  return (
    <Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
      <Box sx={{ p: '2px', borderRadius: '50%', background: VIBE_GRADIENT, flexShrink: 0 }}>
        <Avatar src={avatar} sx={{ width: 34, height: 34, border: '2px solid', borderColor: 'background.paper' }} />
      </Box>
      <Box sx={{ ...composerBarSx(theme), flex: 1 }}>
        <InputBase
          fullWidth
          inputRef={inputRef}
          placeholder={placeholder}
          value={value}
          autoFocus={autoFocus}
          onChange={(e) => onChange(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && onSubmit()}
          inputProps={{ 'aria-label': placeholder }}
          sx={{ fontSize: '0.92rem' }}
        />
        <Tooltip title={t('emojiPickerLabel')}>
          <IconButton
            size="small"
            onClick={(e) => setEmojiAnchorEl(e.currentTarget)}
            sx={{ color: 'text.secondary', '&:hover': { color: VIBE_TEAL } }}
          >
            <InsertEmoticon fontSize="small" />
          </IconButton>
        </Tooltip>
      </Box>
      <GradientSendButton onClick={onSubmit} disabled={!value.trim()} size={38} />
      <Popover
        open={Boolean(emojiAnchorEl)}
        anchorEl={emojiAnchorEl}
        onClose={() => setEmojiAnchorEl(null)}
        anchorOrigin={{ vertical: 'top', horizontal: 'left' }}
        transformOrigin={{ vertical: 'bottom', horizontal: 'left' }}
        slotProps={{ paper: { sx: { borderRadius: '18px', overflow: 'hidden' } } }}
      >
        <EmojiPicker
          onEmojiClick={handleEmojiClick}
          theme={themeMode === 'dark' ? Theme.DARK : Theme.LIGHT}
          lazyLoadEmojis
        />
      </Popover>
    </Box>
  );
};

export default CommentComposer;
