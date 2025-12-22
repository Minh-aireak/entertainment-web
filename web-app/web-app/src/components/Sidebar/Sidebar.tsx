import { useState, useEffect } from "react";
import { Link, useLocation } from "react-router-dom";
import {
  Box,
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Divider,
  Typography,
  Avatar,
  Chip,
  Collapse,
  Badge,
} from "@mui/material";
import {
  PeopleTwoTone as FriendsIcon,
  Groups3Rounded as GroupsIcon,
  MessageTwoTone as MessagesIcon,
  ScheduleTwoTone as SchedulesIcon,
  AccountCircle as ProfileIcon,
  ExpandLess,
  ExpandMore,
} from "@mui/icons-material";
import { getMyInfo } from "../../services/UserService";
import { countScheduleByStatus } from "../../services/ScheduleService";
import axios from "axios";

const secondaryMenuItems = [
  {
    text: "Profile",
    icon: <ProfileIcon />,
    path: "/profile",
    badge: null,
  },
];

const Sidebar = () => {
  const location = useLocation();
  const [selectedItem, setSelectedItem] = useState("");
  const [showSecondary, setShowSecondary] = useState(true);
  const [scheduleStats, setScheduleStats] = useState({
    active: 0,
    upcoming: 0,
  });
  const [userAvatar, setUserAvatar] = useState<string>("");
  const [userDisplayName, setUserDisplayName] = useState<string>("");
  const [snackbarOpen, setSnackbarOpen] = useState(false);
  const [snackbarMessage, setSnackbarMessage] = useState("");
  const [severity, setSeverity] = useState(true);

  useEffect(() => {
    setSelectedItem(location.pathname);
  }, [location.pathname]);

  useEffect(() => {
    getMyInfo()
      .then((profile) => {
        setUserAvatar(profile.result?.avatar || "");
        setUserDisplayName(profile.result?.displayName || "");
      })
      .catch(() => {});
  }, []);

  useEffect(() => {
    const fetchScheduleStats = async () => {
      try {
        const response = await countScheduleByStatus();
        const data = {
          active: response.data?.result?.quantityOnGoing || 0,
          upcoming: response.data?.result?.quantityUpComing || 0,
        };

        setScheduleStats(data);
      } catch (error: any) {
        let messageToShow = error.message;
        if (axios.isAxiosError(error)) {
          if (error.response) {
            messageToShow = error.response.data.message;
          }
        }
        setSnackbarMessage(messageToShow);
        setSeverity(false);
        setScheduleStats({ active: 0, upcoming: 0 });
      }
    };

    fetchScheduleStats();
  }, []);

  const getScheduleBadgeText = () => {
    const { active, upcoming } = scheduleStats;
    if (active > 0 && upcoming > 0) {
      return `${active} Active + ${upcoming} Upcoming`;
    } else if (active > 0) {
      return `${active} Active`;
    } else if (upcoming > 0) {
      return `${upcoming} Upcoming`;
    } else {
      return "No Schedules";
    }
  };

  const mainMenuItems = [
    {
      text: "My Schedules",
      icon: <SchedulesIcon />,
      path: "/schedules",
      badge: getScheduleBadgeText(),
    },
    {
      text: "Friends",
      icon: <FriendsIcon />,
      path: "/friends",
      badge: 12,
    },
    {
      text: "Groups",
      icon: <GroupsIcon />,
      path: "/groups",
      badge: 5,
    },
    {
      text: "Messages",
      icon: <MessagesIcon />,
      path: "/messages",
      badge: 8,
    },
  ];

  const renderMenuItem = (item: any, index: number) => (
    <ListItem key={index} disablePadding sx={{ mb: 0.5 }}>
      <ListItemButton
        component={Link}
        to={item.path}
        selected={selectedItem === item.path}
        sx={{
          borderRadius: 2,
          mx: 1,
          transition: "all 0.3s cubic-bezier(0.4, 0, 0.2, 1)",
          position: "relative",
          overflow: "hidden",
          "&:hover": {
            background:
              "linear-gradient(135deg, rgba(102, 126, 234, 0.1) 0%, rgba(118, 75, 162, 0.1) 100%)",
            transform: "translateX(8px)",
            boxShadow: "0 4px 20px rgba(102, 126, 234, 0.2)",
          },
          "&.Mui-selected": {
            background:
              "linear-gradient(135deg, rgba(102, 126, 234, 0.15) 0%, rgba(118, 75, 162, 0.15) 100%)",
            color: "#667eea",
            "&::before": {
              content: '""',
              position: "absolute",
              left: 0,
              top: 0,
              bottom: 0,
              width: 4,
              background: "linear-gradient(135deg, #667eea 0%, #764ba2 100%)",
              borderRadius: "0 4px 4px 0",
            },
            "& .MuiListItemIcon-root": {
              color: "#667eea",
            },
          },
          "&::after": {
            content: '""',
            position: "absolute",
            top: 0,
            left: "-100%",
            width: "100%",
            height: "100%",
            background:
              "linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.1), transparent)",
            transition: "left 0.5s ease",
          },
          "&:hover::after": {
            left: "100%",
          },
        }}
        onClick={() => setSelectedItem(item.path)}
      >
        <ListItemIcon
          sx={{
            minWidth: 48,
            color: "text.secondary",
            transition: "all 0.3s ease",
          }}
        >
          {item.badge && typeof item.badge === "number" ? (
            <Badge badgeContent={item.badge} color="error">
              {item.icon}
            </Badge>
          ) : (
            item.icon
          )}
        </ListItemIcon>
        <ListItemText
          primary={item.text}
          sx={{
            "& .MuiTypography-root": {
              fontWeight: selectedItem === item.path ? 600 : 500,
              fontSize: "0.95rem",
            },
          }}
        />
        {item.badge && typeof item.badge === "string" && (
          <Chip
            label={item.badge}
            size="small"
            sx={{
              height: item.text === "My Schedules" ? 24 : 20,
              fontSize: item.text === "My Schedules" ? "0.6rem" : "0.7rem",
              background:
                item.text === "My Schedules"
                  ? "linear-gradient(45deg, #667eea, #764ba2)"
                  : item.badge === "New"
                  ? "linear-gradient(45deg, #ff6b6b, #4ecdc4)"
                  : "linear-gradient(45deg, #ff6b6b, #4ecdc4)",
              color: "white",
              fontWeight: 600,
              maxWidth: item.text === "My Schedules" ? 120 : "auto",
              "& .MuiChip-label": {
                padding: item.text === "My Schedules" ? "0 6px" : "0 8px",
                whiteSpace: "nowrap",
                overflow: "hidden",
                textOverflow: "ellipsis",
              },
            }}
          />
        )}
      </ListItemButton>
    </ListItem>
  );

  return (
    <Box
      sx={{
        width: 280,
        minHeight: "calc(100vh - 64px)",
        background: "linear-gradient(180deg, #f8f9ff 0%, #ffffff 100%)",
        borderRight: "1px solid rgba(102, 126, 234, 0.1)",
        position: "sticky",
        top: 64,
        overflowY: "auto",
        boxShadow: "4px 0 20px rgba(0, 0, 0, 0.05)",
        "&::-webkit-scrollbar": {
          width: 6,
        },
        "&::-webkit-scrollbar-track": {
          background: "transparent",
        },
        "&::-webkit-scrollbar-thumb": {
          background: "rgba(102, 126, 234, 0.2)",
          borderRadius: 3,
        },
      }}
    >
      <Box
        sx={{
          p: 3,
          background:
            "linear-gradient(135deg, rgba(102, 126, 234, 0.05) 0%, rgba(118, 75, 162, 0.05) 100%)",
          borderBottom: "1px solid rgba(102, 126, 234, 0.1)",
        }}
      >
        <Box sx={{ display: "flex", alignItems: "center", mb: 2 }}>
          <Avatar
            src={userAvatar}
            sx={{
              width: 48,
              height: 48,
              mr: 2,
              border: "3px solid rgba(102, 126, 234, 0.2)",
              boxShadow: "0 4px 15px rgba(102, 126, 234, 0.2)",
            }}
          />
          <Box>
            <Typography
              variant="subtitle1"
              sx={{
                fontWeight: 600,
                color: "text.primary",
                lineHeight: 1.2,
              }}
            >
              {userDisplayName || ""}
            </Typography>
            <Typography
              variant="caption"
              sx={{
                color: "text.secondary",
                display: "flex",
                alignItems: "center",
                gap: 0.5,
              }}
            >
              <Box
                sx={{
                  width: 8,
                  height: 8,
                  borderRadius: "50%",
                  background: "#4caf50",
                  animation: "pulse 2s infinite",
                }}
              />
              Online
            </Typography>
          </Box>
        </Box>
      </Box>

      <Box sx={{ py: 2 }}>
        <Typography
          variant="overline"
          sx={{
            px: 2,
            color: "text.secondary",
            fontWeight: 600,
            fontSize: "0.75rem",
            letterSpacing: 1,
          }}
        >
          Main Menu
        </Typography>
        <List sx={{ px: 1, mt: 1 }}>
          {mainMenuItems.map((item, index) => renderMenuItem(item, index))}
        </List>
      </Box>

      <Divider sx={{ mx: 2, my: 1 }} />

      {/* Secondary Navigation */}
      <Box sx={{ py: 1 }}>
        <ListItemButton
          onClick={() => setShowSecondary(!showSecondary)}
          sx={{
            mx: 2,
            borderRadius: 2,
            "&:hover": {
              background: "rgba(102, 126, 234, 0.05)",
            },
          }}
        >
          <ListItemIcon>
            <ProfileIcon sx={{ color: "text.secondary" }} />
          </ListItemIcon>
          <ListItemText
            primary="More"
            sx={{
              "& .MuiTypography-root": {
                fontSize: "0.9rem",
                fontWeight: 500,
                color: "text.secondary",
              },
            }}
          />
          {showSecondary ? <ExpandLess /> : <ExpandMore />}
        </ListItemButton>

        <Collapse in={showSecondary} timeout="auto" unmountOnExit>
          <List sx={{ px: 1, mt: 1 }}>
            {secondaryMenuItems.map((item, index) =>
              renderMenuItem(item, index)
            )}
          </List>
        </Collapse>
      </Box>

      <style>
        {`
          @keyframes pulse {
            0% { opacity: 1; }
            50% { opacity: 0.5; }
            100% { opacity: 1; }
          }
        `}
      </style>
    </Box>
  );
};

export default Sidebar;
