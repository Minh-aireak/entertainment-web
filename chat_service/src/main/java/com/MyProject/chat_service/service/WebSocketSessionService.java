package com.MyProject.chat_service.service;

import com.MyProject.chat_service.entity.WebSocketSession;
import com.MyProject.chat_service.repository.WebSocketSessionRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WebSocketSessionService {
    WebSocketSessionRepository webSocketSessionRepository;

    public void createSession(WebSocketSession webSocketSession) {
        webSocketSessionRepository.save(webSocketSession);
    }

    public void deleteSession(String socketSessionId) {
        webSocketSessionRepository.deleteBySocketSessionId(socketSessionId);
    }
}
