package com.MyProject.socket_service.service;

import com.MyProject.socket_service.entity.WebSocketSession;
import com.MyProject.socket_service.repository.WebSocketSessionRepository;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WebSocketSessionService {

    SocketIOServer socketIOServer;
    WebSocketSessionRepository webSocketSessionRepository;
    Map<String, Set<String>> roomMembers = new ConcurrentHashMap<>();
    // roomId -> Set<userId>

    Map<String, Set<String>> userRooms = new ConcurrentHashMap<>();
    // userId -> Set<roomId>

//    Map<UUID, String> sessionToUser = new ConcurrentHashMap<>();
//    // sessionId -> userId

    public void createSession(WebSocketSession webSocketSession) {
        webSocketSessionRepository.save(webSocketSession);
    }

    public void deleteSession(String socketSessionId) {
        webSocketSessionRepository.deleteBySocketSessionId(socketSessionId);
    }

    public void publishToUsers(List<String> userIds, String event, Object payload) {
        for (String userId : userIds) {
            socketIOServer.getRoomOperations(userId).sendEvent(event, payload);
        }
    }
}
