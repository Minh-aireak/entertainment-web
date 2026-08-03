package com.MyProject.notification.notification_service.repository;

import com.MyProject.notification.notification_service.entity.Outbox;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface OutboxRepository extends MongoRepository<Outbox, String> {
    void deleteByCreatedDateBefore(Instant date);
}
