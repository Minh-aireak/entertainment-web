package com.MyProject.socket_service.controller;

import com.MyProject.socket_service.service.WebSocketSessionService;
import com.MyProject.socket_service.dto.request.IntrospectRequest;
import com.MyProject.socket_service.entity.WebSocketSession;
import com.MyProject.socket_service.exception.AppException;
import com.MyProject.socket_service.exception.ErrorCode;
import com.MyProject.socket_service.service.IdentityService;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.annotation.OnConnect;
import com.corundumstudio.socketio.annotation.OnDisconnect;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SocketHandler {
    SocketIOServer socketIOServer;
    WebSocketSessionService webSocketSessionService;
    IdentityService identityService;

    @OnConnect
    public void clientConnected(SocketIOClient socketIOClient){
        String token = socketIOClient.getHandshakeData().getSingleUrlParam("token");

        var response = identityService.introspect(IntrospectRequest.builder()
                .token(token)
                .build());
        if (!response.isValid()) {
            socketIOClient.disconnect();
            throw new AppException(ErrorCode.UNAUTHENTICATED_SOCKET);
        }

        WebSocketSession webSocketSession = WebSocketSession.builder()
                .socketSessionId(socketIOClient.getSessionId().toString())
                .userId(response.getUserId())
                .createdAt(Instant.now())
                .build();
        webSocketSessionService.createSession(webSocketSession);
    }

    @OnDisconnect
    public void clientDisconnected(SocketIOClient socketIOClient){
        webSocketSessionService.deleteSession(socketIOClient.getSessionId().toString());
    }

    @PostConstruct
    public void startServer() {
        socketIOServer.start();
        socketIOServer.addListeners(this);
    }

    @PreDestroy
    public void stopServer() {
        socketIOServer.stop();
    }
}
