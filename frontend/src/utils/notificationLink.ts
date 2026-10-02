import type { NotificationResponse } from '../models';

// Where clicking a notification in the list should take the user, or null if it has nowhere to go.
export const getNotificationLink = (notification: Pick<NotificationResponse, 'type' | 'roomId'>): string | null =>
  notification.type === 'WATCH_ROOM_INVITE' && notification.roomId
    ? `/film/watch-together/room/${notification.roomId}`
    : null;
