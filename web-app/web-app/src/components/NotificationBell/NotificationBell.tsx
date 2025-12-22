import { useCallback, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Avatar,
  Badge,
  Box,
  Button,
  CircularProgress,
  Divider,
  IconButton,
  ListItemIcon,
  Menu,
  MenuItem,
  Typography,
} from "@mui/material";
import { Notifications } from "@mui/icons-material";
import { getMyNotifications } from "../../services/NotificationService";
import type {
  NotificationPageResponse,
  NotificationResponse,
} from "../../InterfaceDataType/DataType";

type NotificationBellProps = {
  pageSize?: number;
};

export default function NotificationBell({
  pageSize = 6,
}: NotificationBellProps) {
  const navigate = useNavigate();

  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
  const open = Boolean(anchorEl);

  const [notifications, setNotifications] = useState<NotificationResponse[]>(
    []
  );
  const [page, setPage] = useState(1);
  const [hasMore, setHasMore] = useState(false);
  const [loading, setLoading] = useState(false);

  const badgeCount = useMemo(
    () => notifications.length,
    [notifications.length]
  );

  const loadNotifications = useCallback(
    async (nextPage: number) => {
      try {
        setLoading(true);
        const resp: NotificationPageResponse = await getMyNotifications(
          nextPage,
          pageSize
        );
        const newItems = resp.data || [];
        setNotifications((prev) => {
          const merged = nextPage === 1 ? newItems : [...prev, ...newItems];
          return merged.filter(
            (f, idx, arr) => arr.findIndex((x) => x.id === f.id) === idx
          );
        });
        setHasMore(resp.currentPage < resp.totalPages);
        setPage(resp.currentPage);
      } finally {
        setLoading(false);
      }
    },
    [pageSize]
  );

  const handleOpen = async (event: React.MouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget);
    if (notifications.length === 0) {
      await loadNotifications(1);
    }
  };

  const handleClose = () => {
    setAnchorEl(null);
  };

  const handleClickNotification = (n: NotificationResponse) => {
    handleClose();
    if (n.actionUrl) {
      navigate(n.actionUrl);
    }
  };

  return (
    <>
      <IconButton
        onClick={handleOpen}
        sx={{
          color: "rgba(255, 255, 255, 0.9)",
          transition: "all 0.3s ease",
          "&:hover": {
            background: "rgba(255, 255, 255, 0.1)",
            transform: "scale(1.1)",
          },
        }}
      >
        <Badge badgeContent={badgeCount} color="error">
          <Notifications />
        </Badge>
      </IconButton>

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
              minWidth: 320,
              maxHeight: 380,
              p: 1,
            },
          },
        }}
      >
        <Box sx={{ px: 1, py: 0.5 }}>
          <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
            Notifications
          </Typography>
        </Box>
        <Divider />

        <Box sx={{ maxHeight: 300, overflowY: "auto" }}>
          {notifications.length === 0 && loading && (
            <Box sx={{ p: 2, display: "flex", justifyContent: "center" }}>
              <CircularProgress size={20} />
            </Box>
          )}

          {notifications.length === 0 && !loading && (
            <Box sx={{ p: 2 }}>
              <Typography variant="body2" color="text.secondary">
                No notifications
              </Typography>
            </Box>
          )}

          {notifications.map((n) => (
            <MenuItem
              key={n.id}
              sx={{ py: 1 }}
              onClick={() => handleClickNotification(n)}
            >
              <ListItemIcon>
                <Avatar src={n.avatar} sx={{ width: 24, height: 24 }} />
              </ListItemIcon>
              <Box>
                <Typography variant="body2" sx={{ fontWeight: 500 }}>
                  {n.displayName}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  {n.message}
                </Typography>
              </Box>
            </MenuItem>
          ))}
        </Box>

        <Divider />
        <Box
          sx={{
            p: 1,
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
          }}
        >
          <Typography variant="caption" color="text.secondary">
            Page {page}
          </Typography>
          <Button
            size="small"
            disabled={!hasMore || loading}
            onClick={() => loadNotifications(page + 1)}
          >
            Load more
          </Button>
        </Box>
      </Menu>
    </>
  );
}
