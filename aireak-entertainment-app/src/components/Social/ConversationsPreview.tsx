import React from 'react';
import {
  Avatar,
  Badge,
  Box,
  Button,
  List,
  ListItemAvatar,
  ListItemButton,
  ListItemText,
  Paper,
  Skeleton,
  Typography,
} from '@mui/material';
import { Chat, ChevronRight } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import type { ConversationResponse } from '../../models';
import { formatRelativeTime } from '../../utils/time';

interface ConversationsPreviewProps {
  conversations: ConversationResponse[];
  unreadByConversation: Record<string, number>;
  loading: boolean;
  onOpenConversation: (conversationId: string) => void;
}

const ConversationsPreview: React.FC<ConversationsPreviewProps> = React.memo(
  ({ conversations, unreadByConversation, loading, onOpenConversation }) => {
    const { t, i18n } = useTranslation();
    const navigate = useNavigate();

    return (
      <Paper
        elevation={0}
        sx={{ p: 3, borderRadius: 3, bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider', height: '100%' }}
      >
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <Chat color="primary" fontSize="small" />
            <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
              {t('recentConversations')}
            </Typography>
          </Box>
          <Button size="small" onClick={() => navigate('/social/chat')} endIcon={<ChevronRight fontSize="small" />}>
            {t('viewAllConversations')}
          </Button>
        </Box>

        {loading ? (
          <Box sx={{ py: 1 }}>
            {[0, 1, 2].map((i) => (
              <Box key={i} sx={{ display: 'flex', alignItems: 'center', gap: 2, py: 1 }}>
                <Skeleton variant="circular" width={40} height={40} />
                <Box sx={{ flex: 1 }}>
                  <Skeleton variant="text" width="50%" />
                  <Skeleton variant="text" width="80%" />
                </Box>
              </Box>
            ))}
          </Box>
        ) : conversations.length === 0 ? (
          <Typography variant="body2" color="text.secondary" sx={{ py: 3, textAlign: 'center' }}>
            {t('noConversationsYet')}
          </Typography>
        ) : (
          <List sx={{ p: 0 }}>
            {conversations.map((conv) => {
              const unread = unreadByConversation[conv.id] || 0;
              const otherParticipant = conv.participants?.[0];
              const title = conv.conversationName || otherParticipant?.displayName || t('chat');
              const avatar = conv.conversationAvatar || otherParticipant?.avatar;

              return (
                <ListItemButton
                  key={conv.id}
                  onClick={() => onOpenConversation(conv.id)}
                  sx={{ borderRadius: 2, px: 1, mb: 0.5 }}
                >
                  <ListItemAvatar>
                    <Badge
                      badgeContent={unread}
                      color="error"
                      max={9}
                      overlap="circular"
                      anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
                    >
                      <Avatar src={avatar} slotProps={{ img: { loading: 'lazy' } }}>{title?.[0]?.toUpperCase()}</Avatar>
                    </Badge>
                  </ListItemAvatar>
                  <ListItemText
                    primary={
                      <Typography variant="body2" noWrap sx={{ fontWeight: unread > 0 ? 700 : 500 }}>
                        {title}
                      </Typography>
                    }
                    secondary={
                      <Typography variant="caption" color="text.secondary" noWrap component="span">
                        {conv.lastMessage || '—'}
                      </Typography>
                    }
                  />
                  {conv.modifiedDate && (
                    <Typography variant="caption" color="text.secondary" sx={{ whiteSpace: 'nowrap', ml: 1 }}>
                      {formatRelativeTime(conv.modifiedDate, i18n.language)}
                    </Typography>
                  )}
                </ListItemButton>
              );
            })}
          </List>
        )}
      </Paper>
    );
  }
);

ConversationsPreview.displayName = 'ConversationsPreview';

export default ConversationsPreview;
