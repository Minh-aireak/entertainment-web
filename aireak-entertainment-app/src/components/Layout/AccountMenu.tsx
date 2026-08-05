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
import { Person, Logout as LogoutIcon } from '@mui/icons-material';

interface AccountMenuProps {
  displayName: string;
  email: string;
  avatar?: string;
  active?: boolean;
  onLogout: () => void;
}

const AccountMenu: React.FC<AccountMenuProps> = ({ displayName, email, avatar, active = false, onLogout }) => {
  const navigate = useNavigate();
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
          aria-label="Tài khoản"
          aria-haspopup="menu"
          aria-expanded={open}
          sx={{
            p: 0.5,
            ml: 0.5,
            border: '2px solid',
            borderColor: active || open ? 'primary.main' : 'transparent',
            transition: 'border-color 0.2s ease',
          }}
        >
          <Avatar src={avatar} sx={{ width: 34, height: 34, fontWeight: 700, fontSize: '0.95rem' }}>
            {!avatar && initial}
          </Avatar>
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
          Trang cá nhân
        </MenuItem>

        <Divider />

        <MenuItem onClick={handleLogoutClick} sx={{ py: 1.25, px: 2, color: 'error.main' }}>
          <ListItemIcon>
            <LogoutIcon fontSize="small" color="error" />
          </ListItemIcon>
          Đăng xuất
        </MenuItem>
      </Menu>
    </>
  );
};

export default AccountMenu;
