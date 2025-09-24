package com.MyProject.chat_service.repository;

import com.MyProject.chat_service.entity.ConversationDirect;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConversationDirectRepository extends MongoRepository<ConversationDirect, String> {
    @Query(value = "{'_class': 'direct', 'participantInfos.userId': ?0}")
    List<ConversationDirect> findAllDirectByUserId(String userId);
}
