package com.MyProject.socket_service.service;

import com.MyProject.common.redis.RedisService;
import com.MyProject.socket_service.entity.Outbox;
import com.MyProject.socket_service.entity.WebSocketSession;
import com.MyProject.socket_service.repository.OutboxRepository;
import com.MyProject.socket_service.repository.WebSocketSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CustomWebSocketHandler extends TextWebSocketHandler {
    private static final String ACCESS_TOKEN_COOKIE = "access_token";

    WebSocketSessionRepository webSocketSessionRepository;
    RedisService redisService;
    SocketDownstreamService socketDownstreamService;
    OutboxRepository outboxRepository;
    ObjectMapper objectMapper;

    // userId -> Set<org.springframework.web.socket.WebSocketSession>
    Map<String, Set<org.springframework.web.socket.WebSocketSession>> userSessions = new ConcurrentHashMap<>();
    
    // sessionId -> userId
    Map<String, String> sessionToUser = new ConcurrentHashMap<>();

    // sessionId -> Set<roomId>
    Map<String, Set<String>> sessionRooms = new ConcurrentHashMap<>();

    // roomId -> Set<org.springframework.web.socket.WebSocketSession>
    Map<String, Set<org.springframework.web.socket.WebSocketSession>> roomSessions = new ConcurrentHashMap<>();

    private static final String PRESENCE_KEY_PREFIX = "presence:active-chat:";

    @Override
    public void afterConnectionEstablished(org.springframework.web.socket.WebSocketSession session) throws Exception {
        try {
            String token = extractToken(session);

            if (!StringUtils.hasText(token)) {
                log.warn("Connection attempt without token for session {}", session.getId());
                session.close(CloseStatus.BAD_DATA);
                return;
            }

            var introspection = socketDownstreamService.introspectAccessToken(token);

            if (introspection == null || !introspection.isValid()) {
                log.warn("Invalid token for session {}", session.getId());
                session.close(CloseStatus.POLICY_VIOLATION);
                return;
            }

            String userId = introspection.getUserId();
            String socketSessionId = session.getId();

            webSocketSessionRepository.save(WebSocketSession.builder()
                    .socketSessionId(socketSessionId)
                    .userId(userId)
                    .build());

            userSessions
                    .computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet())
                    .add(session);
            
            sessionToUser.put(socketSessionId, userId);
            sessionRooms.put(socketSessionId, ConcurrentHashMap.newKeySet());

            // Publish event for CDC
            try {
                outboxRepository.save(Outbox.builder()
                        .aggregateId(socketSessionId)
                        .topic("user.online")
                        .payload(objectMapper.writeValueAsString(Map.of("userId", userId, "timestamp", Instant.now())))
                        .build());
            } catch (Exception e) {
                log.error("Failed to save outbox event for user online", e);
            }

            log.info("User {} connected with session {}", userId, socketSessionId);
        } catch (Exception e) {
            log.error("Error establishing connection for session {}", session.getId(), e);
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    private String extractToken(org.springframework.web.socket.WebSocketSession session) {
        String tokenFromQuery = extractTokenFromQuery(session);
        if (StringUtils.hasText(tokenFromQuery)) {
            return tokenFromQuery;
        }
        return extractTokenFromCookies(session);
    }

    private String extractTokenFromQuery(org.springframework.web.socket.WebSocketSession session) {
        if (session.getUri() == null) {
            return null;
        }
        String query = session.getUri().getQuery();
        if (!StringUtils.hasText(query)) {
            return null;
        }

        return Arrays.stream(query.split("&"))
                .map(part -> part.split("=", 2))
                .filter(parts -> parts.length == 2 && "token".equals(parts[0]))
                .map(parts -> URLDecoder.decode(parts[1], StandardCharsets.UTF_8))
                .findFirst()
                .orElse(null);
    }

    private String extractTokenFromCookies(org.springframework.web.socket.WebSocketSession session) {
        String cookieHeader = session.getHandshakeHeaders().getFirst("Cookie");
        if (!StringUtils.hasText(cookieHeader)) {
            return null;
        }

        return Arrays.stream(cookieHeader.split(";"))
                .map(String::trim)
                .map(part -> part.split("=", 2))
                .filter(parts -> parts.length == 2 && ACCESS_TOKEN_COOKIE.equals(parts[0]))
                .map(parts -> URLDecoder.decode(parts[1], StandardCharsets.UTF_8))
                .findFirst()
                .orElse(null);
    }

    @Override
    public void afterConnectionClosed(org.springframework.web.socket.WebSocketSession session, CloseStatus status) throws Exception {
        String socketSessionId = session.getId();
        String userId = sessionToUser.remove(socketSessionId);

        if (userId == null) return;

        Set<org.springframework.web.socket.WebSocketSession> sessions = userSessions.get(userId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                userSessions.remove(userId);
            }
        }

        // Remove from rooms
        Set<String> rooms = sessionRooms.remove(socketSessionId);
        if (rooms != null) {
            rooms.forEach(roomId -> {
                Set<org.springframework.web.socket.WebSocketSession> room = roomSessions.get(roomId);
                if (room != null) {
                    room.remove(session);
                    if (room.isEmpty()) {
                        roomSessions.remove(roomId);
                    }
                }
                decrementPresence(userId, roomId);
            });
        }

        webSocketSessionRepository.deleteBySocketSessionId(socketSessionId);

        // Publish event for CDC
        try {
            outboxRepository.save(Outbox.builder()
                    .aggregateId(socketSessionId)
                    .topic("user.offline")
                    .payload(objectMapper.writeValueAsString(Map.of("userId", userId, "timestamp", Instant.now())))
                    .build());
        } catch (Exception e) {
            log.error("Failed to save outbox event for user offline", e);
        }

        log.info("User {} disconnected from session {}", userId, socketSessionId);
    }

    @Override
    protected void handleTextMessage(org.springframework.web.socket.WebSocketSession session, TextMessage message) throws Exception {
        try {
            Map<String, Object> data = objectMapper.readValue(message.getPayload(), Map.class);
            String type = (String) data.get("type");
            
            if ("join-room".equals(type)) {
                String roomId = (String) data.get("roomId");
                if (roomId != null) {
                    joinRoom(session, roomId);
                }
            } else if ("leave-room".equals(type)) {
                String roomId = (String) data.get("roomId");
                if (roomId != null) {
                    leaveRoom(session, roomId);
                }
            }
        } catch (Exception e) {
            log.error("Error handling text message from session {}", session.getId(), e);
        }
    }

    public void joinRoom(org.springframework.web.socket.WebSocketSession session, String roomId) {
        roomSessions.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(session);
        sessionRooms.computeIfAbsent(session.getId(), k -> ConcurrentHashMap.newKeySet()).add(roomId);

        String userId = sessionToUser.get(session.getId());
        incrementPresence(userId, roomId);

        log.info("Session {} joined room {}", session.getId(), roomId);
    }

    public void leaveRoom(org.springframework.web.socket.WebSocketSession session, String roomId) {
        Set<org.springframework.web.socket.WebSocketSession> sessions = roomSessions.get(roomId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                roomSessions.remove(roomId);
            }
        }
        Set<String> rooms = sessionRooms.get(session.getId());
        if (rooms != null) {
            rooms.remove(roomId);
        }

        String userId = sessionToUser.get(session.getId());
        decrementPresence(userId, roomId);

        log.info("Session {} left room {}", session.getId(), roomId);
    }

    private void incrementPresence(String userId, String roomId) {
        if (userId == null) return;
        try {
            redisService.hashIncrementAndGet(PRESENCE_KEY_PREFIX + userId, roomId, 1);
        } catch (Exception e) {
            log.error("Failed to increment presence for user {} in room {}", userId, roomId, e);
        }
    }

    private void decrementPresence(String userId, String roomId) {
        if (userId == null) return;
        try {
            Long remaining = redisService.hashIncrementAndGet(PRESENCE_KEY_PREFIX + userId, roomId, -1);
            if (remaining != null && remaining <= 0) {
                redisService.hashDelete(PRESENCE_KEY_PREFIX + userId, roomId);
            }
        } catch (Exception e) {
            log.error("Failed to decrement presence for user {} in room {}", userId, roomId, e);
        }
    }

    public boolean isUserInRoom(String userId, String roomId) {
        Set<org.springframework.web.socket.WebSocketSession> sessions = roomSessions.get(roomId);
        if (sessions == null) return false;
        
        return sessions.stream().anyMatch(session -> userId.equals(sessionToUser.get(session.getId())));
    }

    public void sendToUser(String userId, String event, Object payload) {
        Set<org.springframework.web.socket.WebSocketSession> sessions = userSessions.get(userId);
        if (sessions != null) {
            String jsonPayload = formatMessage(event, payload);
            sessions.forEach(session -> sendMessage(session, jsonPayload));
        }
    }

    public void sendToRoom(String roomId, String event, Object payload) {
        Set<org.springframework.web.socket.WebSocketSession> sessions = roomSessions.get(roomId);
        if (sessions != null) {
            String jsonPayload = formatMessage(event, payload);
            sessions.forEach(session -> sendMessage(session, jsonPayload));
        }
    }

    public void broadcast(String event, Object payload) {
        String jsonPayload = formatMessage(event, payload);
        userSessions.values().forEach(sessions -> 
            sessions.forEach(session -> sendMessage(session, jsonPayload))
        );
    }

    private String formatMessage(String event, Object payload) {
        try {
            return objectMapper.writeValueAsString(Map.of("event", event, "data", payload));
        } catch (IOException e) {
            log.error("Error formatting message", e);
            return "{}";
        }
    }

    private void sendMessage(org.springframework.web.socket.WebSocketSession session, String payload) {
        try {
            // Kafka listeners can fan out concurrently; a standard Spring session only
            // permits one send at a time.
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(payload));
                }
            }
        } catch (IOException e) {
            log.error("Error sending message to session {}", session.getId(), e);
        }
    }
}
