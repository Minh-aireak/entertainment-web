package com.MyProject.socket_service.service;

/**
 * Keeps watch-together traffic in a socket namespace that generic chat/comment join-room
 * messages cannot enter. Only join-watch-room is allowed to resolve to this channel name.
 */
final class RoomChannelNames {
    static final String WATCH_ROOM_PREFIX = "watch-room:";
    static final String WATCH_TOGETHER_LOBBY = "watch-together-lobby";

    private RoomChannelNames() {
    }

    static String watchRoom(String roomId) {
        return WATCH_ROOM_PREFIX + roomId;
    }

    static boolean isWatchRoom(String channelId) {
        return channelId != null && channelId.startsWith(WATCH_ROOM_PREFIX);
    }

    static String externalRoomId(String channelId) {
        return isWatchRoom(channelId) ? channelId.substring(WATCH_ROOM_PREFIX.length()) : channelId;
    }
}
