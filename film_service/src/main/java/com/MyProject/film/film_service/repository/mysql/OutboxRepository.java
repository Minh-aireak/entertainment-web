package com.MyProject.film.film_service.repository.mysql;

import com.MyProject.film.film_service.entity.Outbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface OutboxRepository extends JpaRepository<Outbox, String> {
    int deleteByCreatedAtBefore(LocalDateTime date);
}
