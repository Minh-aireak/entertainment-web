import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import {
  Card,
  CardContent,
  Typography,
  Avatar,
  Button,
  Chip,
  TextField,
  InputAdornment,
  Tab,
  Tabs,
  Grid,
  Badge,
  Divider,
  List,
  ListItem,
  ListItemAvatar,
  ListItemText,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Snackbar,
  Alert,
  CircularProgress,
} from "@mui/material";
import {
  Search as SearchIcon,
  PersonAdd as PersonAddIcon,
  Check as CheckIcon,
  Close as CloseIcon,
  Message as MessageIcon,
  MoreVert as MoreVertIcon,
  People as PeopleIcon,
  PersonAddAlt1 as PersonAddAlt1Icon,
  Notifications as NotificationsIcon,
  Person as PersonIcon,
  PersonRemove as PersonRemoveIcon,
} from "@mui/icons-material";
import styles from "./Friend.module.scss";
import { CustomAlertSnackbar } from "../../components/CustomAlertSnackbar";
import { FriendRequestCard } from "../../components/FriendRequestCard/FriendRequestCard";
import { FriendCard } from "../../components/FriendCard/FriendCard";
import {
  sendFriendRequest,
  updateFriendRequestStatus,
  updateRelationshipStatus,
  getListFriend,
  getListFriendRequest,
} from "../../services/FriendService";
import type {
  ConversationRequest,
  FriendResponse,
  RelationshipStatus,
  UpdateFriendRequestStatus,
  UpdateRelationshipStatus,
} from "../../InterfaceDataType/DataType";
import axios from "axios";
import { Loading } from "../../components/Loading";
import { createConversation } from "../../services/ChatService";

import { socketIoService } from "../../services/SocketIoService";

