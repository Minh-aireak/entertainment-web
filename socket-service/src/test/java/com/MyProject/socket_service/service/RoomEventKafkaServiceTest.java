package com.MyProject.socket_service.service;

import com.MyProject.socket_service.dto.event.RoomClosedEvent;
import com.MyProject.socket_service.dto.event.RoomParticipantChangedEvent;
import com.MyProject.socket_service.dto.event.RoomPlaybackChangedEvent;
import com.MyProject.socket_service.dto.response.RoomListItemResponse;
import com.MyProject.socket_service.dto.response.RoomMessageResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomEventKafkaServiceTest {

    @Mock CustomWebSocketHandler webSocketSessionService;
    @Mock Acknowledgment ack;

    RoomEventKafkaService roomEventKafkaService;

    @BeforeEach
    void setUp() {
        roomEventKafkaService = new RoomEventKafkaService(webSocketSessionService, new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    @Test
    void consumePlaybackUpdated_happyPath_broadcastsToRoomAndAcks() {
        String payload = "{\"roomId\":\"room-1\",\"action\":\"PLAY\"}";

        roomEventKafkaService.consumePlaybackUpdated(payload, ack);

        verify(webSocketSessionService).sendToRoom(eq("watch-room:room-1"), eq("room:playback"), any(RoomPlaybackChangedEvent.class));
        verify(ack).acknowledge();
    }

    @Test
    void consumePlaybackUpdated_malformedPayload_throwsAndDoesNotAck() {
        assertThrows(() -> roomEventKafkaService.consumePlaybackUpdated("not-json", ack));

        verifyNoInteractions(webSocketSessionService, ack);
    }

    @Test
    void consumeParticipantChanged_happyPath_broadcastsToRoomAndAcks() {
        String payload = "{\"roomId\":\"room-1\",\"eventType\":\"JOINED\",\"participantCount\":2}";

        roomEventKafkaService.consumeParticipantChanged(payload, ack);

        verify(webSocketSessionService).sendToRoom(eq("watch-room:room-1"), eq("room:participants"), any(RoomParticipantChangedEvent.class));
        verify(ack).acknowledge();
    }

    @Test
    void consumeParticipantChanged_malformedPayload_throwsAndDoesNotAck() {
        assertThrows(() -> roomEventKafkaService.consumeParticipantChanged("not-json", ack));

        verifyNoInteractions(webSocketSessionService, ack);
    }

    @Test
    void consumeMessageCreated_happyPath_broadcastsToRoomAndAcks() {
        String payload = "{\"id\":\"m-1\",\"roomId\":\"room-1\",\"content\":\"hi\"}";

        roomEventKafkaService.consumeMessageCreated(payload, ack);

        verify(webSocketSessionService).sendToRoom(eq("watch-room:room-1"), eq("room:message"), any(RoomMessageResponse.class));
        verify(ack).acknowledge();
    }

    @Test
    void consumeMessageCreated_malformedPayload_throwsAndDoesNotAck() {
        assertThrows(() -> roomEventKafkaService.consumeMessageCreated("not-json", ack));

        verifyNoInteractions(webSocketSessionService, ack);
    }

    @Test
    void consumeRoomClosed_happyPath_broadcastsToRoomAndLobbyThenAcks() {
        String payload = "{\"roomId\":\"room-1\",\"reason\":\"HOST_CLOSED\"}";

        roomEventKafkaService.consumeRoomClosed(payload, ack);

        verify(webSocketSessionService).sendToRoom(eq("watch-room:room-1"), eq("room:closed"), any(RoomClosedEvent.class));
        verify(webSocketSessionService).sendToRoom(eq("watch-together-lobby"), eq("lobby:room-closed"), any(RoomClosedEvent.class));
        verify(ack).acknowledge();
    }

    @Test
    void consumeRoomClosed_malformedPayload_throwsAndDoesNotAck() {
        assertThrows(() -> roomEventKafkaService.consumeRoomClosed("not-json", ack));

        verifyNoInteractions(webSocketSessionService, ack);
    }

    @Test
    void consumeLobbyRoomCreated_happyPath_broadcastsToLobbyRoomAndAcks() {
        String payload = "{\"id\":\"room-1\",\"name\":\"Room\",\"publicRoom\":true}";

        roomEventKafkaService.consumeLobbyRoomCreated(payload, ack);

        verify(webSocketSessionService).sendToRoom(eq("watch-together-lobby"), eq("lobby:room-created"), any(RoomListItemResponse.class));
        verify(ack).acknowledge();
    }

    @Test
    void consumeLobbyRoomCreated_malformedPayload_throwsAndDoesNotAck() {
        assertThrows(() -> roomEventKafkaService.consumeLobbyRoomCreated("not-json", ack));

        verifyNoInteractions(webSocketSessionService, ack);
    }

    @Test
    void consumeLobbyRoomUpdated_happyPath_broadcastsToLobbyRoomAndAcks() {
        String payload = "{\"id\":\"room-1\",\"episodeId\":\"episode-12\",\"publicRoom\":true}";

        roomEventKafkaService.consumeLobbyRoomUpdated(payload, ack);

        verify(webSocketSessionService).sendToRoom(eq("watch-together-lobby"), eq("lobby:room-updated"),
                argThat((RoomListItemResponse room) -> "episode-12".equals(room.getEpisodeId())));
        verify(ack).acknowledge();
    }

    private void assertThrows(org.junit.jupiter.api.function.Executable executable) {
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, executable);
    }
}
