package com.MyProject.socket_service.service;

import com.MyProject.common.redis.RedisService;
import com.MyProject.socket_service.dto.response.IntrospectResponse;
import com.MyProject.socket_service.repository.OutboxRepository;
import com.MyProject.socket_service.repository.WebSocketSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomWebSocketHandlerTest {

    @Mock WebSocketSessionRepository webSocketSessionRepository;
    @Mock RedisService redisService;
    @Mock SocketDownstreamService socketDownstreamService;
    @Mock OutboxRepository outboxRepository;

    CustomWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CustomWebSocketHandler(webSocketSessionRepository, redisService, socketDownstreamService,
                outboxRepository, new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    private WebSocketSession mockSession(String id, String query, String cookieHeader) {
        WebSocketSession session = mock(WebSocketSession.class);
        lenient().when(session.getId()).thenReturn(id);
        lenient().when(session.getUri()).thenReturn(URI.create("ws://localhost/ws" + (query != null ? "?" + query : "")));
        HttpHeaders headers = new HttpHeaders();
        if (cookieHeader != null) {
            headers.add("Cookie", cookieHeader);
        }
        lenient().when(session.getHandshakeHeaders()).thenReturn(headers);
        lenient().when(session.isOpen()).thenReturn(true);
        return session;
    }

    // ---------- afterConnectionEstablished ----------

    @Test
    void afterConnectionEstablished_noToken_closesWithBadData() throws Exception {
        WebSocketSession session = mockSession("s1", null, null);

        handler.afterConnectionEstablished(session);

        verify(session).close(CloseStatus.BAD_DATA);
        verifyNoInteractions(socketDownstreamService, webSocketSessionRepository);
    }

    @Test
    void afterConnectionEstablished_invalidToken_closesWithPolicyViolation() throws Exception {
        WebSocketSession session = mockSession("s1", "token=bad-token", null);
        when(socketDownstreamService.introspectAccessToken("bad-token"))
                .thenReturn(IntrospectResponse.builder().valid(false).build());

        handler.afterConnectionEstablished(session);

        verify(session).close(CloseStatus.POLICY_VIOLATION);
        verifyNoInteractions(webSocketSessionRepository);
    }

    @Test
    void afterConnectionEstablished_validQueryToken_registersSessionAndSavesOutbox() throws Exception {
        WebSocketSession session = mockSession("s1", "token=good-token", null);
        when(socketDownstreamService.introspectAccessToken("good-token"))
                .thenReturn(IntrospectResponse.builder().valid(true).userId("user-1").build());

        handler.afterConnectionEstablished(session);

        verify(session, never()).close(any());
        verify(webSocketSessionRepository).save(argThat(s -> s.getUserId().equals("user-1") && s.getSocketSessionId().equals("s1")));
        verify(outboxRepository).save(argThat(o -> o.getTopic().equals("user.online")));
    }

    @Test
    void afterConnectionEstablished_fallsBackToCookieWhenNoQueryToken() throws Exception {
        WebSocketSession session = mockSession("s1", null, "access_token=cookie-token; other=x");
        when(socketDownstreamService.introspectAccessToken("cookie-token"))
                .thenReturn(IntrospectResponse.builder().valid(true).userId("user-1").build());

        handler.afterConnectionEstablished(session);

        verify(socketDownstreamService).introspectAccessToken("cookie-token");
        verify(session, never()).close(any());
    }

    // ---------- afterConnectionClosed ----------

    @Test
    void afterConnectionClosed_unknownSession_isNoOp() throws Exception {
        WebSocketSession session = mockSession("unknown", null, null);

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        verifyNoInteractions(webSocketSessionRepository, outboxRepository);
    }

    @Test
    void afterConnectionClosed_knownSession_removesFromMapsAndDeletesRepository() throws Exception {
        WebSocketSession session = connectSession("s1", "user-1");

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        verify(webSocketSessionRepository).deleteBySocketSessionId("s1");
        verify(outboxRepository).save(argThat(o -> o.getTopic().equals("user.offline")));
        assertThat(handler.isUserInRoom("user-1", "room-1")).isFalse();
    }

    @Test
    void afterConnectionClosed_removesFromJoinedRoomAndDecrementsPresence() throws Exception {
        WebSocketSession session = connectSession("s1", "user-1");
        handler.joinRoom(session, "room-1");
        when(redisService.hashIncrementAndGet(eq("presence:active-chat:user-1"), eq("room-1"), eq(-1L))).thenReturn(0L);

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        verify(redisService).hashDelete("presence:active-chat:user-1", "room-1");
        assertThat(handler.isUserInRoom("user-1", "room-1")).isFalse();
    }

    // ---------- handleTextMessage: join/leave room ----------

    @Test
    void handleTextMessage_joinRoom_addsSessionAndIncrementsPresence() throws Exception {
        WebSocketSession session = connectSession("s1", "user-1");

        invokeHandleTextMessage(session, "{\"type\":\"join-room\",\"roomId\":\"room-1\"}");

        assertThat(handler.isUserInRoom("user-1", "room-1")).isTrue();
        verify(redisService).hashIncrementAndGet("presence:active-chat:user-1", "room-1", 1);
    }

    @Test
    void handleTextMessage_leaveRoom_removesSessionAndDecrementsPresence() throws Exception {
        WebSocketSession session = connectSession("s1", "user-1");
        handler.joinRoom(session, "room-1");

        invokeHandleTextMessage(session, "{\"type\":\"leave-room\",\"roomId\":\"room-1\"}");

        assertThat(handler.isUserInRoom("user-1", "room-1")).isFalse();
        verify(redisService).hashIncrementAndGet("presence:active-chat:user-1", "room-1", -1);
    }

    @Test
    void handleTextMessage_unknownType_isIgnoredWithoutError() throws Exception {
        WebSocketSession session = connectSession("s1", "user-1");

        invokeHandleTextMessage(session, "{\"type\":\"ping\"}");

        assertThat(handler.isUserInRoom("user-1", "room-1")).isFalse();
    }

    @Test
    void handleTextMessage_malformedJson_doesNotThrow() {
        WebSocketSession session = mockSession("s1", null, null);

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> invokeHandleTextMessage(session, "not-json"));
    }

    // ---------- sendToUser / sendToRoom / broadcast ----------

    @Test
    void sendToUser_sendsFormattedMessageToAllUserSessions() throws Exception {
        WebSocketSession session = connectSession("s1", "user-1");

        handler.sendToUser("user-1", "notice", java.util.Map.of("msg", "hi"));

        verify(session).sendMessage(argThat(m -> ((TextMessage) m).getPayload().contains("\"event\":\"notice\"")));
    }

    @Test
    void sendToUser_noConnectedSessions_isNoOp() {
        handler.sendToUser("ghost-user", "notice", "payload");
        // No exception, nothing to verify against - absence of interaction is the assertion.
    }

    @Test
    void sendToRoom_sendsToAllSessionsInRoom() throws Exception {
        WebSocketSession session = connectSession("s1", "user-1");
        handler.joinRoom(session, "room-1");

        handler.sendToRoom("room-1", "chat", "hello");

        verify(session).sendMessage(any(TextMessage.class));
    }

    @Test
    void broadcast_sendsToEveryConnectedSession() throws Exception {
        WebSocketSession session1 = connectSession("s1", "user-1");
        WebSocketSession session2 = connectSession("s2", "user-2");

        handler.broadcast("system", "maintenance");

        verify(session1).sendMessage(any(TextMessage.class));
        verify(session2).sendMessage(any(TextMessage.class));
    }

    @Test
    void sendMessage_sessionClosed_doesNotAttemptSend() throws Exception {
        WebSocketSession session = connectSession("s1", "user-1");
        when(session.isOpen()).thenReturn(false);

        handler.sendToUser("user-1", "notice", "payload");

        verify(session, never()).sendMessage(any());
    }

    // ---------- helpers ----------

    private WebSocketSession connectSession(String sessionId, String userId) throws Exception {
        WebSocketSession session = mockSession(sessionId, "token=tok-" + sessionId, null);
        lenient().when(socketDownstreamService.introspectAccessToken("tok-" + sessionId))
                .thenReturn(IntrospectResponse.builder().valid(true).userId(userId).build());
        handler.afterConnectionEstablished(session);
        return session;
    }

    private void invokeHandleTextMessage(WebSocketSession session, String payload) throws Exception {
        var method = CustomWebSocketHandler.class.getDeclaredMethod("handleTextMessage", WebSocketSession.class, TextMessage.class);
        method.setAccessible(true);
        method.invoke(handler, session, new TextMessage(payload));
    }
}
