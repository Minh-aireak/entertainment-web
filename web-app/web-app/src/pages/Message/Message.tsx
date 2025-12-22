import { useState, useEffect, useRef, useCallback, useMemo } from "react";
import { useNavigate } from "react-router-dom";
import axios from "axios";
import { on, off, emit } from "../../services/SocketIOService";
import InfiniteScroll from "react-infinite-scroll-component";
import {
  createConversation,
  getMyConversations,
  createChatMessage,
  getMyChatMessages,
  markAsSeen,
} from "../../services/ChatService";
import {
  Box,
  Card,
  TextField,
  Typography,
  Paper,
  IconButton,
  Avatar,
  List,
  ListItem,
  ListItemAvatar,
  ListItemText,
  Badge,
  CircularProgress,
  Stack,
  Button,
  ListItemButton,
  Fade,
  Tooltip,
  InputAdornment,
  Divider,
} from "@mui/material";
import {
  Send,
  ArrowBackIos,
  Search,
  AttachFile,
  EmojiEmotions,
  CheckCircle,
  Error,
  GroupAdd,
  VideoCall,
  Phone,
  Close,
  FilePresent,
  Download,
} from "@mui/icons-material";
import { CustomAlertSnackbar } from "../../components/CustomAlertSnackbar";
import "./Message.module.scss";
import type {
  ConversationResponse,
  ChatMessageResponse,
  ChatMessageCreateRequest,
  UserProfileResponse,
} from "../../InterfaceDataType/DataType";
import { Loading } from "../../components/Loading";
import { getMyInfo } from "../../services/UserService";

type MessagesMap = {
  [conversationId: string]: ChatMessageResponse[];
};

