import { useState, useEffect } from "react";
import { useNavigate, Link } from "react-router-dom";
import {
  AppBar,
  Toolbar,
  Box,
  Typography,
  IconButton,
  Avatar,
  Menu,
  MenuItem,
  ListItemIcon,
  Divider,
  Badge,
  Chip,
} from "@mui/material";
import {
  Settings,
  Logout,
  MessageTwoTone,
  TravelExplore,
} from "@mui/icons-material";
import { logOut } from "../../services/Authenticate";
import styles from "./Header.module.scss";
import { getMyInfo } from "../../services/UserService";
import NotificationBell from "../NotificationBell/NotificationBell";

export default function Header() {
  const navigate = useNavigate();
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
  const open = Boolean(anchorEl);
  const [currentUserAvatar, setCurrentUserAvatar] = useState<string>("");

  const handleClick = (event: React.MouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget);
  };

  const handleClose = () => {
    setAnchorEl(null);
  };

  const handleMenuAccount = (action: string) => {
    handleClose();
    if (action === "logout") {
      logOut();
      navigate("/login");
    } else if (action === "profile") {
      navigate("/profile");
    }
  };

  useEffect(() => {
    getMyInfo()
      .then((profile) => {
        setCurrentUserAvatar(profile.result?.avatar || "");
      })
      .catch(() => {});
  }, []);

  return (
    <AppBar position="sticky" className={styles.header}>
      <Toolbar sx={{ justifyContent: "space-between", px: { xs: 2, md: 4 } }}>
        <Box sx={{ display: "flex", alignItems: "center", gap: 2 }}>
          <Box component={Link} to="/">
            <Box>
              <TravelExplore />
            </Box>
            <Typography variant="h6">TravelPlanner</Typography>
          </Box>
        </Box>

        {/* Right Section */}
        <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
          {/* Messages */}
          <IconButton
            sx={{
              color: "rgba(255, 255, 255, 0.9)",
              transition: "all 0.3s ease",
              "&:hover": {
                background: "rgba(255, 255, 255, 0.1)",
                transform: "scale(1.1)",
              },
            }}
          >
            <Badge badgeContent={3} color="error">
              <MessageTwoTone />
            </Badge>
          </IconButton>

          {/* Notifications */}
          <NotificationBell />

          <IconButton
            onClick={handleClick}
            sx={{
              ml: 1,
              transition: "all 0.3s ease",
              "&:hover": {
                transform: "scale(1.05)",
              },
            }}
          >
            <Avatar
              src={currentUserAvatar}
              sx={{
                width: 36,
                height: 36,
                border: "2px solid rgba(255, 255, 255, 0.3)",
                boxShadow: "0 2px 10px rgba(0, 0, 0, 0.2)",
              }}
            />
          </IconButton>

          <Chip
            label="Online"
            size="small"
            sx={{
              background: "rgba(76, 175, 80, 0.2)",
              color: "#4caf50",
              border: "1px solid rgba(76, 175, 80, 0.3)",
              fontWeight: 600,
              ml: 1,
            }}
          />
        </Box>

        {/* User Menu Dropdown */}
        <Menu
          anchorEl={anchorEl}
          open={open}
          onClose={handleClose}
          transformOrigin={{ horizontal: "right", vertical: "top" }}
          anchorOrigin={{ horizontal: "right", vertical: "bottom" }}
          slotProps={{
            paper: {
              sx: {
                mt: 1,
                minWidth: 220,
                borderRadius: 2,
                background: "rgba(255, 255, 255, 0.95)",
                backdropFilter: "blur(20px)",
                border: "1px solid rgba(255, 255, 255, 0.2)",
                boxShadow: "0 8px 32px rgba(0, 0, 0, 0.1)",
              },
            },
          }}
        >
          <MenuItem
            onClick={() => handleMenuAccount("profile")}
            sx={{
              py: 1.5,
              px: 2,
              borderRadius: 1,
              mx: 1,
              mb: 0.5,
              "&:hover": {
                background: "rgba(102, 126, 234, 0.1)",
              },
            }}
          >
            <ListItemIcon>
              <Avatar sx={{ width: 24, height: 24 }}>
                <Settings sx={{ fontSize: 16 }} />
              </Avatar>
            </ListItemIcon>
            <Typography sx={{ fontWeight: 500 }}>Profile</Typography>
          </MenuItem>

          <Divider sx={{ my: 1 }} />

          <MenuItem
            onClick={() => handleMenuAccount("logout")}
            sx={{
              py: 1.5,
              px: 2,
              borderRadius: 1,
              mx: 1,
              color: "#f44336",
              "&:hover": {
                background: "rgba(244, 67, 54, 0.1)",
              },
            }}
          >
            <ListItemIcon>
              <Avatar sx={{ width: 24, height: 24, bgcolor: "#f44336" }}>
                <Logout sx={{ fontSize: 16, color: "white" }} />
              </Avatar>
            </ListItemIcon>
            <Typography sx={{ fontWeight: 500 }}>Logout</Typography>
          </MenuItem>
        </Menu>
      </Toolbar>
    </AppBar>
  );
}
