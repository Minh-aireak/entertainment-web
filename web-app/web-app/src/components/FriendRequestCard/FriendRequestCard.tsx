import React from "react";
import {
  Card,
  CardContent,
  Typography,
  Avatar,
  Button,
  Box,
} from "@mui/material";
import { Check as CheckIcon, Close as CloseIcon } from "@mui/icons-material";
import type {
  FriendResponse,
  UpdateFriendRequestStatus,
} from "../../InterfaceDataType/DataType";
import styles from "./FriendRequestCard.module.scss";

interface FriendRequestCardProps {
  request: FriendResponse;
  onAccept: (update: UpdateFriendRequestStatus) => void;
  onDecline: (update: UpdateFriendRequestStatus) => void;
}

export const FriendRequestCard: React.FC<FriendRequestCardProps> = ({
  request,
  onAccept,
  onDecline,
}) => {
  const handleAccept = () => {
    onAccept({
      userId: request.userId,
      friendRequestStatus: "ACCEPTED",
    });
  };

  const handleDecline = () => {
    onDecline({
      userId: request.userId,
      friendRequestStatus: "CANCEL",
    });
  };

  return (
    <Card className={styles.requestCard}>
      <CardContent className={styles.cardContent}>
        <Box className={styles.avatarSection}>
          <Avatar
            src={request.avatar}
            className={styles.avatar}
            sx={{ width: 60, height: 60 }}
          />
        </Box>

        <Box className={styles.userInfo}>
          <Typography variant="h6" className={styles.userName}>
            {request.displayName}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {new Date(request.date).toLocaleDateString()}
          </Typography>
        </Box>

        <Box className={styles.actionButtons}>
          <Button
            variant="contained"
            size="small"
            startIcon={<CheckIcon />}
            onClick={handleAccept}
            className={styles.acceptButton}
            sx={{ mb: 1 }}
          >
            Accept
          </Button>
          <Button
            variant="outlined"
            size="small"
            startIcon={<CloseIcon />}
            onClick={handleDecline}
            color="error"
            className={styles.declineButton}
          >
            Decline
          </Button>
        </Box>
      </CardContent>
    </Card>
  );
};
