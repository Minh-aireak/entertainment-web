package com.MyProject.chat_service.repository.mongo;

import com.MyProject.chat_service.entity.ConversationMember;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationMemberRepository extends MongoRepository<ConversationMember, String> {

    List<ConversationMember> findByConversationId(String conversationId);

    List<ConversationMember> findByConversationIdIn(List<String> conversationIds);

    List<ConversationMember> findByUserId(String userId);

    Optional<ConversationMember> findByConversationIdAndUserId(
            String conversationId,
            String userId
    );

    boolean existsByConversationIdAndUserId(String conversationId, String userId);

    @Query("""
        { "conversationId": ?0, "userId": { $ne: ?1 } }
    """)
    List<ConversationMember> findByConversationIdAndUserIdNot(String conversationId, String userId);
}
