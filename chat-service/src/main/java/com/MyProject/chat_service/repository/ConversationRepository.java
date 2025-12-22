package com.MyProject.chat_service.repository;

import com.MyProject.chat_service.entity.Conversation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConversationRepository extends MongoRepository<Conversation, String> {
    @Query("{'participantInfos.userId': ?0}")
    List<Conversation> findAllByUserId(String userId);
}
