 import React, { useState, useEffect, useRef, useCallback } from 'react';
import {
  Box,
  Typography,
  List,
  ListItemButton,
  ListItemAvatar,
  ListItemText,
  Avatar,
  TextField,
  InputAdornment,
  IconButton,
  Badge,
  styled,
  Paper,
} from '@mui/material';
import { Search, Send, MoreVert, Chat as ChatIcon } from '@mui/icons-material';
import { useDispatch, useSelector } from 'react-redux';
import { type RootState } from '../store/index';
import { setConversations, setActiveConversation, addMessage, setMessages, updateUserStatus, updateMessageSeen } from '../store';
import type { Conversation, ConversationType } from '../models';
import { chatService } from '../api/chatService';

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

const ChatPage: React.FC = () => {
  const dispatch = useDispatch();
  const { user } = useSelector((state: RootState) => state.auth);

  const { conversations, activeConversationId, messages, onlineUsers } = useSelector((state: RootState) => state.chat);
  
  const [inputText, setInputText] = useState('');
  const socketRef = useRef<WebSocket | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);

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

  const fetchMessages = useCallback(async (conversationId: string) => {
    try {
      const response = await chatService.getMyChatMessages(conversationId, 1, 50);
      if (response.code === 1000) {
        dispatch(setMessages({ 
          conversationId, 
          messages: response.result.data as any
        }));
      }
    } catch (error) {
      console.error('Failed to fetch messages:', error);
    }
  }, [dispatch]);

  useEffect(() => {
    fetchConversations();
  }, [fetchConversations]);

  useEffect(() => {
    if (activeConversationId) {
      fetchMessages(activeConversationId);
    }
  }, [activeConversationId, fetchMessages]);

  useEffect(() => {
    const socketUrl = import.meta.env.VITE_SOCKET_URL || 'ws://localhost:8088/ws';
    const token = localStorage.getItem('token');

    if (!token) return;

    const wsUrl = `${socketUrl}?token=${token}`;
    const ws = new WebSocket(wsUrl);
    socketRef.current = ws;

    ws.onopen = () => {
      console.log('Connected to WebSocket server');
    };

    ws.onmessage = (event) => {
      try {
        const payload = JSON.parse(event.data);
        const { event: eventType, data } = payload;

        switch (eventType) {
          case 'new-message':
            dispatch(addMessage({ conversationId: data.conversationId, message: data }));
            break;
          case 'user-status-changed':
            dispatch(updateUserStatus(data));
            break;
          case 'seen-message':
            dispatch(updateMessageSeen(data));
            break;
          default:
            console.log('Received unknown event:', eventType, data);
        }
      } catch (error) {
        console.error('Failed to parse WebSocket message:', error);
      }
    };

    ws.onerror = (error) => {
      console.error('WebSocket error:', error);
    };

    ws.onclose = () => {
      console.log('Disconnected from WebSocket server');
    };

    return () => {
      ws.close();
    };
  }, [dispatch]);

  useEffect(() => {
    scrollRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, activeConversationId]);

  const handleSendMessage = () => {
    if (inputText.trim() && activeConversationId && socketRef.current?.readyState === WebSocket.OPEN) {
      socketRef.current.send(JSON.stringify({
        type: 'send-message',
        data: {
          conversationId: activeConversationId,
          content: inputText,
          messageType: 'TEXT'
        }
      }));
      setInputText('');
    }
  };

  const getConversationName = (conv: any) => {
    if (conv.type === 'GROUP') return conv.groupName;
    // For direct chat, we would typically find the other user's name
    // Mocking it for now
    if (conv.id === '2') return 'Nguyễn Văn A';
    if (conv.id === '3') return 'Trần Thị B';
    return 'Unknown';
  };

  const getLastMessage = (conv: any) => {
    const msgs = messages[conv.id] || [];
    return msgs.length > 0 ? msgs[msgs.length - 1].content : 'No messages yet';
  };

  const activeMessages = activeConversationId ? messages[activeConversationId] || [] : [];
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
      .filter(p => p.userId !== user?.id && p.lastSeenMessageId === messageId)
      .map(p => ({
        userId: p.userId,
        avatar: p.avatar,
        name: p.displayName
      }));
  };

  return (
    <Box sx={{ height: 'calc(100vh - 120px)', bgcolor: '#000000', borderRadius: 4, overflow: 'hidden', display: 'flex' }}>
      {/* Sidebar */}
      <Box sx={{ width: 360, borderRight: '1px solid rgba(255,255,255,0.05)', display: 'flex', flexDirection: 'column', bgcolor: '#1C1C1D' }}>
        <Box sx={{ p: 2, pb: 1 }}>
          <TextField
            fullWidth
            size="small"
            placeholder="Search Messenger"
            variant="outlined"
            slotProps={{
              input: {
                startAdornment: (
                  <InputAdornment position="start">
                    <Search fontSize="small" sx={{ color: '#8E8E93' }} />
                  </InputAdornment>
                ),
                sx: {
                  borderRadius: '20px',
                  bgcolor: '#3A3B3C',
                  color: '#fff',
                  '& fieldset': { border: 'none' },
                  '&:hover fieldset': { border: 'none' },
                  '&.Mui-focused fieldset': { border: 'none' },
                  height: '36px',
                  fontSize: '0.9rem',
                }
              },
            }}
          />
        </Box>

        <List sx={{ flexGrow: 1, overflowY: 'auto', p: 1 }}>
          {conversations.map((conv) => {
            const otherId = getOtherParticipantId(conv);
            const online = (conv as any).online || isUserOnline(otherId);
            const name = getConversationName(conv);
            const lastMsg = (conv as any).lastMessage || getLastMessage(conv);
            const isSelected = activeConversationId === conv.id;
            const isUnread = (conv as any).unread;
            
            return (
              <ListItemButton 
                key={conv.id}
                selected={isSelected}
                onClick={() => dispatch(setActiveConversation(conv.id))}
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
                      src={(conv as any).avatar}
                      sx={{ 
                        width: 50, 
                        height: 50, 
                        bgcolor: '#3A3B3C',
                        border: isSelected ? '2px solid #2D88FF' : 'none'
                      }}
                    >
                      {!(conv as any).avatar && name[0]}
                    </Avatar>
                  </StyledBadge>
                </ListItemAvatar>
                <ListItemText 
                  primary={
                    <Typography 
                      sx={{ 
                        fontWeight: isUnread ? 700 : 500, 
                        color: '#E4E6EB',
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
                          color: isUnread ? '#E4E6EB' : '#B0B3B8',
                          fontSize: '0.85rem',
                          maxWidth: '180px',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                          whiteSpace: 'nowrap'
                        }}
                      >
                        {lastMsg}
                      </Typography>
                      <Typography sx={{ color: '#B0B3B8', fontSize: '0.8rem' }}>
                        · {(conv as any).time || '1d'}
                      </Typography>
                    </Box>
                  }
                />
                {isUnread && (
                  <Box 
                    sx={{ 
                      width: 12, 
                      height: 12, 
                      bgcolor: '#2D88FF', 
                      borderRadius: '50%',
                      ml: 1
                    }} 
                  />
                )}
              </ListItemButton>
            );
          })}
        </List>
      </Box>

      {/* Main Chat Area */}
      <Box sx={{ flexGrow: 1, display: 'flex', flexDirection: 'column', bgcolor: '#000000' }}>
        {activeConversationId ? (
          <>
            {/* Header */}
            <Box sx={{ p: 2, borderBottom: '1px solid', borderColor: 'divider', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                {isUserOnline(getOtherParticipantId(activeConversation!)) ? (
                  <StyledBadge
                    overlap="circular"
                    anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
                    variant="dot"
                  >
                    <Avatar sx={{ bgcolor: 'primary.main' }}>{getConversationName(activeConversation!)[0]}</Avatar>
                  </StyledBadge>
                ) : (
                  <Avatar sx={{ bgcolor: 'primary.main' }}>{getConversationName(activeConversation!)[0]}</Avatar>
                )}
                <Box>
                  <Typography variant="h6" sx={{ fontWeight: 'bold' }}>{getConversationName(activeConversation!)}</Typography>
                  {isUserOnline(getOtherParticipantId(activeConversation!)) && (
                    <Typography variant="caption" color="success.main" sx={{ display: 'block', mt: -0.5 }}>
                      Đang trực tuyến
                    </Typography>
                  )}
                </Box>
              </Box>
              <IconButton><MoreVert /></IconButton>
            </Box>

            {/* Messages */}
            <Box sx={{ flexGrow: 1, overflowY: 'auto', p: 3, display: 'flex', flexDirection: 'column', gap: 2, bgcolor: '#f5f7fa' }}>
              {activeMessages.map((msg) => {
                const seenAvatars = getMessageSeenAvatars(msg.id);
                return (
                  <Box 
                    key={msg.id} 
                    sx={{ 
                      alignSelf: msg.senderId === user?.id ? 'flex-end' : 'flex-start',
                      maxWidth: '70%',
                      display: 'flex',
                      flexDirection: 'column',
                      alignItems: msg.senderId === user?.id ? 'flex-end' : 'flex-start',
                    }}
                  >
                    <Paper 
                      sx={{ 
                        p: 1.5, 
                        borderRadius: 3,
                        bgcolor: msg.senderId === user?.id ? 'primary.main' : 'white',
                        color: msg.senderId === user?.id ? 'white' : 'text.primary',
                        boxShadow: '0 2px 4px rgba(0,0,0,0.05)',
                      }}
                    >
                      <Typography variant="body1">{msg.content}</Typography>
                    </Paper>
                    <Box sx={{ display: 'flex', alignItems: 'center', mt: 0.5, gap: 1 }}>
                      <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                        {new Date(msg.createdDate).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                      </Typography>
                      {seenAvatars.length > 0 && (
                        <Box sx={{ display: 'flex', gap: -0.5, ml: 1 }}>
                          {seenAvatars.map(p => (
                            <Avatar 
                              key={p.userId} 
                              src={p.avatar} 
                              title={p.name}
                              sx={{ width: 14, height: 14, border: '1px solid #fff' }}
                            />
                          ))}
                        </Box>
                      )}
                    </Box>
                  </Box>
                );
              })}
              <div ref={scrollRef} />
            </Box>

            {/* Input */}
            <Box sx={{ p: 2, borderTop: '1px solid', borderColor: 'divider', display: 'flex', gap: 1 }}>
              <TextField
                fullWidth
                placeholder="Nhập tin nhắn..."
                value={inputText}
                onChange={(e) => setInputText(e.target.value)}
                onKeyPress={(e) => e.key === 'Enter' && handleSendMessage()}
              />
              <IconButton color="primary" onClick={handleSendMessage} disabled={!inputText.trim()}>
                <Send />
              </IconButton>
            </Box>
          </>
        ) : (
          <Box sx={{ flexGrow: 1, display: 'flex', flexDirection: 'column', justifyContent: 'center', alignItems: 'center', bgcolor: '#f5f7fa', color: 'text.secondary' }}>
            <ChatIcon sx={{ fontSize: 80, mb: 2, opacity: 0.2 }} />
            <Typography variant="h5">Chọn một hội thoại để bắt đầu</Typography>
          </Box>
        )}
      </Box>
    </Box>
  );
};

export default ChatPage;
