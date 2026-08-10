 import React, { useState, useEffect, useLayoutEffect, useMemo, useRef, useCallback } from 'react';
import {
  Box,
  Typography,
  List,
  ListItemButton,
  ListItemAvatar,
  ListItemText,
  Avatar,
  CircularProgress,
  TextField,
  InputAdornment,
  IconButton,
  Badge,
  styled,
  Paper,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Checkbox,
  Menu,
  MenuItem,
  ListItemIcon,
  Skeleton,
} from '@mui/material';
import {
  Search, Send, MoreVert, Chat as ChatIcon, Create as NewChatIcon, Edit as EditIcon, PhotoCamera,
  AttachFile, Reply as ReplyIcon, Delete as DeleteIcon, InsertDriveFile, Close as CloseIcon,
  ContentCopy as CopyIcon, KeyboardArrowDown as ArrowDownIcon,
} from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { useDispatch, useSelector } from 'react-redux';
import { toast } from 'react-hot-toast';
import { useLocation, useNavigate } from 'react-router-dom';
import { type RootState } from '../store/index';
import { setConversations, setActiveConversation, addMessage, setMessages, prependMessages, updateUserStatus, updateMessageSeen } from '../store';
import type { Conversation, ConversationType, ConversationParticipant, ConversationResponse, ChatMessage, ChatMessageCreateRequest, MessageType, UserRelationshipResponse } from '../models';
import { chatService } from '../api/chatService';
import { friendService } from '../api/friendService';
import { fileService } from '../api/fileService';
import { useWebSocket } from '../contexts/WebSocketContext';
import { useConfirmDialog } from '../contexts/ConfirmDialogContext';

const StyledBadge = styled(Badge)(({ theme }) => ({
  '& .MuiBadge-badge': {
    backgroundColor: '#44b700',
    color: '#44b700',
    boxShadow: `0 0 0 2px ${theme.palette.background.paper}`,
    '&::after': {
      position: 'absolute',
      top: 0,
      left: 0,
      width: '100%',
      height: '100%',
      borderRadius: '50%',
      animation: 'ripple 1.2s infinite ease-in-out',
      border: '1px solid currentColor',
      content: '""',
    },
  },
  '@keyframes ripple': {
    '0%': {
      transform: 'scale(.8)',
      opacity: 1,
    },
    '100%': {
      transform: 'scale(2.4)',
      opacity: 0,
    },
  },
}));

const MESSAGES_PAGE_SIZE = 10;
const LOAD_MORE_SCROLL_THRESHOLD = 60;
// How close to the bottom (px) the user has to be for a new message to auto-scroll them down.
// Further away than this and we assume they're deliberately reading history — surface the
// "↓ new messages" pill instead of yanking their scroll position.
const NEAR_BOTTOM_THRESHOLD = 100;
const MESSAGE_SKELETON_COUNT = 6;
// Cap how many overlapping "seen by" avatars render per message before collapsing into a "+N"
// badge — group conversations can have dozens of members all pointing at the same last-read message.
const SEEN_AVATARS_VISIBLE_LIMIT = 5;

interface MessagesPaginationState {
  page: number;
  hasMore: boolean;
  loadingMore: boolean;
}

type AttachmentMessageType = 'IMAGE' | 'VIDEO' | 'AUDIO' | 'FILE';

// Mirrors the size caps enforced server-side in file-service's FileService.validateFile
const ATTACHMENT_SIZE_LIMITS: Record<AttachmentMessageType, number> = {
  IMAGE: 10 * 1024 * 1024,
  VIDEO: 500 * 1024 * 1024,
  AUDIO: 50 * 1024 * 1024,
  FILE: 25 * 1024 * 1024,
};

const getMessageTypeFromMime = (mimeType: string): AttachmentMessageType => {
  if (mimeType.startsWith('image/')) return 'IMAGE';
  if (mimeType.startsWith('video/')) return 'VIDEO';
  if (mimeType.startsWith('audio/')) return 'AUDIO';
  return 'FILE';
};

const getMessagePreviewText = (msg: { messageType: MessageType; content?: string }): string => {
  switch (msg.messageType) {
    case 'IMAGE': return 'Đã gửi một ảnh';
    case 'VIDEO': return 'Đã gửi một video';
    case 'AUDIO': return 'Đã gửi một audio';
    case 'FILE': return 'Đã gửi một tệp';
    case 'DELETED_FOR_EVERYONE': return 'Tin nhắn đã được thu hồi';
    default: return msg.content || '';
  }
};

const getFileNameFromUrl = (url?: string): string => {
  if (!url) return 'Tệp đính kèm';
  try {
    const clean = url.split('?')[0];
    const parts = clean.split('/');
    return decodeURIComponent(parts[parts.length - 1]) || 'Tệp đính kèm';
  } catch {
    return 'Tệp đính kèm';
  }
};

const renderMessageBody = (msg: ChatMessage) => {
  if (msg.messageType === 'DELETED_FOR_EVERYONE') {
    return (
      <Typography variant="body2" sx={{ fontStyle: 'italic', opacity: 0.7 }}>
        Tin nhắn đã được thu hồi
      </Typography>
    );
  }

  switch (msg.messageType) {
    case 'IMAGE':
      return (
        <Box
          component="img"
          src={msg.attachmentFileUrl}
          onClick={() => window.open(msg.attachmentFileUrl, '_blank', 'noopener,noreferrer')}
          sx={{ maxWidth: 260, maxHeight: 320, borderRadius: '12px', display: 'block', cursor: 'pointer', objectFit: 'cover' }}
        />
      );
    case 'VIDEO':
      return (
        <Box
          component="video"
          src={msg.attachmentFileUrl}
          controls
          sx={{ maxWidth: 280, maxHeight: 320, borderRadius: '12px', display: 'block' }}
        />
      );
    case 'AUDIO':
      return <Box component="audio" src={msg.attachmentFileUrl} controls sx={{ maxWidth: 260, height: 36 }} />;
    case 'FILE':
      return (
        <Box
          component="a"
          href={msg.attachmentFileUrl}
          target="_blank"
          rel="noopener noreferrer"
          sx={{ display: 'flex', alignItems: 'center', gap: 1, color: 'inherit', textDecoration: 'none' }}
        >
          <InsertDriveFile fontSize="small" />
          <Typography variant="body2" sx={{ wordBreak: 'break-word' }}>
            {getFileNameFromUrl(msg.attachmentFileUrl)}
          </Typography>
        </Box>
      );
    default:
      return (
        <Typography variant="body1" sx={{ fontSize: '0.94rem', lineHeight: 1.4, wordBreak: 'break-word' }}>
          {msg.content}
        </Typography>
      );
  }
};

