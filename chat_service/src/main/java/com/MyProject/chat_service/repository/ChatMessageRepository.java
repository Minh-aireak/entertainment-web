package com.MyProject.chat_service.repository;

import com.MyProject.chat_service.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends MongoRepository<ChatMessage, String> {
    @Query(value = "{'conversationId': ?0, 'seenAtMap.?1': { $exists: false } }")
    List<ChatMessage> findAllByConversationIdAndSeenAtMapNotContainsKey(String conversationId, String userId);

    @Query("{'conversationId': ?0}")
    Page<ChatMessage> findChatMessage(String conversationId, Pageable pageable);
    
    @Query("{'sender.userId': ?0}")
    List<ChatMessage> findAllBySenderUserId(String userId);
}
