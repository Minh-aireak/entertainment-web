package com.MyProject.chat_service.repository.mongo;

import com.MyProject.chat_service.entity.Conversation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface ConversationRepository extends MongoRepository<Conversation, String>, ConversationRepositoryCustom {
    @Query("{ '_id': ?0 }")
    @Update("{ '$inc' : { 'totalSeq' : 1 } }")
    void incrementTotalSeq(String conversationId);

    @Query("{ 'userIds': ?0 }")
    Page<Conversation> findAllByUserId(String userId, Pageable pageable);
}
