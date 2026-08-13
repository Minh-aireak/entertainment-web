import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Avatar,
  Box,
  Divider,
  IconButton,
  ListItemIcon,
  MenuItem,
  Menu,
  Tooltip,
  Typography,
} from '@mui/material';
import { Person, Logout as LogoutIcon, ExpandMore } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';

interface AccountMenuProps {
  displayName: string;
  email: string;
  avatar?: string;
  active?: boolean;
  onLogout: () => void;
}

const AccountMenu: React.FC<AccountMenuProps> = ({ displayName, email, avatar, active = false, onLogout }) => {
  const navigate = useNavigate();
  const { t } = useTranslation();
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
  const open = Boolean(anchorEl);
  const initial = displayName.charAt(0).toUpperCase();

  const handleToggle = (event: React.MouseEvent<HTMLElement>) => {
    setAnchorEl((currentAnchor) => (currentAnchor ? null : event.currentTarget));
  };

  const handleClose = () => setAnchorEl(null);

  const handleViewProfile = () => {
    handleClose();
    navigate('/social/profile');
  };

  const handleLogoutClick = () => {
    handleClose();
    onLogout();
  };

  return (
    <>
      <Tooltip title={displayName}>
        <IconButton
          onClick={handleToggle}
          aria-label={t('account')}
          aria-haspopup="menu"
          aria-expanded={open}
          sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 0.5,
            height: 44,
            pl: 0.5,
            pr: 1,
            ml: 0.5,
            borderRadius: '999px',
            bgcolor: active || open ? 'primary.50' : (theme) =>
              theme.palette.mode === 'dark' ? 'rgba(255,255,255,0.05)' : 'rgba(0,0,0,0.03)',
            border: '1px solid',
            borderColor: active || open ? 'primary.main' : 'divider',
            transition: 'all 0.2s ease',
            '&:hover': {
              backgroundColor: 'primary.50',
              borderColor: 'primary.main',
            },
          }}
        >
          <Avatar src={avatar} sx={{ width: 34, height: 34, fontWeight: 700, fontSize: '0.95rem' }}>
            {!avatar && initial}
          </Avatar>
          <ExpandMore
            fontSize="small"
            sx={{
              color: active || open ? 'primary.main' : 'text.secondary',
              transition: 'transform 0.2s ease',
              transform: open ? 'rotate(180deg)' : 'none',
            }}
          />
        </IconButton>
      </Tooltip>

      <Menu
        open={open}
        anchorEl={anchorEl}
        onClose={handleClose}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        transformOrigin={{ vertical: 'top', horizontal: 'right' }}
        slotProps={{
          paper: {
            elevation: 12,
            sx: {
              mt: 1,
              width: 280,
              maxWidth: 'calc(100vw - 24px)',
              borderRadius: 3,
              overflow: 'hidden',
              border: '1px solid',
              borderColor: 'divider',
            },
          },
        }}
      >
        <Box sx={{ px: 2, py: 1.5, display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <Avatar src={avatar} sx={{ width: 44, height: 44, fontWeight: 700 }}>
            {!avatar && initial}
          </Avatar>
          <Box sx={{ minWidth: 0 }}>
            <Typography sx={{ fontWeight: 700, fontSize: '0.95rem', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
              {displayName}
            </Typography>
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
              {email}
            </Typography>
          </Box>
        </Box>

        <Divider />

        <MenuItem onClick={handleViewProfile} sx={{ py: 1.25, px: 2 }}>
          <ListItemIcon>
            <Person fontSize="small" />
          </ListItemIcon>
          {t('profile')}
        </MenuItem>

        <Divider />

        <MenuItem onClick={handleLogoutClick} sx={{ py: 1.25, px: 2, color: 'error.main' }}>
          <ListItemIcon>
            <LogoutIcon fontSize="small" color="error" />
          </ListItemIcon>
          {t('logout')}
        </MenuItem>
      </Menu>
    </>
  );
};

export default AccountMenu;
