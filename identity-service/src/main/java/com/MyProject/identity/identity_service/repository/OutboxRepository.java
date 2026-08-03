package com.MyProject.identity.identity_service.repository;

import com.MyProject.identity.identity_service.entity.Outbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OutboxRepository extends JpaRepository<Outbox, String> {
    int deleteByCreatedAtBefore(LocalDateTime date);
}
