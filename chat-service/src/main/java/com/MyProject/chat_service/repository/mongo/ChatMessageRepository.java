package com.MyProject.chat_service.repository.mongo;

import com.MyProject.chat_service.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatMessageRepository extends MongoRepository<ChatMessage, String> {
    @Query(value = "{'conversationId': ?0, 'seenAtMap.?1': { $exists: false } }")
    List<ChatMessage> findAllByConversationIdAndSeenAtMapNotContainsKey(String conversationId, String userId);

    Page<ChatMessage> findByConversationId(String conversationId, Pageable pageable);

    Optional<ChatMessage> findTopByConversationIdOrderByCreatedDateDesc(String conversationId);

    long countByConversationId(String conversationId);

    long countByConversationIdAndSeqGreaterThan(String conversationId, long lastSeenSeq);

    Optional<ChatMessage> findByClientMessageId(String clientMessageId);
}
