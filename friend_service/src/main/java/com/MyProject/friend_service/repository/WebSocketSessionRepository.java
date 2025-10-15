package com.MyProject.friend_service.repository;

import com.MyProject.chat_service.entity.WebSocketSession;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Set;

@Repository
public interface WebSocketSessionRepository extends MongoRepository<WebSocketSession, String> {
    void deleteBySocketSessionId(String socketSessionId);
    Set<WebSocketSession> findAllByUserIdIn(Set<String> userIds);
}
