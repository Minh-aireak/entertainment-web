package com.MyProject.profile.profile_service.repository.mongo;

import com.MyProject.profile.profile_service.entity.Outbox;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OutboxRepository extends MongoRepository<Outbox, String> {
    List<Outbox> findByProcessedFalse();
    void deleteByCreatedDateBefore(LocalDateTime date);
}