export default function Friends() {
  const navigate = useNavigate();
  const [friends, setFriends] = useState<FriendResponse[]>([]);
  const [friendRequests, setFriendRequests] = useState<FriendResponse[]>([]);
  const [selectedFriend, setSelectedFriend] = useState<FriendResponse | null>(
    null
  );
  const [loading, setLoading] = useState(false);
  const [activeTab, setActiveTab] = useState(0);
  const [searchQuery, setSearchQuery] = useState("");
  const [snackbarOpen, setSnackbarOpen] = useState(false);
  const [snackbarMessage, setSnackbarMessage] = useState("");
  const [severity, setSeverity] = useState(true);
  const [currentPage, setCurrentPage] = useState(1);
  const [hasMoreFriends, setHasMoreFriends] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);

  const loadFriends = async (page = 1, append = false) => {
    try {
      if (page === 1) setLoading(true);
      else setLoadingMore(true);

      const response = await getListFriend(page, 10);
      const newFriends = response.data || [];

      if (append) {
        setFriends((prev) => [...prev, ...newFriends]);
      } else {
        setFriends(newFriends);
      }

      setHasMoreFriends(newFriends.length === 10);
      setCurrentPage(page);
    } catch (error: any) {
      let messageToShow = error.message;
      if (axios.isAxiosError(error)) {
        if (error.response) {
          messageToShow = error.response.data.message;
        }
      }
      setSnackbarMessage(messageToShow);
      setSnackbarOpen(true);
      setSeverity(false);
    } finally {
      setLoading(false);
      setLoadingMore(false);
    }
  };

  const loadMoreFriends = async () => {
    if (!loadingMore && hasMoreFriends) {
      await loadFriends(currentPage + 1, true);
    }
  };

  const loadFriendRequests = async () => {
    try {
      const response = await getListFriendRequest(1, 6);
      setFriendRequests([...friendRequests, ...(response.data || [])]);
    } catch (error: any) {
      let messageToShow = error.message;
      if (axios.isAxiosError(error)) {
        if (error.response) {
          messageToShow = error.response.data.message;
        }
      }
      setSnackbarMessage(messageToShow);
      setSnackbarOpen(true);
      setSeverity(false);
    }
  };

  useEffect(() => {
    const loadData = async () => {
      setLoading(true);
      try {
        await Promise.all([loadFriends(1, false), loadFriendRequests()]);
      } catch (error: any) {
        let messageToShow = error.message;
        if (axios.isAxiosError(error)) {
          if (error.response) {
            messageToShow = error.response.data.message;
          }
        }
        setSnackbarMessage(messageToShow);
        setSnackbarOpen(true);
        setSeverity(false);
      } finally {
        setLoading(false);
      }
    };

    loadData();
  }, []);

  useEffect(() => {
    const handleFriendRequestSent = (data: FriendResponse) => {
      setFriendRequests((prev) => {
        if (!prev.some((req) => req.userId === data.userId)) {
          setSnackbarMessage(
            `You have a new friend request from ${data.displayName}`
          );
          setSnackbarOpen(true);
          setSeverity(true);
          return [data, ...prev];
        }
        return prev;
      });
    };

    const handleFriendRequestUpdated = (data: FriendResponse) => {
      setFriendRequests((prev) =>
        prev.filter((req) => req.userId !== data.userId)
      );
      if (data.status === "ACCEPTED") {
        setFriends((prev) => {
          if (!prev.some((friend) => friend.userId === data.userId)) {
            setSnackbarMessage(
              `${data.displayName} accepted your friend request`
            );
            setSnackbarOpen(true);
            setSeverity(true);
            return [data, ...prev];
          }
          return prev;
        });
      } else if (data.status === "DECLINED") {
        setSnackbarMessage(`${data.displayName} declined your friend request`);
        setSnackbarOpen(true);
        setSeverity(false);
      }
    };

    socketIoService.on("FriendRequestSent", handleFriendRequestSent);
    socketIoService.on("FriendRequestUpdated", handleFriendRequestUpdated);

    return () => {
      socketIoService.off("FriendRequestSent", handleFriendRequestSent);
      socketIoService.off("FriendRequestUpdated", handleFriendRequestUpdated);
    };
  }, []);

  const handleTabChange = (event: React.SyntheticEvent, newValue: number) => {
    setActiveTab(newValue);
  };

  const handleFriendRequestStatus = async (
    update: UpdateFriendRequestStatus
  ) => {
    try {
      const request = friendRequests.find((r) => r.userId === update.userId);
      if (request) {
        const response = await updateFriendRequestStatus(update);
        setFriendRequests((prev) =>
          prev.filter((r) => r.userId !== request.userId)
        );
        setSnackbarMessage(response);
      }
    } catch (error: any) {
      let messageToShow = error.message;
      if (axios.isAxiosError(error)) {
        if (error.response) {
          messageToShow = error.response.data.message;
        }
      }
      setSnackbarMessage(messageToShow);
      setSnackbarOpen(true);
      setSeverity(false);
    }
  };

  const handleFriendStatus = async (
    friendId: string,
    friendStatus: RelationshipStatus
  ) => {
    try {
      const friend = friends.find((f) => f.userId === friendId);
      if (friend) {
        const updateRequest: UpdateRelationshipStatus = {
          userId: friendId,
          friendStatus: friendStatus,
        };
        await updateRelationshipStatus(updateRequest);
        setFriends((prev) => prev.filter((f) => f.userId !== friendId));
      }
    } catch (error: any) {
      let messageToShow = error.message;
      if (axios.isAxiosError(error)) {
        if (error.response) {
          messageToShow = error.response.data.message;
        }
      }
      setSnackbarMessage(messageToShow);
      setSnackbarOpen(true);
      setSeverity(false);
    }
  };

  const handleSendFriendRequest = async (toUserId: string) => {
    try {
      const response = await sendFriendRequest(toUserId);
      setSnackbarMessage(response);
    } catch (error: any) {
      let messageToShow = error.message;
      if (axios.isAxiosError(error)) {
        if (error.response) {
          messageToShow = error.response.data.message;
        }
      }
      setSnackbarMessage(messageToShow);
      setSnackbarOpen(true);
      setSeverity(false);
    }
  };

  const handleMessageFriend = async (friendId: string) => {
    try {
      const friend = friends.find((f) => f.userId === friendId);
      if (!friend) return;

      const conversationRequest: ConversationRequest = {
        type: "DIRECT",
        participantInfos: [
          {
            userId: friendId,
            displayName: friend.displayName,
            avatar: friend.avatar,
          },
        ],
      };

      const conversation = await createConversation(conversationRequest);

      navigate(`/messages?conversationId=${conversation.id}`);
    } catch (error: any) {
      let messageToShow = error.message;
      if (axios.isAxiosError(error)) {
        if (error.response) {
          messageToShow = error.response.data.message;
        }
      }
      setSnackbarMessage(messageToShow);
      setSnackbarOpen(true);
      setSeverity(false);
    }
  };

  const filteredFriends = friends.filter((friend) =>
    friend.displayName.toLowerCase().includes(searchQuery.toLowerCase())
  );

  return (
    <div className={styles.container}>
      <CustomAlertSnackbar
        open={snackbarOpen}
        message={snackbarMessage}
        severity={severity ? "success" : "error"}
        onClose={() => setSnackbarOpen(false)}
      />
      <Loading loading={loading} />

      <div className={styles.header}>
        <Typography variant="h4" className={styles.title}>
          Friends
        </Typography>
        <Typography variant="subtitle1" className={styles.subtitle}>
          Connect with your travel companions
        </Typography>
      </div>

      <Card className={styles.searchCard}>
        <CardContent>
          <TextField
            fullWidth
            placeholder="Search friends..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            slotProps={{
              input: {
                startAdornment: (
                  <InputAdornment position="start">
                    <SearchIcon color="action" />
                  </InputAdornment>
                ),
              },
            }}
            className={styles.searchField}
          />
        </CardContent>
      </Card>

      <Card className={styles.tabCard}>
        <Tabs
          value={activeTab}
          onChange={handleTabChange}
          className={styles.tabs}
        >
          <Tab
            icon={<PeopleIcon />}
            label={`My Friends (${friends.length})`}
            iconPosition="start"
          />
          <Tab
            icon={
              <Badge badgeContent={friendRequests.length} color="error">
                <NotificationsIcon />
              </Badge>
            }
            label="Friend Requests"
            iconPosition="start"
          />
          <Tab
            icon={<PersonAddAlt1Icon />}
            label="Suggestions"
            iconPosition="start"
          />
        </Tabs>
      </Card>

      {activeTab === 0 && (
        <Box className={styles.horizontalScrollContainer}>
          {filteredFriends.length === 0 ? (
            <div className={styles.emptyState}>
              <PeopleIcon className={styles.emptyIcon} />
              <Typography variant="h6" color="text.secondary">
                {searchQuery ? "No friends found" : "No friends yet"}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                {searchQuery
                  ? "Try adjusting your search terms"
                  : "Start connecting with people to build your network!"}
              </Typography>
            </div>
          ) : (
            filteredFriends.map((friend) => (
              <Box key={friend.userId} sx={{ minWidth: "300px" }}>
                <FriendCard
                  key={friend.userId}
                  friend={friend}
                  onMessage={handleMessageFriend}
                  onUpdateStatus={handleFriendStatus}
                  onAddFriend={handleSendFriendRequest}
                />
              </Box>
            ))
          )}
        </Box>
      )}

      {activeTab === 1 && (
        <Card className={styles.requestCard}>
          <CardContent>
            {friendRequests.length === 0 ? (
              <div className={styles.emptyState}>
                <NotificationsIcon className={styles.emptyIcon} />
                <Typography variant="h6" color="text.secondary">
                  No friend requests
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  You're all caught up!
                </Typography>
              </div>
            ) : (
              <div className={styles.horizontalScrollContainer}>
                {friendRequests.map((request) => (
                  <FriendRequestCard
                    key={request.userId}
                    request={request}
                    onAccept={handleFriendRequestStatus}
                    onDecline={handleFriendRequestStatus}
                  />
                ))}
              </div>
            )}
          </CardContent>
        </Card>
      )}

      {/* {activeTab === 2 && (
         <Grid container spacing={3}>
           {suggestions.length === 0 ? (
             <Grid item xs={12}>
               <div className={styles.emptyState}>
                 <PersonAddAlt1Icon className={styles.emptyIcon} />
                 <Typography variant="h6" color="text.secondary">
                   No suggestions available
                 </Typography>
                 <Typography variant="body2" color="text.secondary">
                   Check back later for new friend suggestions!
                 </Typography>
               </div>
             </Grid>
           ) : (
             suggestions.map((suggestion) => (
               <Grid item xs={12} sm={6} md={4} key={suggestion.userId}>
                 <Card className={styles.suggestionCard}>
                   <CardContent>
                     <div className={styles.suggestionHeader}>
                       <Avatar
                         src={suggestion.avatar || "/api/placeholder/40/40"}
                         className={styles.suggestionAvatar}
                       />
                       <div className={styles.suggestionInfo}>
                         <Typography
                           variant="h6"
                           className={styles.suggestionName}
                         >
                           {suggestion.displayName}
                         </Typography>
                         <Chip
                           label="Suggested"
                           size="small"
                           className={styles.suggestedBadge}
                         />
                       </div>
                     </div>

                     <Typography
                       variant="body2"
                       color="text.secondary"
                       className={styles.mutualFriends}
                     >
                       {suggestion.email}
                     </Typography>

                     <Button
                       variant="contained"
                       fullWidth
                       startIcon={<PersonAddIcon />}
                       onClick={() => handleSendFriendRequest(suggestion.userId)}
                       className={styles.addFriendButton}
                     >
                       Add Friend
                     </Button>
                   </CardContent>
                 </Card>
               </Grid>
             ))
           )}
         </Grid>
       )} */}
    </div>
  );
}
