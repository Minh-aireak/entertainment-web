package com.MyProject.friend.friend_service.repository.mongo;

import com.MyProject.friend.friend_service.entity.Outbox;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboxRepository extends MongoRepository<Outbox, String> {
}
