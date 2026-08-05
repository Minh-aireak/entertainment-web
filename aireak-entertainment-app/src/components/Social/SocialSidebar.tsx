import React, { useCallback, useEffect, useState } from 'react';
import { Box, Stack } from '@mui/material';
import { useDispatch, useSelector } from 'react-redux';
import { useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { useTranslation } from 'react-i18next';

import { type RootState, setActiveConversation, setProfileData, setProfileLoading } from '../../store';
import { profileService } from '../../api/profileService';
import { friendService } from '../../api/friendService';
import { chatService } from '../../api/chatService';
import { notificationService } from '../../api/notificationService';
import { REALTIME_FRIENDSHIP_EVENT, REALTIME_NOTIFICATION_EVENT } from '../../contexts/WebSocketContext';
import type { ConversationResponse, FriendRequestResponse, NotificationResponse, UserRelationshipResponse } from '../../models';

import ProfileSummaryCard from './ProfileSummaryCard';
import FriendRequestsCard from './FriendRequestsCard';
import FriendsPreviewCard from './FriendsPreviewCard';
import ConversationsPreview from './ConversationsPreview';
import NotificationsPreview from './NotificationsPreview';

const SocialSidebar: React.FC = () => {
  const { t } = useTranslation();
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const { profileData, loading: profileLoading } = useSelector((state: RootState) => state.profile);

  const [friends, setFriends] = useState<UserRelationshipResponse[]>([]);
  const [totalFriends, setTotalFriends] = useState(0);
  const [friendRequests, setFriendRequests] = useState<FriendRequestResponse[]>([]);
  const [conversations, setConversations] = useState<ConversationResponse[]>([]);
  const [unreadByConversation, setUnreadByConversation] = useState<Record<string, number>>({});
  const [notifications, setNotifications] = useState<NotificationResponse[]>([]);
  const [unreadNotifications, setUnreadNotifications] = useState(0);
  const [loadingSections, setLoadingSections] = useState(true);

  useEffect(() => {
    if (profileData || profileLoading) return;
    dispatch(setProfileLoading(true));
    profileService
      .getUserSummary()
      .then((res) => {
        if (res.code === 1000 && res.result) dispatch(setProfileData(res.result));
      })
      .catch((error) => console.error('Failed to load profile summary:', error))
      .finally(() => dispatch(setProfileLoading(false)));
    // Runs once on mount; re-fetching is gated by profileData/profileLoading already being set.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const fetchSidebarData = useCallback(async () => {
    setLoadingSections(true);
    const results = await Promise.allSettled([
      friendService.getMyFriends(1, 6),
      friendService.getMyFriendRequests(1, 5),
      chatService.getMyConversations(1, 5),
      chatService.getUnreadCount(),
      notificationService.getMyNotifications(1, 5),
      notificationService.getUnreadCount(),
    ]);

    const [friendsRes, requestsRes, conversationsRes, unreadRes, notificationsRes, unreadCountRes] = results;

    if (friendsRes.status === 'fulfilled' && friendsRes.value.code === 1000) {
      setFriends(friendsRes.value.result?.data ?? []);
      setTotalFriends(friendsRes.value.result?.totalElement ?? 0);
    } else if (friendsRes.status === 'rejected') {
      console.error('Failed to load friends:', friendsRes.reason);
    }

    if (requestsRes.status === 'fulfilled' && requestsRes.value.code === 1000) {
      const pending = (requestsRes.value.result?.data ?? []).filter((r) => r.status === 'PENDING');
      setFriendRequests(pending);
    } else if (requestsRes.status === 'rejected') {
      console.error('Failed to load friend requests:', requestsRes.reason);
    }

    if (conversationsRes.status === 'fulfilled' && conversationsRes.value.code === 1000) {
      setConversations(conversationsRes.value.result?.data ?? []);
    } else if (conversationsRes.status === 'rejected') {
      console.error('Failed to load conversations:', conversationsRes.reason);
    }

    if (unreadRes.status === 'fulfilled' && unreadRes.value.code === 1000) {
      setUnreadByConversation(unreadRes.value.result?.data ?? {});
    } else if (unreadRes.status === 'rejected') {
      console.error('Failed to load unread counts:', unreadRes.reason);
    }

    if (notificationsRes.status === 'fulfilled' && notificationsRes.value.code === 1000) {
      setNotifications(notificationsRes.value.result?.data ?? []);
    } else if (notificationsRes.status === 'rejected') {
      console.error('Failed to load notifications:', notificationsRes.reason);
    }

    if (unreadCountRes.status === 'fulfilled' && unreadCountRes.value.code === 1000) {
      setUnreadNotifications(unreadCountRes.value.result ?? 0);
    } else if (unreadCountRes.status === 'rejected') {
      console.error('Failed to load unread notification count:', unreadCountRes.reason);
    }

    setLoadingSections(false);
  }, []);

  useEffect(() => {
    fetchSidebarData();
  }, [fetchSidebarData]);

  useEffect(() => {
    const refresh = () => fetchSidebarData();
    window.addEventListener(REALTIME_FRIENDSHIP_EVENT, refresh);
    window.addEventListener(REALTIME_NOTIFICATION_EVENT, refresh);
    window.addEventListener('sidebar-counts:refresh', refresh);
    return () => {
      window.removeEventListener(REALTIME_FRIENDSHIP_EVENT, refresh);
      window.removeEventListener(REALTIME_NOTIFICATION_EVENT, refresh);
      window.removeEventListener('sidebar-counts:refresh', refresh);
    };
  }, [fetchSidebarData]);

  const handleAcceptRequest = useCallback(async (senderId: string) => {
    setFriendRequests((prev) => prev.filter((r) => r.senderId !== senderId));
    try {
      const res = await friendService.friendRequestStatus(senderId, 'ACCEPTED');
      if (res.code === 1000) {
        toast.success(t('friendRequestAccepted'));
        window.dispatchEvent(new Event('sidebar-counts:refresh'));
      }
    } catch (error) {
      console.error('Failed to accept friend request:', error);
      toast.error(t('actionFailed'));
      fetchSidebarData();
    }
  }, [t, fetchSidebarData]);

  const handleDeclineRequest = useCallback(async (senderId: string) => {
    setFriendRequests((prev) => prev.filter((r) => r.senderId !== senderId));
    try {
      const res = await friendService.friendRequestStatus(senderId, 'CANCEL');
      if (res.code === 1000) {
        toast.success(t('friendRequestDeclined'));
        window.dispatchEvent(new Event('sidebar-counts:refresh'));
      }
    } catch (error) {
      console.error('Failed to decline friend request:', error);
      toast.error(t('actionFailed'));
      fetchSidebarData();
    }
  }, [t, fetchSidebarData]);

  const handleOpenConversation = useCallback((conversationId: string) => {
    dispatch(setActiveConversation(conversationId));
    navigate('/social/chat');
  }, [dispatch, navigate]);

  const handleMarkAllRead = useCallback(async () => {
    const previous = notifications;
    setNotifications((prev) => prev.map((n) => ({ ...n, read: true })));
    setUnreadNotifications(0);
    try {
      const res = await notificationService.markAllAsRead();
      if (res.code === 1000) {
        toast.success(t('notificationsMarkedRead'));
        window.dispatchEvent(new Event('sidebar-counts:refresh'));
      }
    } catch (error) {
      console.error('Failed to mark notifications as read:', error);
      toast.error(t('actionFailed'));
      setNotifications(previous);
      fetchSidebarData();
    }
  }, [notifications, t, fetchSidebarData]);

  return (
    <Box
      component="aside"
      sx={{
        position: { md: 'sticky' },
        top: { md: 96 },
        alignSelf: 'flex-start',
        maxHeight: { md: 'calc(100vh - 112px)' },
        overflowY: { md: 'auto' },
        pr: { md: 0.5 },
      }}
    >
      <Stack spacing={2}>
        <ProfileSummaryCard summary={profileData} loading={profileLoading} />
        <FriendRequestsCard
          requests={friendRequests}
          loading={loadingSections}
          onAccept={handleAcceptRequest}
          onDecline={handleDeclineRequest}
        />
        <NotificationsPreview
          notifications={notifications}
          unreadCount={unreadNotifications}
          loading={loadingSections}
          onMarkAllRead={handleMarkAllRead}
        />
        <ConversationsPreview
          conversations={conversations}
          unreadByConversation={unreadByConversation}
          loading={loadingSections}
          onOpenConversation={handleOpenConversation}
        />
        <FriendsPreviewCard friends={friends} totalFriends={totalFriends} loading={loadingSections} />
      </Stack>
    </Box>
  );
};

export default SocialSidebar;
