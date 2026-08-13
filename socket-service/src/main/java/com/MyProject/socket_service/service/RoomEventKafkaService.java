package com.MyProject.socket_service.service;

import com.MyProject.socket_service.dto.event.RoomClosedEvent;
import com.MyProject.socket_service.dto.event.RoomParticipantChangedEvent;
import com.MyProject.socket_service.dto.event.RoomPlaybackChangedEvent;
import com.MyProject.socket_service.dto.response.RoomListItemResponse;
import com.MyProject.socket_service.dto.response.RoomMessageResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

/** Watch-together events use a token-protected internal socket namespace, separate from the
 *  generic join-room channels used by chat and comments.
 *
 *  The lobby is a second, fixed WS room every client browsing the watch-together page joins
 *  (see FilmWatchTogether.tsx) so the public room list can update live instead of needing a
 *  manual refresh - the string must match exactly on the frontend's join-room call. */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoomEventKafkaService {
    CustomWebSocketHandler webSocketSessionService;
    ObjectMapper objectMapper;

    @KafkaListener(topics = "room.playback.updated")
    public void consumePlaybackUpdated(String payload, Acknowledgment ack) {
        try {
            RoomPlaybackChangedEvent event = objectMapper.readValue(payload, RoomPlaybackChangedEvent.class);
            webSocketSessionService.sendToRoom(RoomChannelNames.watchRoom(event.getRoomId()), "room:playback", event);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process room playback event", e);
            throw new RuntimeException(e);
        }
    }

    @KafkaListener(topics = "room.participant.changed")
    public void consumeParticipantChanged(String payload, Acknowledgment ack) {
        try {
            RoomParticipantChangedEvent event = objectMapper.readValue(payload, RoomParticipantChangedEvent.class);
            webSocketSessionService.sendToRoom(RoomChannelNames.watchRoom(event.getRoomId()), "room:participants", event);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process room participant event", e);
            throw new RuntimeException(e);
        }
    }

    @KafkaListener(topics = "room.message.created")
    public void consumeMessageCreated(String payload, Acknowledgment ack) {
        try {
            RoomMessageResponse message = objectMapper.readValue(payload, RoomMessageResponse.class);
            webSocketSessionService.sendToRoom(RoomChannelNames.watchRoom(message.getRoomId()), "room:message", message);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process room message event", e);
            throw new RuntimeException(e);
        }
    }

    @KafkaListener(topics = "room.closed")
    public void consumeRoomClosed(String payload, Acknowledgment ack) {
        try {
            RoomClosedEvent event = objectMapper.readValue(payload, RoomClosedEvent.class);
            webSocketSessionService.sendToRoom(RoomChannelNames.watchRoom(event.getRoomId()), "room:closed", event);
            // Also tell the lobby so a public room that just closed disappears from the list in
            // realtime. Harmless no-op on the frontend for a room it never had listed (private
            // rooms, or a room nobody in the lobby had loaded) - the payload carries nothing
            // beyond an opaque roomId + reason.
            webSocketSessionService.sendToRoom(RoomChannelNames.WATCH_TOGETHER_LOBBY, "lobby:room-closed", event);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process room closed event", e);
            throw new RuntimeException(e);
        }
    }

    @KafkaListener(topics = "room.lobby.created")
    public void consumeLobbyRoomCreated(String payload, Acknowledgment ack) {
        try {
            RoomListItemResponse room = objectMapper.readValue(payload, RoomListItemResponse.class);
            webSocketSessionService.sendToRoom(RoomChannelNames.WATCH_TOGETHER_LOBBY, "lobby:room-created", room);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process room lobby created event", e);
            throw new RuntimeException(e);
        }
    }

    @KafkaListener(topics = "room.lobby.updated")
    public void consumeLobbyRoomUpdated(String payload, Acknowledgment ack) {
        try {
            RoomListItemResponse room = objectMapper.readValue(payload, RoomListItemResponse.class);
            webSocketSessionService.sendToRoom(RoomChannelNames.WATCH_TOGETHER_LOBBY, "lobby:room-updated", room);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process room lobby updated event", e);
            throw new RuntimeException(e);
        }
    }
}