const Message = () => {
  const navigate = useNavigate();
  const [currentUser, setCurrentUser] = useState<UserProfileResponse>({
    code: 100,
    result: {
      userId: "",
      username: "",
      email: "",
      firstName: "",
      lastName: "",
      dob: new Date(),
      phoneNumber: "",
      city: "",
      joinDate: new Date(),
      avatar: "",
    },
  });
  const [conversations, setConversations] = useState<ConversationResponse[]>(
    []
  );
  useEffect(() => {
    const fetchCurrentUser = async () => {
      try {
        const user = await getMyInfo();
        setCurrentUser(user);
      } catch (e) {
        console.error("Failed to fetch current user:", e);
      }
    };
    fetchCurrentUser();
  }, []);

  useEffect(() => {
    if (conversations && conversations.length > 0) {
      const roomIds = conversations.map((c) => c.id);
      emit("join-rooms", roomIds);
    }
  }, [conversations]);
  const [loading, setLoading] = useState(false);
  const [selectedConversation, setSelectedConversation] =
    useState<ConversationResponse>({
      id: "",
      type: "",
      participantsHash: "",
      participantInfos: [
        {
          userId: "",
          displayName: "",
          avatar: "",
        },
      ],
      directName: "",
      directAvatar: "",
      groupName: "",
      groupOwner: "",
      groupAvatar: "",
      createdDate: new Date(),
      modifiedDate: new Date(),
    });
  const [messagesMap, setMessagesMap] = useState<MessagesMap>({});
  const [messageInput, setMessageInput] = useState<ChatMessageCreateRequest>({
    conversationId: "",
    messageType: "TEXT",
    content: "",
    attachmentFileUrl: "",
    replyToMessageId: "",
  });
  const messagesContainerRef = useRef<HTMLDivElement | null>(null);
  const [severity, setSeverity] = useState(false);
  const [snackbarMessage, setSnackbarMessage] = useState("");
  const [snackbarOpen, setSnackbarOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [typingUsers] = useState<string[]>([]);
  const [conversationMenuAnchor, setConversationMenuAnchor] =
    useState<null | HTMLElement>(null);
  const [replyToMessage, setReplyToMessage] =
    useState<ChatMessageResponse | null>(null);

  const [showEmojiPicker, setShowEmojiPicker] = useState(false);
  const [showFileUpload, setShowFileUpload] = useState(false);
  const [showScrollButton, setShowScrollButton] = useState(false);
  const [hasMore, setHasMore] = useState(false);
  const [currentPage, setCurrentPage] = useState(1);
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    on("message", handleIncomingMessage);
    return () => off("message", handleIncomingMessage);
  }, []);

  useEffect(() => {
    getMyConversations()
      .then((resp) => {
        setConversations(resp ?? []);
      })
      .catch((err) => {
        let msg = err.message;
        if (axios.isAxiosError(err) && err.response) {
          msg = err.response.data?.message ?? msg;
        }
        setSnackbarOpen(true);
        setSnackbarMessage(msg);
        setSeverity(false);
      });
  }, []);

  const handleConversationSelect = useCallback(
    (conversation: ConversationResponse) => {
      setSelectedConversation(conversation);
    },
    []
  );

  const filteredConversations = conversations.filter((c) => {
    const q = (searchQuery || "").trim().toLowerCase();
    if (!q) return true;
    const name = c.type === "DIRECT" ? c.directName : c.groupName;
    return (name || "").toLowerCase().includes(q);
  });

  const loadMessagePerPage = async (
    conversationId: string,
    page: number,
    size: number
  ) => {
    setLoading(true);
    try {
      const response = await getMyChatMessages(conversationId, page, size);
      setHasMore(response.length === size);
      setMessagesMap((prev) => {
        const prevList = prev[conversationId] ?? [];
        const combined = [...response, ...prevList];

        const deduped = combined.filter(
          (m, idx, arr) => arr.findIndex((x) => x.id === m.id) === idx
        );

        return { ...prev, [conversationId]: deduped };
      });

      try {
        await markAsSeen(conversationId);
      } catch (e) {
        console.warn("markAsSeen failed", e);
      }
      return response;
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

  const handleIncomingMessage = (message: any) => {
    let incoming = message;
    try {
      if (typeof incoming === "string") {
        incoming = JSON.parse(incoming);
      }
      incoming.me = incoming?.sender?.userId === currentUser.result.userId;
    } catch (e) {
      console.error("Failed to normalize incoming message:", e, message);
      return;
    }

    setMessagesMap((prev) => {
      const prevMsgs = prev[incoming.conversationId] ?? [];
      const exists = prevMsgs.some((m) => m.id === incoming.id);
      const updated = exists
        ? prevMsgs.map((m) => (m.id === incoming.id ? incoming : m))
        : [...prevMsgs, incoming];
      return { ...prev, [incoming.conversationId]: updated };
    });
  };

  const loadMore = async () => {
    if (loading) return;
    if (!hasMore) return;
    if (!selectedConversation?.id) return;

    const nextPage = currentPage + 1;
    const nextData = await loadMessagePerPage(
      selectedConversation.id,
      nextPage,
      15
    );
    if (nextData && nextData.length > 0) {
      setCurrentPage(nextPage);
    }
  };

  useEffect(() => {
    if (selectedConversation?.id) {
      setCurrentPage(1);
      loadMessagePerPage(selectedConversation.id, 1, 15);
    }
  }, [selectedConversation]);

  const currentMessages = useMemo(() => {
    return selectedConversation
      ? messagesMap[selectedConversation.id] ?? []
      : [];
  }, [selectedConversation, messagesMap]);

  const scrollToBottom = () => {
    const anchor = document.getElementById("messages-bottom-anchor");
    if (!anchor) return;
    requestAnimationFrame(() => {
      anchor.scrollIntoView({ behavior: "smooth", block: "end" });
    });
  };

  // const handleScroll = () => {
  //   const scrolled = document.documentElement.scrollTop;
  //   if (scrolled > 200) {
  //     setVisible(true);
  //   } else {
  //     setVisible(false);
  //   }
  // };

  // const handleTyping = (isTyping: boolean) => {
  //   if (socketRef.current && selectedConversation) {
  //     socketRef.current.emit("typing", {
  //       conversationId: selectedConversation.id,
  //       isTyping
  //     });
  //   }
  // };

  // const handleMessageAction = (action: string, message: ChatMessageResponse) => {
  //   switch (action) {
  //     case 'reply':
  //       setReplyToMessage(message);
  //       break;
  //     case 'edit':
  //       setMessageInput(prev => ({
  //         ...prev,
  //         content: message.content,
  //         replyToMessageId: message.id
  //       }));
  //       break;
  //     case 'delete':
  //       deleteChatMessage({
  //         chatMessageId: message.id,
  //         deleteType: "DELETED_FOR_SENDER"
  //       }).then(() => {
  //         setMessagesMap(prev => ({
  //           ...prev,
  //           [message.conversationId]: prev[message.conversationId]?

  const handleSendMessage = async () => {
    if (!messageInput.content.trim() || !selectedConversation) return;

    const convId = selectedConversation.id;
    const tempId = `temp-${Date.now()}`;
    const messageContent = messageInput.content;
    const messageType = messageInput.messageType;
    const attachmentUrl = messageInput.attachmentFileUrl;
    const replyToId = messageInput.replyToMessageId;

    const tempMessage: ChatMessageResponse = {
      id: tempId,
      conversationId: convId,
      attachmentFileUrl: attachmentUrl,
      messageType: messageType,
      createdDate: new Date(),
      modifiedDate: new Date(),
      seenAtMap: null,
      sender: {
        userId: "",
        displayName: "",
        avatar: "",
      },
      replyToMessageId: replyToId,
      me: true,
      content: messageContent,
      messageStatus: "SENDING",
    };

    setMessagesMap((prev) => ({
      ...prev,
      [convId]: [...(prev[convId] ?? []), tempMessage],
    }));

    scrollToBottom();

    setMessageInput({
      conversationId: "",
      messageType: "TEXT",
      content: "",
      attachmentFileUrl: "",
      replyToMessageId: "",
    });

    try {
      const resp = await createChatMessage({
        conversationId: convId,
        messageType: messageType,
        content: messageContent,
        attachmentFileUrl: attachmentUrl,
        replyToMessageId: replyToId,
      });

      if (resp) {
        setMessagesMap((prev) => {
          const arr = prev[convId] ?? [];
          const exists = arr.some((m) => m.id === tempId);
          if (!exists) {
            return prev;
          }

          return {
            ...prev,
            [convId]: arr.map((m) =>
              m.id === tempId
                ? {
                    ...m,
                    id: resp.id,
                    createdDate: resp.createdDate,
                    modifiedDate: resp.modifiedDate,
                    seenAtMap: resp.seenAtMap,
                    sender: resp.sender,
                    messageStatus: "SENT",
                  }
                : m
            ),
          };
        });
      }
    } catch (err: any) {
      setMessagesMap((prev) => {
        const oldArr = prev[convId] || [];
        return {
          ...prev,
          [convId]: oldArr.map((msg) =>
            msg.id === tempId
              ? { ...msg, messageStatus: "FAILED" as const }
              : msg
          ),
        };
      });

      let msg = err.message;
      if (axios.isAxiosError(err) && err.response) {
        msg = err.response.data?.message ?? msg;
      } else {
        msg = err.message;
      }
      setSnackbarOpen(true);
      setSnackbarMessage(msg);
      setSeverity(false);
    }
  };

  return (
    <>
      <CustomAlertSnackbar
        open={snackbarOpen}
        message={snackbarMessage}
        severity={severity ? "success" : "error"}
        onClose={() => setSnackbarOpen(false)}
      />
      <Loading loading={loading} />
      <Fade in timeout={800}>
        <Box
          sx={{
            display: "flex",
            height: "100vh",
            backgroundColor: "#f8fafc",
            overflow: "hidden",
          }}
        >
          <Card
            sx={{
              width: 360,
              backgroundColor: "white",
              borderRadius: 0,
              borderRight: "1px solid #e2e8f0",
              display: "flex",
              flexDirection: "column",
              boxShadow: "0 1px 3px rgba(0,0,0,0.1)",
            }}
          >
            <Box
              sx={{
                p: 2,
                borderBottom: "1px solid #e2e8f0",
                backgroundColor: "white",
              }}
            >
              <Stack direction="row" alignItems="center" spacing={2}>
                <IconButton
                  onClick={() => navigate("/")}
                  sx={{
                    color: "#64748b",
                    "&:hover": {
                      backgroundColor: "#f1f5f9",
                      color: "#334155",
                    },
                    transition: "all 0.2s ease",
                  }}
                >
                  <ArrowBackIos />
                </IconButton>
                <Typography
                  variant="h6"
                  sx={{
                    flexGrow: 1,
                    fontWeight: 600,
                    color: "#1e293b",
                    fontSize: "1.25rem",
                  }}
                >
                  Messages
                </Typography>
                <IconButton
                  onClick={() =>
                    createConversation([])
                      .then((resp) => {
                        setConversations((prev) => [resp, ...prev]);
                        setSelectedConversation(resp);
                      })
                      .catch((err) => {
                        let msg = err.message;
                        if (axios.isAxiosError(err) && err.response) {
                          msg = err.response.data?.message ?? msg;
                        }
                        setSnackbarOpen(true);
                        setSnackbarMessage(msg);
                        setSeverity(false);
                      })
                  }
                  sx={{
                    color: "#3b82f6",
                    backgroundColor: "#eff6ff",
                    "&:hover": {
                      backgroundColor: "#dbeafe",
                      transform: "scale(1.05)",
                    },
                    transition: "all 0.2s ease",
                  }}
                >
                  <GroupAdd />
                </IconButton>
              </Stack>
            </Box>

            <Box sx={{ p: 2 }}>
              <TextField
                fullWidth
                placeholder="Search conversations..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                size="small"
                sx={{
                  "& .MuiOutlinedInput-root": {
                    borderRadius: 2,
                    backgroundColor: "#f8fafc",
                    border: "1px solid #e2e8f0",
                    "&:hover": {
                      border: "1px solid #cbd5e1",
                      backgroundColor: "#f1f5f9",
                    },
                    "&.Mui-focused": {
                      border: "1px solid #3b82f6",
                      backgroundColor: "white",
                      boxShadow: "0 0 0 3px rgba(59, 130, 246, 0.1)",
                    },
                    transition: "all 0.2s ease",
                  },
                  "& .MuiInputBase-input": {
                    fontSize: "0.875rem",
                  },
                }}
                slotProps={{
                  input: {
                    startAdornment: (
                      <InputAdornment position="start">
                        <Search sx={{ color: "#64748b", fontSize: 20 }} />
                      </InputAdornment>
                    ),
                  },
                }}
              />
            </Box>
            <Divider sx={{ borderColor: "rgba(102, 126, 234, 0.1)" }} />

            <List
              sx={{
                flexGrow: 1,
                overflow: "auto",
                px: 1,
                height: "calc(100vh - 140px)",
              }}
            >
              {filteredConversations.map((conversation, index) => (
                <Fade in timeout={600 + index * 100} key={conversation.id}>
                  <ListItem disablePadding>
                    <ListItemButton
                      selected={selectedConversation?.id === conversation.id}
                      onClick={() => handleConversationSelect(conversation)}
                      sx={{
                        borderRadius: 2,
                        mb: 0.5,
                        transition: "all 0.3s ease",
                        "&:hover": {
                          backgroundColor: "rgba(102, 126, 234, 0.08)",
                          transform: "translateX(4px)",
                          boxShadow: "0 4px 12px rgba(102, 126, 234, 0.15)",
                        },
                        "&.Mui-selected": {
                          backgroundColor: "rgba(102, 126, 234, 0.12)",
                          borderLeft: "4px solid #667eea",
                          "&:hover": {
                            backgroundColor: "rgba(102, 126, 234, 0.15)",
                          },
                        },
                      }}
                    >
                      <ListItemAvatar>
                        <Badge
                          badgeContent={(conversation as any).unread || 0}
                          color="error"
                          invisible={
                            !(conversation as any).unread ||
                            (conversation as any).unread === 0
                          }
                          sx={{
                            "& .MuiBadge-badge": {
                              animation: (conversation as any).unread
                                ? "pulse 2s infinite"
                                : "none",
                            },
                          }}
                        >
                          {conversation.type === "DIRECT" ? (
                            <Avatar
                              src={conversation.directAvatar}
                              sx={{
                                width: 48,
                                height: 48,
                                fontSize: "1.2rem",
                                fontWeight: 600,
                                border: "2px solid rgba(255,255,255,0.8)",
                                boxShadow: "0 4px 12px rgba(0,0,0,0.15)",
                                background:
                                  "linear-gradient(135deg, #667eea, #764ba2)",
                                transition: "all 0.3s ease",
                                "&:hover": {
                                  transform: "scale(1.05)",
                                },
                              }}
                            >
                              {conversation.directName?.charAt(0).toUpperCase()}
                            </Avatar>
                          ) : (
                            <Avatar
                              src={conversation.groupAvatar}
                              sx={{
                                width: 48,
                                height: 48,
                                fontSize: "1.2rem",
                                fontWeight: 600,
                                border: "2px solid rgba(255,255,255,0.8)",
                                boxShadow: "0 4px 12px rgba(0,0,0,0.15)",
                                background:
                                  "linear-gradient(135deg, #ff6b6b, #ee5a24)",
                                transition: "all 0.3s ease",
                                "&:hover": {
                                  transform: "scale(1.05)",
                                },
                              }}
                            >
                              {conversation.groupName?.charAt(0).toUpperCase()}
                            </Avatar>
                          )}
                        </Badge>
                      </ListItemAvatar>
                      <ListItemText
                        primary={
                          <Typography
                            variant="subtitle2"
                            sx={{
                              fontWeight: (conversation as any).unread
                                ? 600
                                : 500,
                              fontSize: "1rem",
                              color: (conversation as any).unread
                                ? "#2c3e50"
                                : "#34495e",
                              alignItems: "center",
                            }}
                          >
                            {conversation.type === "DIRECT"
                              ? conversation.directName
                              : conversation.groupName}
                          </Typography>
                        }
                        secondary={
                          <Box
                            sx={{
                              display: "flex",
                              justifyContent: "space-between",
                              alignItems: "center",
                              mt: 0.5,
                            }}
                          >
                            <Typography
                              noWrap
                              component="div"
                              variant="body2"
                              sx={{
                                fontWeight: (conversation as any).unread
                                  ? 500
                                  : 400,
                                fontSize: "0.875rem",
                                color: "#7f8c8d",
                                maxWidth: "200px",
                                display: "inline-block",
                              }}
                            >
                              {(conversation as any).lastMessage ||
                                "No messages yet"}
                            </Typography>
                          </Box>
                        }
                        disableTypography
                      />
                    </ListItemButton>
                  </ListItem>
                </Fade>
              ))}
            </List>
          </Card>

          <Box
            sx={{
              flexGrow: 1,
              display: "flex",
              flexDirection: "column",
              height: "100vh",
              overflow: "hidden",
              backgroundColor: "white",
            }}
          >
            {selectedConversation ? (
              <>
                <Box
                  sx={{
                    p: 2,
                    borderBottom: "1px solid #e2e8f0",
                    backgroundColor: "white",
                  }}
                >
                  <Stack direction="row" alignItems="center" spacing={2}>
                    <Avatar
                      src={
                        selectedConversation.type === "DIRECT"
                          ? selectedConversation.directAvatar
                          : selectedConversation.groupAvatar
                      }
                      sx={{
                        width: 44,
                        height: 44,
                        bgcolor:
                          selectedConversation.type === "GROUP"
                            ? "#f59e0b"
                            : "#3b82f6",
                        fontSize: "1.1rem",
                        fontWeight: 600,
                      }}
                    >
                      {selectedConversation.type === "DIRECT"
                        ? selectedConversation.directName
                            ?.charAt(0)
                            .toUpperCase()
                        : selectedConversation.groupName
                            ?.charAt(0)
                            .toUpperCase()}
                    </Avatar>
                    <Box sx={{ flexGrow: 1 }}>
                      <Typography
                        variant="h6"
                        sx={{
                          fontWeight: 600,
                          color: "#1e293b",
                          fontSize: "1.1rem",
                          lineHeight: 1.2,
                        }}
                      >
                        {selectedConversation.type === "DIRECT"
                          ? selectedConversation.directName
                          : selectedConversation.groupName}
                      </Typography>
                      {selectedConversation.type === "GROUP" && (
                        <Typography
                          variant="body2"
                          sx={{
                            color: "#64748b",
                            fontSize: "0.8rem",
                          }}
                        >
                          {selectedConversation.participantInfos?.length}{" "}
                          members
                        </Typography>
                      )}
                    </Box>
                    <Stack direction="row" spacing={1}>
                      <Tooltip title="Voice call">
                        <IconButton
                          size="small"
                          sx={{
                            color: "#64748b",
                            "&:hover": {
                              backgroundColor: "#f1f5f9",
                              color: "#334155",
                            },
                            transition: "all 0.2s ease",
                          }}
                        >
                          <Phone />
                        </IconButton>
                      </Tooltip>
                      <Tooltip title="Video call">
                        <IconButton
                          size="small"
                          sx={{
                            color: "#64748b",
                            "&:hover": {
                              backgroundColor: "#f1f5f9",
                              color: "#334155",
                            },
                            transition: "all 0.2s ease",
                          }}
                        >
                          <VideoCall />
                        </IconButton>
                      </Tooltip>
                    </Stack>
                  </Stack>
                </Box>

                <Box
                  sx={{
                    flexGrow: 1,
                    backgroundColor: "#f8fafc",
                    height: "calc(100vh - 140px)",
                    overflow: "hidden",
                    display: "flex",
                    flexDirection: "column",
                    minHeight: 0,
                  }}
                >
                  <Box
                    id="message-scroll-container"
                    sx={{
                      display: "flex",
                      flexDirection: "column",
                      justifyContent: "flex-end",
                      width: "100%",
                      flex: 1,
                      minHeight: 0,
                      overflowY: "auto",
                      overflowX: "hidden",
                      padding: 2,
                      position: "relative",
                      backgroundColor: "white",
                      "&::-webkit-scrollbar": {
                        width: "6px",
                      },
                      "&::-webkit-scrollbar-track": {
                        background: "#f1f5f9",
                        borderRadius: "3px",
                      },
                      "&::-webkit-scrollbar-thumb": {
                        background: "#cbd5e1",
                        borderRadius: "3px",
                      },
                      "&::-webkit-scrollbar-thumb:hover": {
                        background: "#94a3b8",
                      },
                    }}
                    ref={messagesContainerRef}
                  >
                    <InfiniteScroll
                      dataLength={currentMessages.length}
                      next={loadMore}
                      hasMore={hasMore}
                      loader={
                        <Box
                          sx={{
                            display: "flex",
                            justifyContent: "center",
                            p: 2,
                          }}
                        >
                          <CircularProgress
                            size={24}
                            sx={{ color: "#667eea" }}
                          />
                        </Box>
                      }
                      inverse={true}
                      scrollableTarget="message-scroll-container"
                    >
                      <Box
                        sx={{
                          display: "flex",
                          flexDirection: "column",
                          justifyContent: "flex-end",
                          height: "100%",
                        }}
                      >
                        {currentMessages.map((msg, index) => (
                          <Box
                            key={msg.id}
                            sx={{
                              display: "flex",
                              justifyContent: msg.me
                                ? "flex-end"
                                : "flex-start",
                              mb: 1.5,
                              px: 1,
                            }}
                          >
                            {!msg.me && (
                              <Avatar
                                src={msg.sender.avatar || ""}
                                sx={{
                                  mr: 1,
                                  alignSelf: "flex-end",
                                  width: 32,
                                  height: 32,
                                  bgcolor: "#3b82f6",
                                  fontSize: "0.9rem",
                                }}
                              >
                                {msg.sender.displayName
                                  ?.charAt(0)
                                  .toUpperCase()}
                              </Avatar>
                            )}
                            <Box sx={{ maxWidth: "70%" }}>
                              {replyToMessage &&
                                replyToMessage.id === msg.id && (
                                  <Paper
                                    elevation={0}
                                    sx={{
                                      p: 1,
                                      mb: 0.5,
                                      backgroundColor: "#f1f5f9",
                                      borderLeft: "3px solid #3b82f6",
                                      borderRadius: 1,
                                    }}
                                  >
                                    <Typography
                                      variant="caption"
                                      sx={{
                                        color: "#3b82f6",
                                        fontWeight: 600,
                                        fontSize: "0.75rem",
                                      }}
                                    >
                                      Replying to: {replyToMessage.content}
                                    </Typography>
                                  </Paper>
                                )}
                              <Paper
                                elevation={0}
                                sx={{
                                  p: 1.5,
                                  backgroundColor: msg.me
                                    ? msg.messageStatus === "FAILED"
                                      ? "#ef4444"
                                      : msg.messageStatus === "SENDING"
                                      ? "#94a3b8"
                                      : "#3b82f6"
                                    : "white",
                                  color: msg.me ? "white" : "#1e293b",
                                  borderRadius: msg.me
                                    ? "16px 16px 4px 16px"
                                    : "16px 16px 16px 4px",
                                  border: msg.me ? "none" : "1px solid #e2e8f0",
                                  boxShadow: "0 1px 3px rgba(0,0,0,0.1)",
                                }}
                              >
                                <Typography
                                  variant="body2"
                                  sx={{
                                    whiteSpace: "pre-wrap",
                                    wordBreak: "break-word",
                                    lineHeight: 1.4,
                                    fontSize: "0.875rem",
                                  }}
                                >
                                  {msg.content}
                                </Typography>

                                {msg.attachmentFileUrl && (
                                  <Box sx={{ mt: 1 }}>
                                    {msg.messageType === "IMAGE" ? (
                                      <img
                                        src={msg.attachmentFileUrl}
                                        alt="Attachment"
                                        style={{
                                          maxWidth: "100%",
                                          maxHeight: "200px",
                                          borderRadius: "8px",
                                          objectFit: "cover",
                                        }}
                                      />
                                    ) : (
                                      <Box
                                        sx={{
                                          display: "flex",
                                          alignItems: "center",
                                          p: 1,
                                          backgroundColor:
                                            "rgba(255,255,255,0.1)",
                                          borderRadius: 1,
                                        }}
                                      >
                                        <FilePresent
                                          sx={{ mr: 1, fontSize: 16 }}
                                        />
                                        <Typography
                                          variant="body2"
                                          sx={{ fontSize: "0.8rem" }}
                                        >
                                          File attachment
                                        </Typography>
                                        <Download
                                          sx={{
                                            ml: "auto",
                                            cursor: "pointer",
                                            fontSize: 16,
                                          }}
                                        />
                                      </Box>
                                    )}
                                  </Box>
                                )}
                              </Paper>

                              <Stack
                                direction="row"
                                spacing={1}
                                alignItems="center"
                                sx={{
                                  mt: 0.5,
                                  justifyContent: msg.me
                                    ? "flex-end"
                                    : "flex-start",
                                  px: 1,
                                }}
                              >
                                {msg.messageStatus === "SENDING" && (
                                  <CircularProgress
                                    size={12}
                                    sx={{ color: "#64748b" }}
                                  />
                                )}
                                {msg.messageStatus === "SENT" && (
                                  <CheckCircle
                                    fontSize="small"
                                    sx={{ color: "#64748b", fontSize: 14 }}
                                  />
                                )}
                                {msg.messageStatus === "DELIVERED" && (
                                  <CheckCircle
                                    fontSize="small"
                                    sx={{ color: "#10b981", fontSize: 14 }}
                                  />
                                )}
                                {msg.messageStatus === "FAILED" && (
                                  <Error
                                    sx={{ fontSize: 14, color: "#ef4444" }}
                                  />
                                )}
                                <Typography
                                  variant="caption"
                                  sx={{
                                    color: "#64748b",
                                    fontSize: "0.7rem",
                                  }}
                                >
                                  {new Date(msg.createdDate).toLocaleTimeString(
                                    [],
                                    {
                                      hour: "2-digit",
                                      minute: "2-digit",
                                    }
                                  )}
                                </Typography>
                              </Stack>
                            </Box>
                          </Box>
                        ))}
                        <div id="messages-bottom-anchor" />
                      </Box>
                    </InfiniteScroll>
                    {/* {typingUsers.length > 0 && (
                    <Box sx={{ px: 1, mb: 1 }}>
                      <Paper
                        elevation={0}
                        sx={{
                          p: 1.5,
                          backgroundColor: "grey.100",
                          borderRadius: "18px 18px 18px 4px",
                          maxWidth: "fit-content",
                        }}
                      >
                        <Typography variant="body2" color="text.secondary">
                          {typingUsers.length === 1
                            ? "Someone is typing..."
                            : `${typingUsers.length} people are typing...`}
                        </Typography>
                      </Paper>
                    </Box>
                  )} */}
                    {/* {visible && (
                    <Button style={{ display: visible ? "inline" : "none" }}>
                      <KeyboardArrowDown onClick={handleScroll} />
                    </Button>
                  )} */}
                    {/* Bottom anchor moved to Messages component */}
                  </Box>
                </Box>

                <Box
                  sx={{
                    p: 2,
                    backgroundColor: "white",
                    borderTop: "1px solid #e2e8f0",
                  }}
                >
                  {replyToMessage && (
                    <Box
                      sx={{
                        mb: 2,
                        p: 1.5,
                        backgroundColor: "#f8fafc",
                        borderRadius: 2,
                        border: "1px solid #e2e8f0",
                      }}
                    >
                      <Box
                        sx={{
                          display: "flex",
                          justifyContent: "space-between",
                          alignItems: "center",
                        }}
                      >
                        <Typography
                          variant="body2"
                          sx={{
                            color: "#3b82f6",
                            fontWeight: 500,
                          }}
                        >
                          Replying to: {replyToMessage.content.substring(0, 50)}
                          ...
                        </Typography>
                        <IconButton
                          size="small"
                          onClick={() => setReplyToMessage(null)}
                          sx={{
                            color: "#64748b",
                            "&:hover": {
                              backgroundColor: "#f1f5f9",
                              color: "#3b82f6",
                            },
                          }}
                        >
                          <Close fontSize="small" />
                        </IconButton>
                      </Box>
                    </Box>
                  )}

                  <Stack direction="row" spacing={1} alignItems="flex-end">
                    <IconButton
                      sx={{
                        color: "#64748b",
                        "&:hover": {
                          backgroundColor: "#f1f5f9",
                          color: "#3b82f6",
                        },
                      }}
                      onClick={() => setShowFileUpload(!showFileUpload)}
                    >
                      <AttachFile />
                    </IconButton>
                    <IconButton
                      sx={{
                        color: "#64748b",
                        "&:hover": {
                          backgroundColor: "#f1f5f9",
                          color: "#3b82f6",
                        },
                      }}
                      onClick={() => setShowFileUpload(!showFileUpload)}
                    >
                      <AttachFile />
                    </IconButton>
                    <IconButton
                      sx={{
                        color: "#64748b",
                        "&:hover": {
                          backgroundColor: "#f1f5f9",
                          color: "#3b82f6",
                        },
                      }}
                      onClick={() => setShowFileUpload(!showFileUpload)}
                    >
                      <AttachFile />
                    </IconButton>

                    <TextField
                      fullWidth
                      multiline
                      maxRows={4}
                      variant="outlined"
                      placeholder="Type a message..."
                      value={messageInput.content}
                      onChange={(e) => {
                        setMessageInput((prev) => ({
                          ...prev,
                          conversationId: selectedConversation.id,
                          content: e.target.value,
                        }));
                        // handleTyping(e.target.value.length > 0);
                      }}
                      onKeyDown={(e) => {
                        const native = (e as any).nativeEvent;
                        if (native?.isComposing || native?.keyCode === 229) {
                          return;
                        }
                        if (e.key === "Enter" && !e.shiftKey) {
                          e.preventDefault();
                          handleSendMessage();
                        }
                      }}
                      sx={{
                        "& .MuiOutlinedInput-root": {
                          borderRadius: 2,
                          backgroundColor: "#f8fafc",
                          border: "1px solid #e2e8f0",
                          "&:hover": {
                            borderColor: "#3b82f6",
                          },
                          "&.Mui-focused": {
                            borderColor: "#3b82f6",
                            boxShadow: "0 0 0 3px rgba(59, 130, 246, 0.1)",
                          },
                        },
                        "& .MuiInputBase-input": {
                          fontSize: "0.95rem",
                        },
                      }}
                    />

                    <IconButton
                      sx={{
                        color: "#64748b",
                        "&:hover": {
                          backgroundColor: "#f1f5f9",
                          color: "#3b82f6",
                        },
                      }}
                      onClick={() => setShowEmojiPicker(!showEmojiPicker)}
                    >
                      <EmojiEmotions />
                    </IconButton>

                    <IconButton
                      onClick={handleSendMessage}
                      disabled={!messageInput?.content.trim()}
                      sx={{
                        backgroundColor: messageInput?.content.trim()
                          ? "#3b82f6"
                          : "#e2e8f0",
                        color: messageInput?.content.trim()
                          ? "white"
                          : "#94a3b8",
                        width: 48,
                        height: 48,
                        "&:hover": {
                          backgroundColor: messageInput?.content.trim()
                            ? "#2563eb"
                            : "#e2e8f0",
                        },
                        "&:disabled": {
                          backgroundColor: "#e2e8f0",
                          color: "#94a3b8",
                        },
                      }}
                    >
                      <Send />
                    </IconButton>
                  </Stack>

                  {showFileUpload && (
                    <Box
                      sx={{
                        p: 2,
                        mt: 1,
                        backgroundColor: "#f8fafc",
                        border: "2px dashed #cbd5e1",
                        borderRadius: 2,
                        textAlign: "center",
                        "&:hover": {
                          borderColor: "#3b82f6",
                          backgroundColor: "#f1f5f9",
                        },
                      }}
                    >
                      <input
                        type="file"
                        multiple
                        style={{ display: "none" }}
                        id="file-upload"
                      />
                      <label htmlFor="file-upload">
                        <Button
                          component="span"
                          variant="outlined"
                          sx={{
                            borderColor: "#3b82f6",
                            color: "#3b82f6",
                            borderRadius: 1.5,
                            "&:hover": {
                              borderColor: "#2563eb",
                              backgroundColor: "rgba(59, 130, 246, 0.1)",
                            },
                          }}
                        >
                          Choose file to upload
                        </Button>
                      </label>
                    </Box>
                  )}
                </Box>
              </>
            ) : (
              <Box
                sx={{
                  display: "flex",
                  flexDirection: "column",
                  alignItems: "center",
                  justifyContent: "center",
                  height: "100%",
                  backgroundColor: "white",
                  p: 4,
                }}
              >
                <GroupAdd
                  sx={{
                    fontSize: 64,
                    color: "#94a3b8",
                    mb: 2,
                  }}
                />
                <Typography
                  variant="h6"
                  sx={{
                    color: "#1e293b",
                    fontWeight: 600,
                    mb: 1,
                    textAlign: "center",
                  }}
                >
                  Welcome to Messages
                </Typography>
                <Typography
                  variant="body2"
                  sx={{
                    color: "#64748b",
                    textAlign: "center",
                    maxWidth: 300,
                    lineHeight: 1.5,
                  }}
                >
                  Select a conversation from the sidebar to start chatting
                </Typography>
              </Box>
            )}
          </Box>
        </Box>
      </Fade>

      {/* <Menu
        anchorEl={conversationMenuAnchor}
        open={Boolean(conversationMenuAnchor)}
        onClose={() => setConversationMenuAnchor(null)}
      >
        <MenuItem onClick={() => setConversationMenuAnchor(null)}>
          <Info sx={{ mr: 1 }} />
          Conversation Info
        </MenuItem>
        <MenuItem onClick={() => setConversationMenuAnchor(null)}>
          <PersonAdd sx={{ mr: 1 }} />
          Add People
        </MenuItem>
        <MenuItem onClick={() => setConversationMenuAnchor(null)}>
          <GroupAdd sx={{ mr: 1 }} />
          Create Group
        </MenuItem>
        <MenuItem
          // onClick={() => setConversationMenuAnchor(null)}
          sx={{ color: "error.main" }}
        >
          <Delete sx={{ mr: 1 }} />
          Delete Conversation
        </MenuItem>
      </Menu> */}

      {/* Message Menu
      <Menu
        anchorEl={messageMenuAnchor}
        open={Boolean(messageMenuAnchor)}
        onClose={() => setMessageMenuAnchor(null)}
      >
        <MenuItem onClick={() => handleMessageAction("reply", selectedMessage!)}>
          <Reply sx={{ mr: 1 }} />
          Reply
        </MenuItem>
        {selectedMessage?.me && (
          <>
            <MenuItem onClick={() => handleMessageAction("edit", selectedMessage!)}>
              <Edit sx={{ mr: 1 }} />
              Edit
            </MenuItem>
            <MenuItem 
              onClick={() => handleMessageAction("delete", selectedMessage!)}
              sx={{ color: "error.main" }}
            >
              <Delete sx={{ mr: 1 }} />
              Delete
            </MenuItem>
          </>
        )}
      </Menu> */}
    </>
  );
};

export default Message;
