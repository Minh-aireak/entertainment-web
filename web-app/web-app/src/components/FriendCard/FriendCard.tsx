import React from "react";
import {
  Card,
  CardContent,
  Typography,
  Avatar,
  Button,
  Box,
  Chip,
} from "@mui/material";
import {
  Message as MessageIcon,
  PersonRemove as PersonRemoveIcon,
  PersonAdd as PersonAddIcon,
} from "@mui/icons-material";
import type {
  FriendResponse,
  RelationshipStatus,
} from "../../InterfaceDataType/DataType";
import styles from "./FriendCard.module.scss";

interface FriendCardProps {
  friend: FriendResponse;
  onMessage: (friendId: string) => void;
  onAddFriend: (friendId: string) => void;
  onUpdateStatus: (friendId: string, friendStatus: RelationshipStatus) => void;
}

export const FriendCard: React.FC<FriendCardProps> = ({
  friend,
  onMessage,
  onAddFriend,
  onUpdateStatus,
}) => {
  return (
    <Card className={styles.friendCard}>
      <CardContent className={styles.cardContent}>
        <Box className={styles.avatarSection}>
          <Avatar
            src={friend.avatar}
            className={styles.avatar}
            sx={{ width: 60, height: 60 }}
          />
          <Chip label="Offline" size="small" className={styles.statusChip} />
        </Box>

        <Box className={styles.userInfo}>
          <Typography variant="h6" className={styles.userName}>
            {friend.displayName}
          </Typography>
        </Box>

        <Box className={styles.actionButtons}>
          {friend.status === "UNFRIEND" ? (
            <Button
              variant="contained"
              size="small"
              startIcon={<PersonAddIcon />}
              onClick={() => onAddFriend(friend.userId)}
              className={styles.addFriendButton}
              fullWidth
            >
              Add Friend
            </Button>
          ) : (
            <>
              <Button
                variant="contained"
                size="small"
                startIcon={<MessageIcon />}
                onClick={() => onMessage(friend.userId)}
                className={styles.messageButton}
                fullWidth
              >
                Message
              </Button>
              <Box className={styles.secondaryActions}>
                <Button
                  variant="outlined"
                  size="small"
                  startIcon={<PersonRemoveIcon />}
                  onClick={() => onUpdateStatus(friend.userId, "UNFRIEND")}
                  color="error"
                  className={styles.removeButton}
                >
                  Remove
                </Button>
                {/* <Button
                  variant="outlined"
                  size="small"
                  startIcon={<PersonRemoveIcon />}
                  onClick={() => onUpdateStatus(friend.userId, "BLOCK")}
                  color="error"
                  className={styles.removeButton}
                >
                  Remove
                </Button> */}
              </Box>
            </>
          )}
        </Box>
      </CardContent>
    </Card>
  );
};
