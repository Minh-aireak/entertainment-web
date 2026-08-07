import React, { useRef, useState } from 'react';
import { Box, IconButton, Popover, TextField, Tooltip } from '@mui/material';
import { Send, InsertEmoticon } from '@mui/icons-material';
import { useSelector } from 'react-redux';
import { useTranslation } from 'react-i18next';
import EmojiPicker, { Theme, type EmojiClickData } from 'emoji-picker-react';
import { type RootState } from '../../store';

interface CommentComposerProps {
  value: string;
  onChange: (value: string) => void;
  onSubmit: () => void;
  placeholder: string;
  autoFocus?: boolean;
}

/** Shared TextField + emoji-picker + send button row, used for both the top-level compose box
 *  and each comment's reply box - keeps cursor-aware emoji insertion in one place. */
const CommentComposer: React.FC<CommentComposerProps> = ({ value, onChange, onSubmit, placeholder, autoFocus }) => {
  const { t } = useTranslation();
  const themeMode = useSelector((state: RootState) => state.ui.themeMode);
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
      <TextField
        fullWidth
        size="small"
        inputRef={inputRef}
        placeholder={placeholder}
        value={value}
        autoFocus={autoFocus}
        onChange={(e) => onChange(e.target.value)}
        onKeyDown={(e) => e.key === 'Enter' && onSubmit()}
      />
      <Tooltip title={t('emojiPickerLabel')}>
        <IconButton size="small" onClick={(e) => setEmojiAnchorEl(e.currentTarget)}>
          <InsertEmoticon fontSize="small" />
        </IconButton>
      </Tooltip>
      <IconButton size="small" color="primary" onClick={onSubmit} disabled={!value.trim()}>
        <Send fontSize="small" />
      </IconButton>
      <Popover
        open={Boolean(emojiAnchorEl)}
        anchorEl={emojiAnchorEl}
        onClose={() => setEmojiAnchorEl(null)}
        anchorOrigin={{ vertical: 'top', horizontal: 'left' }}
        transformOrigin={{ vertical: 'bottom', horizontal: 'left' }}
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
