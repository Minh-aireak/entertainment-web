import { useState } from "react";
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
  InputBase,
  Button,
  Chip,
} from "@mui/material";
import {
  Search as SearchIcon,
  Notifications,
  Settings,
  Logout,
  MessageTwoTone,
  Home,
  TravelExplore,
  People,
  Schedule,
} from "@mui/icons-material";
import { logOut } from "../../features/hooks/useAuthApi";
import styles from "./Header.module.scss";

const navigationItems = [
  { label: "Home", icon: <Home />, path: "/" },
  { label: "Friends", icon: <People />, path: "/friends" },
  { label: "Schedules", icon: <Schedule />, path: "/schedules" },
];

export default function Header() {
  const navigate = useNavigate();
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
  const [searchValue, setSearchValue] = useState("");
  const open = Boolean(anchorEl);

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
    } else if (action === "settings") {
      navigate("/settings");
    } else if (action === "profile") {
      navigate("/profile");
    }
  };

  const handleSearch = (event: React.FormEvent) => {
    event.preventDefault();
    if (searchValue.trim()) {
      navigate(`/search?q=${encodeURIComponent(searchValue.trim())}`);
    }
  };

  return (
    <AppBar 
      position="sticky" 
      className={styles.header}
      sx={{
        background: "linear-gradient(135deg, #667eea 0%, #764ba2 100%)",
        boxShadow: "0 4px 20px rgba(102, 126, 234, 0.3)",
        backdropFilter: "blur(10px)",
      }}
    >
      <Toolbar sx={{ justifyContent: "space-between", px: { xs: 2, md: 4 } }}>
        {/* Logo Section */}
        <Box sx={{ display: "flex", alignItems: "center", gap: 2 }}>
          <Box
            component={Link}
            to="/"
            sx={{
              display: "flex",
              alignItems: "center",
              textDecoration: "none",
              color: "inherit",
            }}
          >
            <Box
              sx={{
                width: 40,
                height: 40,
                borderRadius: "50%",
                background: "linear-gradient(45deg, #ff6b6b, #4ecdc4)",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                mr: 2,
                boxShadow: "0 4px 15px rgba(255, 107, 107, 0.3)",
              }}
            >
              <TravelExplore sx={{ color: "white", fontSize: 24 }} />
            </Box>
            <Typography
              variant="h6"
              sx={{
                fontWeight: 700,
                background: "linear-gradient(45deg, #fff, #f0f0f0)",
                backgroundClip: "text",
                WebkitBackgroundClip: "text",
                WebkitTextFillColor: "transparent",
                fontSize: "1.5rem",
              }}
            >
              TravelPlanner
            </Typography>
          </Box>
        </Box>

        {/* Navigation Links */}
        <Box sx={{ display: { xs: "none", md: "flex" }, gap: 1 }}>
          {navigationItems.map((item) => (
            <Button
              key={item.label}
              component={Link}
              to={item.path}
              startIcon={item.icon}
              sx={{
                color: "rgba(255, 255, 255, 0.9)",
                textTransform: "none",
                fontWeight: 500,
                px: 2,
                py: 1,
                borderRadius: 2,
                transition: "all 0.3s ease",
                "&:hover": {
                  background: "rgba(255, 255, 255, 0.1)",
                  transform: "translateY(-1px)",
                  color: "white",
                },
              }}
            >
              {item.label}
            </Button>
          ))}
        </Box>

        {/* Search Bar */}
        <Box
          component="form"
          onSubmit={handleSearch}
          sx={{
            display: { xs: "none", sm: "flex" },
            alignItems: "center",
            background: "rgba(255, 255, 255, 0.15)",
            borderRadius: 3,
            px: 2,
            py: 0.5,
            backdropFilter: "blur(10px)",
            border: "1px solid rgba(255, 255, 255, 0.2)",
            transition: "all 0.3s ease",
            "&:hover": {
              background: "rgba(255, 255, 255, 0.2)",
            },
            "&:focus-within": {
              background: "rgba(255, 255, 255, 0.25)",
              border: "1px solid rgba(255, 255, 255, 0.4)",
            },
          }}
        >
          <SearchIcon sx={{ color: "rgba(255, 255, 255, 0.7)", mr: 1 }} />
          <InputBase
            placeholder="Search destinations..."
            value={searchValue}
            onChange={(e) => setSearchValue(e.target.value)}
            sx={{
              color: "white",
              "& ::placeholder": {
                color: "rgba(255, 255, 255, 0.7)",
                opacity: 1,
              },
              minWidth: 200,
            }}
          />
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
            <Badge badgeContent={5} color="error">
              <Notifications />
            </Badge>
          </IconButton>

          {/* User Menu */}
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
              src="https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcT4g49MMXD8rb1kFPrVEy7BQ0K3GQo8fZYkMQ&s"
              sx={{
                width: 36,
                height: 36,
                border: "2px solid rgba(255, 255, 255, 0.3)",
                boxShadow: "0 2px 10px rgba(0, 0, 0, 0.2)",
              }}
            />
          </IconButton>

          {/* Online Status */}
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
          
          <MenuItem
            onClick={() => handleMenuAccount("settings")}
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
            <Typography sx={{ fontWeight: 500 }}>Settings</Typography>
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