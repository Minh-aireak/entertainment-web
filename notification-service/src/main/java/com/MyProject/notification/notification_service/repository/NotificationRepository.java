package com.MyProject.notification.notification_service.repository;

import com.MyProject.notification.notification_service.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface NotificationRepository extends MongoRepository<Notification, String>,
        NotificationRepositoryCustom {
    @Query(value = "{ 'toUserIds': ?0 }", sort = "{ 'createdAt': -1 }")
    Page<Notification> findByToUserIdsOrderByCreatedAtDesc(String userId, Pageable pageable);
}
