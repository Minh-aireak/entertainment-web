import React from 'react';
import { IconButton } from '@mui/material';
import { alpha } from '@mui/material/styles';
import { Send } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { VIBE_GLOW, VIBE_GRADIENT, VIBE_GRADIENT_HOVER } from '../../styles/vibe';

interface GradientSendButtonProps {
  onClick: () => void;
  disabled?: boolean;
  size?: number;
}

/** Round gradient send button shared by every chat/comment composer - pops in when there is
 *  something to send, sits flat and grey otherwise. */
const GradientSendButton: React.FC<GradientSendButtonProps> = ({ onClick, disabled = false, size = 40 }) => {
  const { t } = useTranslation();
  return (
    <IconButton
      aria-label={t('sendLabel')}
      onClick={onClick}
      disabled={disabled}
      sx={{
        width: size,
        height: size,
        flexShrink: 0,
        color: '#fff',
        background: VIBE_GRADIENT,
        boxShadow: VIBE_GLOW,
        transition: 'transform 0.18s cubic-bezier(.2,.8,.2,1), box-shadow 0.2s ease, opacity 0.2s ease',
        '&:hover': { background: VIBE_GRADIENT_HOVER, transform: 'scale(1.07) rotate(-10deg)' },
        '&:active': { transform: 'scale(0.92)' },
        '&.Mui-disabled': {
          background: (theme) => alpha(theme.palette.text.primary, 0.08),
          color: 'text.disabled',
          boxShadow: 'none',
        },
      }}
    >
      <Send sx={{ fontSize: Math.round(size * 0.45), ml: '2px' }} />
    </IconButton>
  );
};

export default GradientSendButton;
