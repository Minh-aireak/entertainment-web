package com.MyProject.chat_service.repository.mongo;

import com.MyProject.chat_service.entity.ConversationDirect;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConversationDirectRepository extends MongoRepository<ConversationDirect, String> {
    @Query(value = "{'_class': 'direct', 'participantsHash': ?0}")
    Optional<ConversationDirect> findByParticipantsHash(String ids);
}