const ChatPage: React.FC = React.memo(() => {
  const dispatch = useDispatch();
  const location = useLocation();
  const navigate = useNavigate();
  const confirmDialog = useConfirmDialog();
  const { user } = useSelector((state: RootState) => state.auth);
  const { isConnected, send, subscribe } = useWebSocket();

  const { conversations, activeConversationId, messages, onlineUsers } = useSelector((state: RootState) => state.chat);

  const theme = useTheme();
  const isDarkMode = theme.palette.mode === 'dark';
  // Messenger-style bubble/surface grey that isn't one of MUI's semantic tokens — has to
  // flip explicitly between modes instead of resolving through the theme automatically.
  const surfaceElevated = isDarkMode ? '#3A3B3C' : theme.palette.grey[200];
  const surfaceElevatedHover = isDarkMode ? '#4A4B4C' : theme.palette.grey[300];

  const [inputText, setInputText] = useState('');
  const joinedRoomRef = useRef<string | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);
  const messagesContainerRef = useRef<HTMLDivElement>(null);
  const scrolledConversationRef = useRef<string | null>(null);
  const activeConversationIdRef = useRef<string | null>(null);
  const markSeenTimerRef = useRef<number | undefined>(undefined);
  const scrollAdjustRef = useRef<{ prevScrollHeight: number; prevScrollTop: number } | null>(null);
  const skipAutoScrollRef = useRef(false);
  const isNearBottomRef = useRef(true);
  // Set only by the WebSocket 'new-message' handler when it's for the conversation currently
  // open — the one true source of "a message just arrived live." The scroll effect reads and
  // clears it instead of inferring "new message" from activeMessages.length, which was also
  // (wrongly) tripped by the unrelated REST refetch-and-replace on every conversation switch.
  const liveMessageArrivedRef = useRef<{ conversationId: string; senderId: string } | null>(null);
  const [hasNewMessagesBelow, setHasNewMessagesBelow] = useState(false);
  const [loadingMessages, setLoadingMessages] = useState(false);
  const [pagination, setPagination] = useState<Record<string, MessagesPaginationState>>({});
  const [unreadCounts, setUnreadCounts] = useState<Record<string, number>>({});

  const attachmentInputRef = useRef<HTMLInputElement>(null);
  const [uploadingAttachment, setUploadingAttachment] = useState(false);
  const [replyTarget, setReplyTarget] = useState<ChatMessage | null>(null);
  const [editingMessageId, setEditingMessageId] = useState<string | null>(null);
  const [messageMenuAnchor, setMessageMenuAnchor] = useState<HTMLElement | null>(null);
  const [selectedMessage, setSelectedMessage] = useState<ChatMessage | null>(null);

  const [groupMenuAnchor, setGroupMenuAnchor] = useState<HTMLElement | null>(null);
  const [renameDialogOpen, setRenameDialogOpen] = useState(false);
  const [renameValue, setRenameValue] = useState('');
  const [renamingGroup, setRenamingGroup] = useState(false);
  const [uploadingGroupAvatar, setUploadingGroupAvatar] = useState(false);
  const groupAvatarInputRef = useRef<HTMLInputElement>(null);

  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState<ConversationResponse[] | null>(null);
  const [searching, setSearching] = useState(false);

  const [newConvOpen, setNewConvOpen] = useState(false);
  const [friendQuery, setFriendQuery] = useState('');
  const [friendOptions, setFriendOptions] = useState<{ id: string; displayName: string; avatar?: string }[]>([]);
  const [friendsLoading, setFriendsLoading] = useState(false);
  const [selectedFriendIds, setSelectedFriendIds] = useState<string[]>([]);
  const [creatingConversation, setCreatingConversation] = useState(false);

  const fetchConversations = useCallback(async () => {
    try {
      const response = await chatService.getMyConversations(1, 50);
      if (response.code === 1000) {
        const mappedConversations: Conversation[] = response.result.data.map(conv => ({
          ...conv,
          type: conv.type as ConversationType,
          lastMessageId: undefined, // Add if needed
        } as any));
        dispatch(setConversations(mappedConversations));
      }
    } catch (error) {
      console.error('Failed to fetch conversations:', error);
    }
  }, [dispatch]);

  const fetchUnreadCounts = useCallback(async () => {
    try {
      const response = await chatService.getUnreadCount();
      if (response.code === 1000) {
        setUnreadCounts(response.result.data || {});
      }
    } catch (error) {
      console.error('Failed to fetch unread counts:', error);
    }
  }, []);

  const fetchMessages = useCallback(async (conversationId: string) => {
    setLoadingMessages(true);
    try {
      const response = await chatService.getMyChatMessages(conversationId, 1, MESSAGES_PAGE_SIZE);
      if (response.code === 1000) {
        // The API returns the page newest-first (for "give me the latest N" pagination);
        // reverse to oldest-first so the list renders top-to-bottom like a normal chat and
        // "scroll to the last item" actually lands on the most recent message.
        dispatch(setMessages({
          conversationId,
          messages: response.result.data.slice().reverse() as any
        }));
        setPagination((prev) => ({
          ...prev,
          [conversationId]: {
            page: 1,
            hasMore: response.result.totalPages > 1,
            loadingMore: false,
          },
        }));
      }
    } catch (error) {
      console.error('Failed to fetch messages:', error);
    } finally {
      setLoadingMessages(false);
    }
  }, [dispatch]);

  const loadMoreMessages = useCallback(async (conversationId: string) => {
    const current = pagination[conversationId];
    if (!current || !current.hasMore || current.loadingMore) return;

    const nextPage = current.page + 1;
    const container = messagesContainerRef.current;
    if (container) {
      scrollAdjustRef.current = { prevScrollHeight: container.scrollHeight, prevScrollTop: container.scrollTop };
    }

    setPagination((prev) => ({
      ...prev,
      [conversationId]: { ...prev[conversationId], loadingMore: true },
    }));

    try {
      const response = await chatService.getMyChatMessages(conversationId, nextPage, MESSAGES_PAGE_SIZE);
      if (response.code === 1000) {
        skipAutoScrollRef.current = true;
        dispatch(prependMessages({
          conversationId,
          messages: response.result.data.slice().reverse() as any,
        }));
        setPagination((prev) => ({
          ...prev,
          [conversationId]: {
            page: nextPage,
            hasMore: nextPage < response.result.totalPages,
            loadingMore: false,
          },
        }));
      }
    } catch (error) {
      console.error('Failed to load more messages:', error);
      scrollAdjustRef.current = null;
      setPagination((prev) => ({
        ...prev,
        [conversationId]: { ...prev[conversationId], loadingMore: false },
      }));
    }
  }, [dispatch, pagination]);

  const handleMessagesScroll = useCallback(() => {
    const container = messagesContainerRef.current;
    if (!container || !activeConversationId) return;
    if (container.scrollTop <= LOAD_MORE_SCROLL_THRESHOLD) {
      loadMoreMessages(activeConversationId);
    }

    const distanceFromBottom = container.scrollHeight - container.scrollTop - container.clientHeight;
    const nearBottom = distanceFromBottom < NEAR_BOTTOM_THRESHOLD;
    isNearBottomRef.current = nearBottom;
    if (nearBottom) {
      setHasNewMessagesBelow(false);
    }
  }, [activeConversationId, loadMoreMessages]);

  const handleScrollToBottomClick = useCallback(() => {
    scrollRef.current?.scrollIntoView({ behavior: 'smooth' });
    isNearBottomRef.current = true;
    setHasNewMessagesBelow(false);
  }, []);

  const markAsSeen = useCallback(async (conversationId: string) => {
    try {
      await chatService.seenAt(conversationId);
      setUnreadCounts((prev) => (prev[conversationId] ? { ...prev, [conversationId]: 0 } : prev));
    } catch (error) {
      console.error('Failed to mark as seen:', error);
    }
  }, []);

  useEffect(() => {
    fetchConversations();
    fetchUnreadCounts();
  }, [fetchConversations, fetchUnreadCounts]);

  // Opening a conversation from another page (e.g. the "Nhắn tin" button on Friends) passes the
  // target user's id via navigation state instead of a conversation id, since the direct
  // conversation may not exist yet — the backend get-or-creates it by participant hash.
  useEffect(() => {
    const targetUserId = (location.state as { openWithUserId?: string } | null)?.openWithUserId;
    if (!targetUserId || !user?.id) return;

    // Clear the navigation state immediately so a later refresh/back doesn't reopen it.
    navigate(location.pathname, { replace: true, state: null });

    const openConversationWithUser = async () => {
      try {
        const response = await chatService.createConversation([user.id, targetUserId]);
        if (response.code === 1000) {
          const created = response.result;
          dispatch(setConversations(
            conversations.some((c) => c.id === created.id)
              ? conversations
              : [{ ...created, type: created.type as ConversationType } as any, ...conversations]
          ));
          dispatch(setActiveConversation(created.id));
        }
      } catch (error) {
        console.error('Failed to open conversation:', error);
        toast.error('Không thể mở cuộc trò chuyện');
      }
    };

    openConversationWithUser();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [location.state, user?.id]);

  useEffect(() => {
    activeConversationIdRef.current = activeConversationId;
  }, [activeConversationId]);

  useEffect(() => {
    if (activeConversationId) {
      // Only hit the REST endpoint the first time this conversation is opened. The cache is
      // already kept live by the WebSocket 'new-message' subscriber below regardless of which
      // conversation is active, so refetching page 1 on every revisit only ever replaced a
      // possibly-larger cached array (grown via "load more" or background socket updates) with
      // just the newest 10 — silently discarding load-more progress and resetting pagination,
      // and tripping the scroll effect into thinking a new message had arrived.
      if (!messages[activeConversationId]) {
        fetchMessages(activeConversationId);
      }
      markAsSeen(activeConversationId);
    }

    if (isConnected) {
      const previousRoomId = joinedRoomRef.current;
      if (previousRoomId && previousRoomId !== activeConversationId) {
        send({ type: 'leave-room', roomId: previousRoomId });
        joinedRoomRef.current = null;
      }

      // Only join if the tab is actually visible; the visibility effect below joins on foreground.
      if (activeConversationId && document.visibilityState === 'visible') {
        send({ type: 'join-room', roomId: activeConversationId });
        joinedRoomRef.current = activeConversationId;
      }
    }
    // `messages` is intentionally excluded: it gets a new top-level reference on every incoming
    // socket message for ANY conversation (Immer), so declaring it here would re-run this
    // effect (and re-send markAsSeen/join-room) on every unrelated background message instead
    // of only when the active conversation actually changes. The `!messages[...]` check below
    // only needs the current value at the moment this effect fires, which the closure already
    // provides — same intentionally-stale-safe pattern as the effect above (openWithUserId).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeConversationId, fetchMessages, isConnected, markAsSeen, send]);

  useEffect(() => {
    const handleVisibilityChange = () => {
      if (!isConnected) return;

      if (document.visibilityState === 'hidden') {
        if (joinedRoomRef.current) {
          send({ type: 'leave-room', roomId: joinedRoomRef.current });
          joinedRoomRef.current = null;
        }
      } else if (activeConversationIdRef.current && joinedRoomRef.current !== activeConversationIdRef.current) {
        send({ type: 'join-room', roomId: activeConversationIdRef.current });
        joinedRoomRef.current = activeConversationIdRef.current;
      }
    };

    document.addEventListener('visibilitychange', handleVisibilityChange);
    return () => document.removeEventListener('visibilitychange', handleVisibilityChange);
  }, [isConnected, send]);

  useEffect(() => {
    const unsubscribe = [
      subscribe('new-message', (data) => {
        dispatch(addMessage({
          conversationId: data.conversationId,
          message: { ...data, me: data.senderId === user?.id },
        }));

        // The one true "a message just arrived live" signal for the scroll effect — deliberately
        // not set for update-message/delete-message (in-place edits must never move the
        // viewport) and not set for a conversation that isn't the active one.
        if (data.conversationId === activeConversationIdRef.current) {
          liveMessageArrivedRef.current = { conversationId: data.conversationId, senderId: data.senderId };
        }

        if (data.conversationId === activeConversationIdRef.current && data.senderId !== user?.id) {
          if (markSeenTimerRef.current !== undefined) {
            window.clearTimeout(markSeenTimerRef.current);
          }
          markSeenTimerRef.current = window.setTimeout(() => {
            markAsSeen(data.conversationId);
          }, 800);
        }
      }),
      subscribe('update-message', (data) => {
        dispatch(addMessage({
          conversationId: data.conversationId,
          message: { ...data, me: data.senderId === user?.id },
        }));
        fetchConversations();
      }),
      subscribe('delete-message', (data) => {
        dispatch(addMessage({
          conversationId: data.conversationId,
          message: { ...data, me: data.senderId === user?.id },
        }));
        fetchConversations();
      }),
      subscribe('update-conversation-list', () => {
        fetchConversations();
        fetchUnreadCounts();
      }),
      subscribe('conversation-created', fetchConversations),
      subscribe('user-status-changed', (data) => dispatch(updateUserStatus(data))),
      subscribe('seen-message', (data) => dispatch(updateMessageSeen(data))),
    ];

    return () => {
      unsubscribe.forEach((removeListener) => removeListener());
      if (markSeenTimerRef.current !== undefined) {
        window.clearTimeout(markSeenTimerRef.current);
      }
    };
  }, [dispatch, fetchConversations, fetchUnreadCounts, markAsSeen, subscribe, user?.id]);

  useEffect(() => () => {
    if (joinedRoomRef.current) {
      send({ type: 'leave-room', roomId: joinedRoomRef.current });
      joinedRoomRef.current = null;
    }
  }, [send]);

  const activeMessages = useMemo(
    () => (activeConversationId ? messages[activeConversationId] || [] : []),
    [messages, activeConversationId],
  );

  useLayoutEffect(() => {
    const container = messagesContainerRef.current;
    if (scrollAdjustRef.current && container) {
      const { prevScrollHeight, prevScrollTop } = scrollAdjustRef.current;
      container.scrollTop = container.scrollHeight - prevScrollHeight + prevScrollTop;
      scrollAdjustRef.current = null;
    }
  }, [activeMessages]);

  // useLayoutEffect (not useEffect) so the jump-to-bottom happens before the browser paints the
  // newly-rendered message list — otherwise the user briefly sees the top of the conversation
  // flash on screen before the scroll kicks in.
  useLayoutEffect(() => {
    if (!activeConversationId) return;

    // Consume unconditionally on every run, before any branch below can return early, so a live
    // arrival can never survive into a later, unrelated render (e.g. a REST refetch or a
    // load-more prepend landing right after) and get misattributed to that render instead.
    const liveArrival = liveMessageArrivedRef.current;
    liveMessageArrivedRef.current = null;

    if (skipAutoScrollRef.current) {
      skipAutoScrollRef.current = false;
      return;
    }

    // Opening a conversation (or its first page of messages just finished loading) should land
    // directly on the latest message. Only animate the scroll for messages that arrive afterwards.
    const isNewConversation = scrolledConversationRef.current !== activeConversationId;
    if (isNewConversation && activeMessages.length === 0) return;

    if (!isNewConversation) {
      // No genuine WebSocket "new-message" arrival caused this render — a REST refetch/replace,
      // a load-more prepend, an in-place edit/delete, or anything else that mutates the array
      // for unrelated reasons. Never move the viewport for those.
      if (!liveArrival) return;

      const isOwnMessage = liveArrival.senderId === user?.id;
      if (!isNearBottomRef.current && !isOwnMessage) {
        // User is scrolled up reading history and someone else's message just arrived — don't
        // yank them down, just flag that new messages are waiting below.
        setHasNewMessagesBelow(true);
        scrolledConversationRef.current = activeConversationId;
        return;
      }
    }

    scrollRef.current?.scrollIntoView({ behavior: isNewConversation ? 'auto' : 'smooth' });
    if (isNewConversation) {
      isNearBottomRef.current = true;
    }
    setHasNewMessagesBelow(false);
    scrolledConversationRef.current = activeConversationId;
  }, [activeMessages, activeConversationId, user?.id]);

  const handleSendMessage = async () => {
    if (!inputText.trim() || !activeConversationId) return;

    if (editingMessageId) {
      const messageId = editingMessageId;
      try {
        const response = await chatService.updateChatMessage({ chatMessageId: messageId, content: inputText });
        if (response.code !== 1000) {
          toast.error(response.message || 'Không thể sửa tin nhắn');
        }
      } catch (error) {
        console.error('Failed to update message:', error);
        toast.error('Không thể sửa tin nhắn');
      } finally {
        setEditingMessageId(null);
        setInputText('');
      }
      return;
    }

    try {
      const request: ChatMessageCreateRequest = {
        conversationId: activeConversationId,
        content: inputText,
        messageType: 'TEXT',
        replyToMessageId: replyTarget?.id,
      };
      const response = await chatService.createChatMessage(request);
      if (response.code === 1000) {
        // Message will be added via WebSocket event
      }
      setInputText('');
      setReplyTarget(null);
    } catch (error) {
      console.error('Failed to send message:', error);
    }
  };

  const handleAttachmentButtonClick = () => {
    if (uploadingAttachment || editingMessageId) return;
    attachmentInputRef.current?.click();
  };

  const handleAttachmentFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file || !activeConversationId) return;

    const messageType = getMessageTypeFromMime(file.type);
    const maxSize = ATTACHMENT_SIZE_LIMITS[messageType];
    if (file.size > maxSize) {
      toast.error(`Kích thước tệp không được vượt quá ${Math.round(maxSize / (1024 * 1024))}MB`);
      return;
    }

    setUploadingAttachment(true);
    const toastId = toast.loading('Đang tải lên...');
    try {
      const uploadRes = await fileService.uploadFile(file);
      if (uploadRes.code === 1000) {
        const request: ChatMessageCreateRequest = {
          conversationId: activeConversationId,
          content: '',
          messageType,
          attachmentFileId: uploadRes.result.id,
          replyToMessageId: replyTarget?.id,
        };
        const response = await chatService.createChatMessage(request);
        if (response.code === 1000) {
          toast.success('Đã gửi', { id: toastId });
          setReplyTarget(null);
        } else {
          toast.error(response.message || 'Gửi thất bại', { id: toastId });
        }
      } else {
        toast.error('Tải tệp lên thất bại: ' + (uploadRes.message || ''), { id: toastId });
      }
    } catch (error) {
      console.error('Failed to send attachment:', error);
      toast.error('Có lỗi xảy ra khi gửi tệp đính kèm', { id: toastId });
    } finally {
      setUploadingAttachment(false);
    }
  };

  const handleOpenMessageMenu = (anchorEl: HTMLElement, msg: ChatMessage) => {
    setMessageMenuAnchor(anchorEl);
    setSelectedMessage(msg);
  };

  const handleCloseMessageMenu = () => {
    setMessageMenuAnchor(null);
    setSelectedMessage(null);
  };

  // Desktop reveals the actions button on hover (CSS-only), but touch devices have no hover
  // state, so a long-press on the bubble itself opens the same menu.
  const LONG_PRESS_DURATION = 500;
  const longPressTimerRef = useRef<number | undefined>(undefined);

  const clearLongPressTimer = () => {
    if (longPressTimerRef.current !== undefined) {
      window.clearTimeout(longPressTimerRef.current);
      longPressTimerRef.current = undefined;
    }
  };

  const handleMessageTouchStart = (e: React.TouchEvent<HTMLElement>, msg: ChatMessage) => {
    const target = e.currentTarget;
    clearLongPressTimer();
    longPressTimerRef.current = window.setTimeout(() => {
      longPressTimerRef.current = undefined;
      if (navigator.vibrate) navigator.vibrate(10);
      handleOpenMessageMenu(target, msg);
    }, LONG_PRESS_DURATION);
  };

  const handleCopyMessageClick = async () => {
    const target = selectedMessage;
    handleCloseMessageMenu();
    if (!target || target.messageType !== 'TEXT') return;
    try {
      await navigator.clipboard.writeText(target.content);
      toast.success('Đã sao chép');
    } catch (error) {
      console.error('Failed to copy message:', error);
      toast.error('Không thể sao chép');
    }
  };

  const handleReplyClick = () => {
    if (selectedMessage) {
      setReplyTarget(selectedMessage);
      setEditingMessageId(null);
    }
    handleCloseMessageMenu();
  };

  const handleEditMessageClick = () => {
    if (selectedMessage && selectedMessage.messageType === 'TEXT') {
      setEditingMessageId(selectedMessage.id);
      setInputText(selectedMessage.content);
      setReplyTarget(null);
    }
    handleCloseMessageMenu();
  };

  const handleDeleteMessageClick = async () => {
    const target = selectedMessage;
    handleCloseMessageMenu();
    if (!target || !activeConversationId) return;
    const confirmed = await confirmDialog({
      title: 'Thu hồi tin nhắn',
      message: 'Bạn có chắc chắn muốn thu hồi tin nhắn này?',
      confirmText: 'Thu hồi',
    });
    if (!confirmed) return;

    try {
      const response = await chatService.deleteChatMessage({ chatMessageId: target.id, conversationId: activeConversationId });
      if (response.data.code !== 1000) {
        toast.error(response.data.message || 'Không thể thu hồi tin nhắn');
      }
    } catch (error) {
      console.error('Failed to delete message:', error);
      toast.error('Không thể thu hồi tin nhắn');
    }
  };

  const handleCancelReply = () => setReplyTarget(null);
  const handleCancelEdit = () => {
    setEditingMessageId(null);
    setInputText('');
  };

  // Debounced backend search over the user's conversations (by name/participant).
  useEffect(() => {
    const query = searchQuery.trim();
    if (!query) {
      setSearchResults(null);
      setSearching(false);
      return;
    }

    setSearching(true);
    const timer = window.setTimeout(async () => {
      try {
        const response = await chatService.searchConversations(query, 1, 20);
        if (response.code === 1000) {
          setSearchResults(response.result.data);
        }
      } catch (error) {
        console.error('Failed to search conversations:', error);
      } finally {
        setSearching(false);
      }
    }, 300);

    return () => window.clearTimeout(timer);
  }, [searchQuery]);

  const isSearchingMode = searchQuery.trim().length > 0;
  const displayedConversations: any[] = isSearchingMode ? (searchResults ?? []) : conversations;

  const handleSelectSearchResult = (conv: ConversationResponse) => {
    if (!conversations.some((c) => c.id === conv.id)) {
      dispatch(setConversations([{ ...conv, type: conv.type as ConversationType } as any, ...conversations]));
    }
    dispatch(setActiveConversation(conv.id));
    setSearchQuery('');
    setSearchResults(null);
  };

  // Friend picker for starting a new (or resuming an existing) conversation.
  const fetchFriendOptions = useCallback(async (query: string) => {
    setFriendsLoading(true);
    try {
      const response = query
        ? await friendService.searchFriends(query, 1, 50)
        : await friendService.getMyFriends(1, 50);
      if (response.code === 1000) {
        setFriendOptions(response.result.data.map((f: UserRelationshipResponse) => ({
          id: f.friendId,
          displayName: f.displayName,
          avatar: f.friendAvatar,
        })));
      }
    } catch (error) {
      console.error('Failed to load friends:', error);
    } finally {
      setFriendsLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!newConvOpen) return;
    const timer = window.setTimeout(() => {
      fetchFriendOptions(friendQuery.trim());
    }, 300);
    return () => window.clearTimeout(timer);
  }, [newConvOpen, friendQuery, fetchFriendOptions]);

  const handleOpenNewConversation = () => {
    setNewConvOpen(true);
    setFriendQuery('');
    setSelectedFriendIds([]);
  };

  const handleCloseNewConversation = () => {
    if (creatingConversation) return;
    setNewConvOpen(false);
  };

  const toggleSelectedFriend = (id: string) => {
    setSelectedFriendIds((prev) => (prev.includes(id) ? prev.filter((f) => f !== id) : [...prev, id]));
  };

  const handleCreateConversation = async () => {
    if (selectedFriendIds.length === 0 || !user?.id) return;
    setCreatingConversation(true);
    try {
      const ids = Array.from(new Set([...selectedFriendIds, user.id]));
      const response = await chatService.createConversation(ids);
      if (response.code === 1000) {
        const created = response.result;
        if (!conversations.some((c) => c.id === created.id)) {
          dispatch(setConversations([{ ...created, type: created.type as ConversationType } as any, ...conversations]));
        }
        dispatch(setActiveConversation(created.id));
        toast.success('Đã tạo cuộc trò chuyện');
        setNewConvOpen(false);
      }
    } catch (error) {
      console.error('Failed to create conversation:', error);
      toast.error('Không thể tạo cuộc trò chuyện');
    } finally {
      setCreatingConversation(false);
    }
  };

  const getConversationName = (conv: any) => {
    return conv.conversationName || 'Unknown';
  };

  const applyConversationUpdate = (conversationId: string, patch: { conversationName?: string; conversationAvatar?: string }) => {
    dispatch(setConversations(conversations.map((c) => (c.id === conversationId ? { ...c, ...patch } : c))));
  };

  const handleOpenGroupMenu = (e: React.MouseEvent<HTMLElement>) => {
    setGroupMenuAnchor(e.currentTarget);
  };

  const handleCloseGroupMenu = () => {
    setGroupMenuAnchor(null);
  };

  const handleOpenRenameDialog = () => {
    if (activeConversation) {
      setRenameValue(getConversationName(activeConversation));
    }
    setRenameDialogOpen(true);
    handleCloseGroupMenu();
  };

  const handleConfirmRename = async () => {
    const name = renameValue.trim();
    if (!name || !activeConversationId) return;
    setRenamingGroup(true);
    try {
      const response = await chatService.updateConversation(activeConversationId, { conversationName: name });
      if (response.code === 1000) {
        applyConversationUpdate(activeConversationId, { conversationName: response.result.conversationName });
        toast.success('Đã đổi tên nhóm');
        setRenameDialogOpen(false);
      }
    } catch (error) {
      console.error('Failed to rename conversation:', error);
      toast.error('Không thể đổi tên nhóm');
    } finally {
      setRenamingGroup(false);
    }
  };

  const handleGroupAvatarClick = () => {
    groupAvatarInputRef.current?.click();
    handleCloseGroupMenu();
  };

  const handleGroupAvatarFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file || !activeConversationId) return;

    if (!file.type.startsWith('image/')) {
      toast.error('Vui lòng chọn tệp hình ảnh');
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      toast.error('Kích thước ảnh không được vượt quá 5MB');
      return;
    }

    setUploadingGroupAvatar(true);
    const toastId = toast.loading('Đang tải ảnh lên...');
    try {
      const uploadRes = await fileService.uploadFile(file);
      if (uploadRes.code === 1000) {
        const updateRes = await chatService.updateConversation(activeConversationId, { groupAvatarFileId: uploadRes.result.id });
        if (updateRes.code === 1000) {
          applyConversationUpdate(activeConversationId, { conversationAvatar: updateRes.result.conversationAvatar });
          toast.success('Đã đổi ảnh nhóm', { id: toastId });
        } else {
          toast.error(updateRes.message || 'Không thể cập nhật ảnh nhóm', { id: toastId });
        }
      } else {
        toast.error('Tải ảnh lên thất bại: ' + (uploadRes.message || ''), { id: toastId });
      }
    } catch (error) {
      console.error('Failed to update group avatar:', error);
      toast.error('Có lỗi xảy ra khi đổi ảnh nhóm', { id: toastId });
    } finally {
      setUploadingGroupAvatar(false);
    }
  };

  const getLastMessage = (conv: any) => {
    const msgs = messages[conv.id] || [];
    return msgs.length > 0 ? getMessagePreviewText(msgs[msgs.length - 1]) : 'No messages yet';
  };

  const activeConversation = conversations.find(c => c.id === activeConversationId);

  // Helper to get other participant's ID for direct chat
  const getOtherParticipantId = (conv: Conversation) => {
    return conv.userIds.find(id => id !== user?.id);
  };

  const isUserOnline = (userId?: string) => {
    return userId && onlineUsers.includes(userId);
  };

  const getMessageSeenAvatars = (messageId: string) => {
    if (!activeConversation?.participants) return [];
    return activeConversation.participants
      .filter((p: ConversationParticipant) => p.userId !== user?.id && p.lastSeenMessageId === messageId)
      .map((p: ConversationParticipant) => ({
        userId: p.userId,
        avatar: p.avatar,
        name: p.displayName
      }));
  };

  return (
    <>
    <Box
      sx={{
        height: 'calc(100vh - 120px)',
        bgcolor: 'background.default',
        borderRadius: 4,
        overflow: 'hidden',
        display: 'flex',
        border: '1px solid',
        borderColor: 'divider',
        boxShadow: isDarkMode ? '0 12px 40px rgba(0,0,0,0.35)' : '0 12px 40px rgba(0,0,0,0.1)',
      }}
    >
      {/* Sidebar */}
      <Box sx={{ width: 360, borderRight: '1px solid', borderColor: 'divider', display: 'flex', flexDirection: 'column', bgcolor: 'background.paper' }}>
        <Box sx={{ p: 2, pb: 1, display: 'flex', alignItems: 'center', gap: 1 }}>
          <TextField
            fullWidth
            size="small"
            placeholder="Search Messenger"
            variant="outlined"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            slotProps={{
              input: {
                startAdornment: (
                  <InputAdornment position="start">
                    <Search fontSize="small" sx={{ color: 'text.secondary' }} />
                  </InputAdornment>
                ),
                sx: {
                  borderRadius: '20px',
                  bgcolor: surfaceElevated,
                  color: 'text.primary',
                  '& fieldset': { border: 'none' },
                  '&:hover fieldset': { border: 'none' },
                  '&.Mui-focused fieldset': { border: 'none' },
                  height: '36px',
                  fontSize: '0.9rem',
                }
              },
            }}
          />
          <IconButton
            onClick={handleOpenNewConversation}
            title="Cuộc trò chuyện mới"
            sx={{
              flexShrink: 0,
              width: 36,
              height: 36,
              color: 'text.secondary',
              bgcolor: surfaceElevated,
              '&:hover': { bgcolor: surfaceElevatedHover, color: 'text.primary' },
            }}
          >
            <NewChatIcon fontSize="small" />
          </IconButton>
        </Box>

        <List sx={{ flexGrow: 1, overflowY: 'auto', p: 1 }}>
          {isSearchingMode && searching && (
            <Box sx={{ display: 'flex', justifyContent: 'center', py: 2 }}>
              <CircularProgress size={20} sx={{ color: 'text.secondary' }} />
            </Box>
          )}
          {isSearchingMode && !searching && displayedConversations.length === 0 && (
            <Box sx={{ textAlign: 'center', py: 4 }}>
              <Typography sx={{ color: 'text.secondary', fontSize: '0.9rem' }}>Không tìm thấy hội thoại</Typography>
            </Box>
          )}
          {displayedConversations.map((conv) => {
            const otherId = getOtherParticipantId(conv);
            const online = (conv as any).online || isUserOnline(otherId);
            const name = getConversationName(conv);
            const lastMsg = (conv as any).lastMessage || getLastMessage(conv);
            const isSelected = activeConversationId === conv.id;
            const convUnreadCount = unreadCounts[conv.id] || 0;
            const isUnread = convUnreadCount > 0;

            return (
              <ListItemButton
                key={conv.id}
                selected={isSelected}
                onClick={() => (isSearchingMode ? handleSelectSearchResult(conv) : dispatch(setActiveConversation(conv.id)))}
                sx={{
                  borderRadius: '8px',
                  mb: 0.5,
                  py: 1,
                  px: 1,
                  '&.Mui-selected': {
                    bgcolor: 'rgba(45, 136, 255, 0.1)',
                    '&:hover': { bgcolor: 'rgba(45, 136, 255, 0.15)' },
                  },
                  '&:hover': { bgcolor: 'rgba(255, 255, 255, 0.05)' },
                }}
              >
                <ListItemAvatar sx={{ minWidth: 60 }}>
                  <StyledBadge
                    overlap="circular"
                    anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
                    variant="dot"
                    invisible={!online}
                  >
                    <Avatar 
                      src={(conv as any).conversationAvatar}
                      sx={{ 
                        width: 50,
                        height: 50,
                        bgcolor: surfaceElevated,
                        border: isSelected ? '2px solid #2D88FF' : 'none'
                      }}
                    >
                      {!(conv as any).conversationAvatar && name[0]}
                    </Avatar>
                  </StyledBadge>
                </ListItemAvatar>
                <ListItemText 
                  primary={
                    <Typography 
                      sx={{ 
                        fontWeight: isUnread ? 700 : 500, 
                        color: 'text.primary',
                        fontSize: '0.95rem',
                        lineHeight: 1.2
                      }}
                    >
                      {name}
                    </Typography>
                  } 
                  secondary={
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                      <Typography 
                        sx={{ 
                          fontWeight: isUnread ? 700 : 400, 
                          color: isUnread ? 'text.primary' : 'text.secondary',
                          fontSize: '0.85rem',
                          maxWidth: '180px',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                          whiteSpace: 'nowrap'
                        }}
                      >
                        {lastMsg}
                      </Typography>
                      <Typography sx={{ color: 'text.secondary', fontSize: '0.8rem' }}>
                        · {(conv as any).time || '1d'}
                      </Typography>
                    </Box>
                  }
                />
                {isUnread && (
                  <Box
                    sx={{
                      minWidth: 20,
                      height: 20,
                      px: convUnreadCount > 9 ? 0.6 : 0,
                      bgcolor: '#2D88FF',
                      borderRadius: '10px',
                      ml: 1,
                      flexShrink: 0,
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                    }}
                  >
                    <Typography sx={{ fontSize: '0.7rem', fontWeight: 700, color: '#fff', lineHeight: 1 }}>
                      {convUnreadCount > 99 ? '99+' : convUnreadCount}
                    </Typography>
                  </Box>
                )}
              </ListItemButton>
            );
          })}
        </List>
      </Box>

      {/* Main Chat Area */}
      <Box sx={{ flexGrow: 1, display: 'flex', flexDirection: 'column', bgcolor: 'background.default' }}>
        {activeConversationId ? (
          <>
            {/* Header */}
            <Box
              sx={{
                p: 2,
                borderBottom: '1px solid',
                borderColor: 'divider',
                bgcolor: 'background.paper',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
              }}
            >
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                {isUserOnline(getOtherParticipantId(activeConversation!)) ? (
                  <StyledBadge
                    overlap="circular"
                    anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
                    variant="dot"
                  >
                    <Avatar src={(activeConversation as any)?.conversationAvatar} sx={{ bgcolor: 'primary.main' }}>{getConversationName(activeConversation!)[0]}</Avatar>
                  </StyledBadge>
                ) : (
                  <Avatar src={(activeConversation as any)?.conversationAvatar} sx={{ bgcolor: 'primary.main' }}>{getConversationName(activeConversation!)[0]}</Avatar>
                )}
                <Box>
                  <Typography variant="h6" sx={{ fontWeight: 700, color: 'text.primary' }}>{getConversationName(activeConversation!)}</Typography>
                  <Typography variant="caption" sx={{ display: 'block', mt: -0.25, color: isUserOnline(getOtherParticipantId(activeConversation!)) ? '#44b700' : 'text.secondary' }}>
                    {isUserOnline(getOtherParticipantId(activeConversation!)) ? 'Đang trực tuyến' : 'Ngoại tuyến'}
                  </Typography>
                </Box>
              </Box>
              {activeConversation?.type === 'GROUP' && (
                <IconButton
                  onClick={handleOpenGroupMenu}
                  sx={{ color: 'text.secondary', '&:hover': { bgcolor: 'action.hover', color: 'text.primary' } }}
                >
                  <MoreVert />
                </IconButton>
              )}
            </Box>

            {/* Messages */}
            <Box sx={{ position: 'relative', flexGrow: 1, minHeight: 0, display: 'flex' }}>
            <Box
              ref={messagesContainerRef}
              onScroll={handleMessagesScroll}
              sx={{
                flexGrow: 1,
                overflowY: 'auto',
                p: 3,
                display: 'flex',
                flexDirection: 'column',
                gap: 0.5,
                bgcolor: 'background.default',
                backgroundImage: 'radial-gradient(circle at 100% 0%, rgba(0,168,78,0.07), transparent 55%)',
                '&::-webkit-scrollbar': { width: 6 },
                '&::-webkit-scrollbar-thumb': {
                  bgcolor: isDarkMode ? 'rgba(255,255,255,0.15)' : 'rgba(0,0,0,0.15)',
                  borderRadius: 3,
                },
              }}
            >
              {loadingMessages && activeMessages.length === 0 ? (
                <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5, flexGrow: 1, justifyContent: 'flex-end' }}>
                  {Array.from({ length: MESSAGE_SKELETON_COUNT }).map((_, i) => (
                    <Box key={i} sx={{ display: 'flex', justifyContent: i % 3 === 0 ? 'flex-end' : 'flex-start' }}>
                      <Skeleton
                        variant="rounded"
                        width={100 + ((i * 37) % 120)}
                        height={36}
                        sx={{ borderRadius: '18px' }}
                      />
                    </Box>
                  ))}
                </Box>
              ) : (
              <>
              {pagination[activeConversationId]?.loadingMore && (
                <Box sx={{ display: 'flex', justifyContent: 'center', py: 1 }}>
                  <CircularProgress size={20} sx={{ color: 'text.secondary' }} />
                </Box>
              )}
              {activeMessages.map((msg, index) => {
                const isMe = msg.senderId === user?.id;
                const isDeleted = msg.messageType === 'DELETED_FOR_EVERYONE';
                const seenAvatars = getMessageSeenAvatars(msg.id);
                const nextMsg = activeMessages[index + 1];
                const isLastInGroup = !nextMsg || nextMsg.senderId !== msg.senderId;
                const senderAvatar = msg.senderAvatar;
                const senderInitial = (msg.senderName || getConversationName(activeConversation!) || '?')[0];
                // Media messages render edge-to-edge (no bubble background/padding around the
                // image/video itself), matching Messenger/Zalo/Telegram conventions.
                const isMediaOnly = (msg.messageType === 'IMAGE' || msg.messageType === 'VIDEO') && !isDeleted;
                const replyQuote = msg.replyToMessageId && !isDeleted ? (
                  <>
                    <Typography variant="caption" sx={{ fontWeight: 700, display: 'block' }}>
                      {msg.replyToSenderName || 'Người dùng'}
                    </Typography>
                    <Typography variant="caption" sx={{ display: 'block', wordBreak: 'break-word' }}>
                      {msg.replyToContent || getMessagePreviewText({ messageType: msg.replyToMessageType || 'TEXT' })}
                    </Typography>
                  </>
                ) : null;

                return (
                  <Box
                    key={msg.id}
                    sx={{
                      display: 'flex',
                      flexDirection: isMe ? 'row-reverse' : 'row',
                      alignItems: 'flex-end',
                      gap: 1,
                      animation: 'messageIn 0.2s ease-out',
                      '@keyframes messageIn': {
                        from: { opacity: 0, transform: 'translateY(6px)' },
                        to: { opacity: 1, transform: 'translateY(0)' },
                      },
                      '&:hover .message-actions': { opacity: 1 },
                    }}
                  >
                    {!isMe && (
                      <Avatar
                        src={senderAvatar}
                        sx={{
                          width: 28,
                          height: 28,
                          fontSize: '0.75rem',
                          bgcolor: surfaceElevated,
                          color: 'text.primary',
                          visibility: isLastInGroup ? 'visible' : 'hidden',
                        }}
                      >
                        {!senderAvatar && senderInitial}
                      </Avatar>
                    )}
                    <Box
                      sx={{
                        maxWidth: '65%',
                        display: 'flex',
                        flexDirection: 'column',
                        alignItems: isMe ? 'flex-end' : 'flex-start',
                      }}
                    >
                      <Box
                        sx={{ display: 'flex', alignItems: 'center', gap: 0.5, flexDirection: isMe ? 'row' : 'row-reverse' }}
                        onTouchStart={!isDeleted ? (e) => handleMessageTouchStart(e, msg) : undefined}
                        onTouchEnd={clearLongPressTimer}
                        onTouchMove={clearLongPressTimer}
                      >
                        {!isDeleted && (
                          <IconButton
                            className="message-actions"
                            size="small"
                            onClick={(e) => handleOpenMessageMenu(e.currentTarget, msg)}
                            sx={{ opacity: 0, transition: 'opacity 0.15s', width: 26, height: 26, color: 'text.secondary', '&:hover': { color: 'text.primary', bgcolor: 'action.hover' } }}
                          >
                            <MoreVert fontSize="small" />
                          </IconButton>
                        )}
                        {isMediaOnly ? (
                          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.5 }}>
                            {replyQuote && (
                              <Box
                                sx={{
                                  borderLeft: '3px solid',
                                  borderColor: 'divider',
                                  pl: 1,
                                  py: 0.5,
                                  pr: 1,
                                  borderRadius: '8px',
                                  bgcolor: 'action.hover',
                                  color: 'text.primary',
                                  opacity: 0.85,
                                }}
                              >
                                {replyQuote}
                              </Box>
                            )}
                            {renderMessageBody(msg)}
                          </Box>
                        ) : (
                          <Paper
                            elevation={0}
                            sx={{
                              py: 1,
                              px: 1.75,
                              borderRadius: '18px',
                              borderBottomRightRadius: isMe && isLastInGroup ? '4px' : '18px',
                              borderBottomLeftRadius: !isMe && isLastInGroup ? '4px' : '18px',
                              bgcolor: isMe ? 'primary.main' : surfaceElevated,
                              color: isMe ? '#fff' : 'text.primary',
                            }}
                          >
                            {replyQuote && (
                              <Box sx={{ borderLeft: '3px solid', borderColor: isMe ? 'rgba(255,255,255,0.4)' : 'divider', pl: 1, mb: 0.5, opacity: 0.85 }}>
                                {replyQuote}
                              </Box>
                            )}
                            {renderMessageBody(msg)}
                          </Paper>
                        )}
                      </Box>
                      {/* Timestamp only shows once per consecutive-sender group, but the seen
                          indicator is tied to whichever exact message each participant last read
                          (`getMessageSeenAvatars`) — that message isn't necessarily the last one
                          in a same-sender streak, so this row must render independently of
                          `isLastInGroup` or the avatar can silently never appear. */}
                      {(isLastInGroup || seenAvatars.length > 0) && (
                        <Box sx={{ display: 'flex', alignItems: 'center', mt: 0.5, gap: 1, px: 0.5 }}>
                          {isLastInGroup && (
                            <Typography variant="caption" sx={{ color: 'text.disabled' }}>
                              {new Date(msg.createdDate).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                            </Typography>
                          )}
                          {seenAvatars.length > 0 && (
                            <Box sx={{ display: 'flex' }}>
                              {seenAvatars.slice(0, SEEN_AVATARS_VISIBLE_LIMIT).map((p, i) => (
                                <Avatar
                                  key={p.userId}
                                  src={p.avatar}
                                  title={p.name}
                                  sx={{ width: 14, height: 14, border: '1px solid', borderColor: 'background.default', ml: i > 0 ? -0.75 : 0 }}
                                />
                              ))}
                              {seenAvatars.length > SEEN_AVATARS_VISIBLE_LIMIT && (
                                <Box
                                  title={seenAvatars.slice(SEEN_AVATARS_VISIBLE_LIMIT).map((p) => p.name).join(', ')}
                                  sx={{
                                    width: 14,
                                    height: 14,
                                    ml: -0.75,
                                    borderRadius: '50%',
                                    border: '1px solid',
                                    borderColor: 'background.default',
                                    bgcolor: surfaceElevated,
                                    color: 'text.secondary',
                                    fontSize: '0.5rem',
                                    fontWeight: 700,
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                  }}
                                >
                                  +{seenAvatars.length - SEEN_AVATARS_VISIBLE_LIMIT}
                                </Box>
                              )}
                            </Box>
                          )}
                        </Box>
                      )}
                    </Box>
                  </Box>
                );
              })}
              <div ref={scrollRef} />
              </>
              )}
            </Box>

            {hasNewMessagesBelow && (
              <Box
                onClick={handleScrollToBottomClick}
                sx={{
                  position: 'absolute',
                  left: '50%',
                  bottom: 16,
                  transform: 'translateX(-50%)',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 0.5,
                  px: 1.75,
                  py: 0.75,
                  borderRadius: '20px',
                  bgcolor: '#2D88FF',
                  color: '#fff',
                  fontSize: '0.8rem',
                  fontWeight: 600,
                  cursor: 'pointer',
                  boxShadow: isDarkMode ? '0 4px 12px rgba(0,0,0,0.35)' : '0 4px 12px rgba(0,0,0,0.15)',
                  userSelect: 'none',
                  '&:hover': { bgcolor: '#1877F2' },
                }}
              >
                <ArrowDownIcon fontSize="small" />
                Tin nhắn mới
              </Box>
            )}
            </Box>

            {/* Input */}
            <Box sx={{ borderTop: '1px solid', borderColor: 'divider', bgcolor: 'background.paper' }}>
              {(replyTarget || editingMessageId) && (
                <Box sx={{ px: 2, pt: 1.5, display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 1 }}>
                  <Box sx={{ display: 'flex', flexDirection: 'column', overflow: 'hidden', borderLeft: '3px solid', borderColor: 'primary.main', pl: 1 }}>
                    <Typography variant="caption" sx={{ color: 'primary.main', fontWeight: 700 }}>
                      {editingMessageId ? 'Đang chỉnh sửa tin nhắn' : `Trả lời ${replyTarget?.senderName || 'tin nhắn'}`}
                    </Typography>
                    {replyTarget && (
                      <Typography variant="caption" sx={{ color: 'text.secondary', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                        {getMessagePreviewText(replyTarget)}
                      </Typography>
                    )}
                  </Box>
                  <IconButton size="small" onClick={editingMessageId ? handleCancelEdit : handleCancelReply}>
                    <CloseIcon fontSize="small" sx={{ color: 'text.secondary' }} />
                  </IconButton>
                </Box>
              )}
              <Box sx={{ p: 2, display: 'flex', alignItems: 'center', gap: 1.5 }}>
                <IconButton
                  onClick={handleAttachmentButtonClick}
                  disabled={uploadingAttachment || !!editingMessageId}
                  sx={{ flexShrink: 0, color: 'text.secondary', '&:hover': { color: 'text.primary', bgcolor: 'action.hover' } }}
                >
                  {uploadingAttachment ? <CircularProgress size={18} sx={{ color: 'text.secondary' }} /> : <AttachFile />}
                </IconButton>
                <TextField
                  fullWidth
                  size="small"
                  placeholder="Nhập tin nhắn..."
                  value={inputText}
                  onChange={(e) => setInputText(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' && !e.shiftKey) {
                      e.preventDefault();
                      handleSendMessage();
                    }
                  }}
                  slotProps={{
                    input: {
                      sx: {
                        borderRadius: '22px',
                        bgcolor: surfaceElevated,
                        color: 'text.primary',
                        px: 1,
                        '& fieldset': { border: 'none' },
                      },
                    },
                  }}
                />
                <IconButton
                  onClick={handleSendMessage}
                  disabled={!inputText.trim()}
                  sx={{
                    width: 40,
                    height: 40,
                    flexShrink: 0,
                    bgcolor: inputText.trim() ? 'primary.main' : 'action.disabledBackground',
                    color: inputText.trim() ? '#fff' : 'text.secondary',
                    transition: 'all 0.2s ease',
                    '&:hover': { bgcolor: 'primary.dark' },
                    '&.Mui-disabled': { color: 'text.disabled' },
                  }}
                >
                  <Send fontSize="small" />
                </IconButton>
              </Box>
            </Box>
          </>
        ) : (
          <Box
            sx={{
              flexGrow: 1,
              display: 'flex',
              flexDirection: 'column',
              justifyContent: 'center',
              alignItems: 'center',
              bgcolor: 'background.default',
              backgroundImage: 'radial-gradient(circle at 50% 40%, rgba(0,168,78,0.08), transparent 60%)',
            }}
          >
            <ChatIcon sx={{ fontSize: 72, mb: 2, opacity: 0.3, color: 'primary.main' }} />
            <Typography variant="h5" sx={{ color: 'text.primary', fontWeight: 700, mb: 0.5 }}>
              Chọn một hội thoại để bắt đầu
            </Typography>
            <Typography variant="body2" sx={{ color: 'text.disabled' }}>
              Tin nhắn của bạn sẽ hiện ở đây
            </Typography>
          </Box>
        )}
      </Box>
    </Box>

    <Dialog open={newConvOpen} onClose={handleCloseNewConversation} fullWidth maxWidth="xs">
      <DialogTitle>Cuộc trò chuyện mới</DialogTitle>
      <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
        <TextField
          autoFocus
          fullWidth
          size="small"
          placeholder="Tìm bạn bè..."
          value={friendQuery}
          onChange={(e) => setFriendQuery(e.target.value)}
          slotProps={{
            input: {
              startAdornment: (
                <InputAdornment position="start">
                  <Search fontSize="small" />
                </InputAdornment>
              ),
            },
          }}
        />
        {friendsLoading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 3 }}>
            <CircularProgress size={24} />
          </Box>
        ) : friendOptions.length === 0 ? (
          <Typography variant="body2" color="text.secondary" sx={{ textAlign: 'center', py: 3 }}>
            Không tìm thấy bạn bè
          </Typography>
        ) : (
          <List sx={{ maxHeight: 320, overflowY: 'auto' }}>
            {friendOptions.map((f) => (
              <ListItemButton key={f.id} onClick={() => toggleSelectedFriend(f.id)} sx={{ borderRadius: 1 }}>
                <Checkbox checked={selectedFriendIds.includes(f.id)} tabIndex={-1} disableRipple />
                <ListItemAvatar>
                  <Avatar src={f.avatar}>{!f.avatar && f.displayName[0]}</Avatar>
                </ListItemAvatar>
                <ListItemText primary={f.displayName} />
              </ListItemButton>
            ))}
          </List>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={handleCloseNewConversation} disabled={creatingConversation}>Hủy</Button>
        <Button
          variant="contained"
          disabled={selectedFriendIds.length === 0 || creatingConversation}
          onClick={handleCreateConversation}
        >
          {creatingConversation ? <CircularProgress size={18} sx={{ color: '#fff' }} /> : 'Tạo'}
        </Button>
      </DialogActions>
    </Dialog>

    <Menu anchorEl={groupMenuAnchor} open={Boolean(groupMenuAnchor)} onClose={handleCloseGroupMenu}>
      <MenuItem onClick={handleOpenRenameDialog}>
        <ListItemIcon><EditIcon fontSize="small" /></ListItemIcon>
        Đổi tên nhóm
      </MenuItem>
      <MenuItem onClick={handleGroupAvatarClick} disabled={uploadingGroupAvatar}>
        <ListItemIcon><PhotoCamera fontSize="small" /></ListItemIcon>
        Đổi ảnh nhóm
      </MenuItem>
    </Menu>

    <Menu anchorEl={messageMenuAnchor} open={Boolean(messageMenuAnchor)} onClose={handleCloseMessageMenu}>
      <MenuItem onClick={handleReplyClick}>
        <ListItemIcon><ReplyIcon fontSize="small" /></ListItemIcon>
        Trả lời
      </MenuItem>
      {selectedMessage?.messageType === 'TEXT' && (
        <MenuItem onClick={handleCopyMessageClick}>
          <ListItemIcon><CopyIcon fontSize="small" /></ListItemIcon>
          Sao chép
        </MenuItem>
      )}
      {selectedMessage?.senderId === user?.id && selectedMessage?.messageType === 'TEXT' && (
        <MenuItem onClick={handleEditMessageClick}>
          <ListItemIcon><EditIcon fontSize="small" /></ListItemIcon>
          Sửa
        </MenuItem>
      )}
      {selectedMessage?.senderId === user?.id && (
        <MenuItem onClick={handleDeleteMessageClick} sx={{ color: 'error.main' }}>
          <ListItemIcon><DeleteIcon fontSize="small" color="error" /></ListItemIcon>
          Thu hồi
        </MenuItem>
      )}
    </Menu>

    <input
      type="file"
      ref={attachmentInputRef}
      style={{ display: 'none' }}
      onChange={handleAttachmentFileChange}
    />

    <input
      type="file"
      accept="image/*"
      ref={groupAvatarInputRef}
      style={{ display: 'none' }}
      onChange={handleGroupAvatarFileChange}
    />

    <Dialog open={renameDialogOpen} onClose={() => !renamingGroup && setRenameDialogOpen(false)} fullWidth maxWidth="xs">
      <DialogTitle>Đổi tên nhóm</DialogTitle>
      <DialogContent>
        <TextField
          autoFocus
          fullWidth
          size="small"
          margin="dense"
          placeholder="Tên nhóm"
          value={renameValue}
          onChange={(e) => setRenameValue(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              e.preventDefault();
              handleConfirmRename();
            }
          }}
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={() => setRenameDialogOpen(false)} disabled={renamingGroup}>Hủy</Button>
        <Button
          variant="contained"
          disabled={!renameValue.trim() || renamingGroup}
          onClick={handleConfirmRename}
        >
          {renamingGroup ? <CircularProgress size={18} sx={{ color: '#fff' }} /> : 'Lưu'}
        </Button>
      </DialogActions>
    </Dialog>
    </>
  );
});

export default ChatPage;
