import { describe, expect, it } from 'vitest';
import { getNotificationLink } from './notificationLink';

describe('getNotificationLink', () => {
  it('opens the watch-together room a stored invite points to', () => {
    expect(getNotificationLink({ type: 'WATCH_ROOM_INVITE', roomId: 'room-1' }))
      .toBe('/film/watch-together/room/room-1');
  });

  it('has nowhere to go for an invite without a room or for other notification types', () => {
    expect(getNotificationLink({ type: 'WATCH_ROOM_INVITE' })).toBeNull();
    expect(getNotificationLink({ type: 'FRIEND_REQUEST', roomId: 'room-1' })).toBeNull();
  });
});
