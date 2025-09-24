package com.MyProject.chat_service.repository;

import com.MyProject.chat_service.entity.ConversationGroup;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConversationGroupRepository extends MongoRepository<ConversationGroup, String> {
    @Query(value = "{'_class': 'group', 'participantInfos.userId': ?0}")
    List<ConversationGroup> findAllGroupByUserId(String userId);
}
