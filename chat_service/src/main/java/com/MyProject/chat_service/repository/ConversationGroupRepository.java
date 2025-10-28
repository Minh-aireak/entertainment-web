package com.MyProject.chat_service.repository;

import com.MyProject.chat_service.entity.ConversationGroup;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConversationGroupRepository extends MongoRepository<ConversationGroup, String> {

}
