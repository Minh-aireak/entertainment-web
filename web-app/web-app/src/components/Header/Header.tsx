import { useState } from "react";
import classNames from "classnames/bind";
import styles from "./Header.module.scss";
import { logOut } from "../../features/hooks/useAuthApi";
import {
  Avatar,
  IconButton,
  Menu,
  MenuItem,
  ListItemIcon,
  Divider,
  Tooltip,
} from "@mui/material";
import {
  Settings,
  Logout,
  Notifications,
  MessageTwoTone,
} from "@mui/icons-material";
import { useNavigate } from "react-router-dom";

const cx = classNames.bind(styles);

export default function Header() {
  const navigate = useNavigate();
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
  const open = Boolean(anchorEl);

  const handleClick = (event: React.MouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget);
  };

  const handleClose = () => {
    setAnchorEl(null);
  };

  const handleMenuAccount = (action: string) => {
    handleClose();
    if (action == "logout") {
      logOut();
      navigate("/login");
    } else if (action === "setting") {
      console.log("Setiing");
    }
  };

  return (
    <header className={cx("wrapper")}>
      <Tooltip
        title="Messages"
        slotProps={{
          popper: {
            modifiers: [
              {
                name: "offset",
                options: {
                  offset: [0, -10],
                },
              },
            ],
          },
          tooltip: {
            sx: {
              fontSize: "1.1rem",
              color: "white",
              borderRadius: "10px",
              padding: "10px",
              bgcolor: "black",
            },
          },
        }}
      >
        <IconButton>
          <Avatar
            sx={{
              color: "black",
              width: 30,
              height: 30,
            }}
          >
            <MessageTwoTone sx={{ strokeWidth: 1, fontSize: 20 }} />
          </Avatar>
        </IconButton>
      </Tooltip>
      <Tooltip
        title="Notifications"
        slotProps={{
          popper: {
            modifiers: [
              {
                name: "offset",
                options: {
                  offset: [0, -10],
                },
              },
            ],
          },
          tooltip: {
            sx: {
              fontSize: "1.1rem",
              color: "white",
              borderRadius: "10px",
              padding: "10px",
              bgcolor: "black",
            },
          },
        }}
      >
        <IconButton>
          <Avatar
            sx={{
              color: "black",
              width: 30,
              height: 30,
            }}
          >
            <Notifications sx={{ strokeWidth: 1, fontSize: 20 }} />
          </Avatar>
        </IconButton>
      </Tooltip>
      <Tooltip
        title="Account"
        slotProps={{
          popper: {
            modifiers: [
              {
                name: "offset",
                options: {
                  offset: [0, -10],
                },
              },
            ],
          },
          tooltip: {
            sx: {
              fontSize: "1.1rem",
              color: "white",
              borderRadius: "10px",
              padding: "10px",
              bgcolor: "black",
            },
          },
        }}
      >
        <IconButton
          onClick={handleClick}
          size="small"
          aria-controls={open ? "user-menu" : undefined}
          aria-haspopup="true"
          aria-expanded={open ? "true" : undefined}
        >
          <Avatar
            src="https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcT4g49MMXD8rb1kFPrVEy7BQ0K3GQo8fZYkMQ&s"
            sx={{ width: 30, height: 30 }}
          />
        </IconButton>
      </Tooltip>
      <Menu
        anchorEl={anchorEl}
        id="user-menu"
        open={open}
        onClose={handleClose}
        slotProps={{
          paper: {
            elevation: 6,
            sx: {
              mt: 2,
              minWidth: 180,
            },
          },
        }}
        transformOrigin={{ horizontal: "right", vertical: "top" }}
        anchorOrigin={{ horizontal: "right", vertical: "bottom" }}
      >
        <MenuItem
          onClick={() => handleMenuAccount("setting")}
          style={{ gap: "10px", fontSize: "1.2rem", fontWeight: 600 }}
        >
          <ListItemIcon>
            <Avatar
              sx={{
                color: "black",
                width: 30,
                height: 30,
              }}
            >
              <Settings sx={{ strokeWidth: 1, fontSize: 20 }} />
            </Avatar>
          </ListItemIcon>
          Settings
        </MenuItem>
        <Divider />
        <MenuItem
          onClick={() => handleMenuAccount("logout")}
          style={{ gap: "10px", fontSize: "1.2rem", fontWeight: 600 }}
        >
          <ListItemIcon>
            <Avatar
              sx={{
                color: "black",
                width: 30,
                height: 30,
              }}
            >
              <Logout sx={{ strokeWidth: 1, fontSize: 20 }} />
            </Avatar>
          </ListItemIcon>
          Logout
        </MenuItem>
      </Menu>
    </header>
  );
}