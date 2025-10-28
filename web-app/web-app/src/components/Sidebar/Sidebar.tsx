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
  Settings,
  ExpandLess,
  ExpandMore,
  Notifications,
} from "@mui/icons-material";

const secondaryMenuItems = [
  {
    text: "Profile",
    icon: <ProfileIcon />,
    path: "/profile",
    badge: null,
  },
  {
    text: "Settings",
    icon: <Settings />,
    path: "/settings",
    badge: null,
  },
];

const Sidebar = () => {
  const location = useLocation();
  const [selectedItem, setSelectedItem] = useState("");
  const [isCollapsed, setIsCollapsed] = useState(false);
  const [showSecondary, setShowSecondary] = useState(true);
  const [scheduleStats, setScheduleStats] = useState({
    active: 2,
    upcoming: 3
  });

  useEffect(() => {
    setSelectedItem(location.pathname);
  }, [location.pathname]);

  // Simulate fetching schedule data
  useEffect(() => {
    const fetchScheduleStats = async () => {
      try {
        // TODO: Replace with actual API call
        // const response = await fetch('/api/schedules/stats');
        // const data = await response.json();
        
        // Mock data for demonstration
        const mockStats = {
          active: 2, // Schedules currently happening
          upcoming: 3 // Schedules starting soon
        };
        
        setScheduleStats(mockStats);
      } catch (error) {
        console.error('Error fetching schedule stats:', error);
        // Set default values on error
        setScheduleStats({ active: 0, upcoming: 0 });
      }
    };

    fetchScheduleStats();
    
    // Optional: Set up interval to refresh stats periodically
    const interval = setInterval(fetchScheduleStats, 60000); // Refresh every minute
    
    return () => clearInterval(interval);
  }, []);

  // Function to get schedule badge text
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
            background: "linear-gradient(135deg, rgba(102, 126, 234, 0.1) 0%, rgba(118, 75, 162, 0.1) 100%)",
            transform: "translateX(8px)",
            boxShadow: "0 4px 20px rgba(102, 126, 234, 0.2)",
          },
          "&.Mui-selected": {
            background: "linear-gradient(135deg, rgba(102, 126, 234, 0.15) 0%, rgba(118, 75, 162, 0.15) 100%)",
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
            background: "linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.1), transparent)",
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
              background: item.text === "My Schedules" 
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
      {/* User Profile Section */}
      <Box
        sx={{
          p: 3,
          background: "linear-gradient(135deg, rgba(102, 126, 234, 0.05) 0%, rgba(118, 75, 162, 0.05) 100%)",
          borderBottom: "1px solid rgba(102, 126, 234, 0.1)",
        }}
      >
        <Box sx={{ display: "flex", alignItems: "center", mb: 2 }}>
          <Avatar
            src="https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcT4g49MMXD8rb1kFPrVEy7BQ0K3GQo8fZYkMQ&s"
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
              John Doe
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
        
        {/* Quick Stats */}
        <Box sx={{ display: "flex", gap: 1 }}>
          <Chip
            icon={<SchedulesIcon sx={{ fontSize: 16 }} />}
            label="5 Trips"
            size="small"
            variant="outlined"
            sx={{ fontSize: "0.7rem" }}
          />
          <Chip
            icon={<FriendsIcon sx={{ fontSize: 16 }} />}
            label="12 Friends"
            size="small"
            variant="outlined"
            sx={{ fontSize: "0.7rem" }}
          />
        </Box>
      </Box>

      {/* Main Navigation */}
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
            <Settings sx={{ color: "text.secondary" }} />
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
            {secondaryMenuItems.map((item, index) => renderMenuItem(item, index))}
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
