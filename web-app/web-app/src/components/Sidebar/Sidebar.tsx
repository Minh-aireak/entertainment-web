import { useState } from "react";
import { Link } from "react-router-dom";
import {
  Box,
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Divider,
  styled,
} from "@mui/material";
import {
  PeopleTwoTone as FriendsIcon,
  Groups3Rounded as GroupsIcon,
  MessageTwoTone as MessagesIcon,
  ScheduleTwoTone as SchedulesIcon,
  AccountCircle as ProfileIcon,
} from "@mui/icons-material";
import classNames from "classnames/bind";
import styles from "./Sidebar.module.scss";

const cx = classNames.bind(styles);

const sidebarItems = [
  {
    text: "Friends",
    icon: <FriendsIcon style={{ fontSize: 30 }} />,
    path: "/friends",
  },
  {
    text: "Groups",
    icon: <GroupsIcon className={cx("icon-size")} style={{ fontSize: 30 }} />,
    path: "/groups",
  },
  {
    text: "Messages",
    icon: <MessagesIcon className={cx("icon-size")} style={{ fontSize: 30 }} />,
    path: "/messages",
  },
  {
    text: "Schedules",
    icon: (
      <SchedulesIcon className={cx("icon-size")} style={{ fontSize: 30 }} />
    ),
    path: "/schedules",
  },
  {
    text: "Profile",
    icon: <ProfileIcon className={cx("icon-size")} style={{ fontSize: 30 }} />,
    path: "/profile",
  },
];

const SidebarContainer = styled(Box)(({ theme }) => ({
  width: 360,
  minHeight: "100vh",
  position: "sticky",
  top: 60,
  overflowY: "auto",
  "& .MuiListItemButton-root": {
    borderRadius: theme.shape.borderRadius,
    marginBottom: theme.spacing(0.5),
    "&:hover": {
      backgroundColor: theme.palette.action.hover,
    },
  },
}));

const Sidebar = () => {
  const [selectedItem, setSelectedItem] = useState(-1);

  return (
    <SidebarContainer>
      <List>
        {sidebarItems.map((item, index) => (
          <ListItem key={index} disablePadding>
            <ListItemButton
              component={Link}
              to={item.path}
              sx={{
                "&.Mui-selected": {
                  color: "red",
                  "& .MuiListItemIcon-root": {
                    color: "red",
                  },
                },
              }}
              selected={selectedItem === index}
              onClick={() => setSelectedItem(index)}
            >
              <ListItemIcon
                sx={{
                  minWidth: 50,
                  minHeight: 60,
                  color: "text.primary",
                  alignItems: "center",
                }}
              >
                {item.icon}
              </ListItemIcon>
              <ListItemText
                primary={item.text}
                slotProps={{
                  primary: {
                    fontSize: "1.2rem",
                    fontWeight: 600,
                  },
                }}
              />
            </ListItemButton>
          </ListItem>
        ))}
      </List>
      <Divider sx={{ my: 1 }} />
    </SidebarContainer>
  );
};

export default Sidebar;
