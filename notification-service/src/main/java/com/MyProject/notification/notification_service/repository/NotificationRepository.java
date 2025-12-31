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
public interface NotificationRepository extends MongoRepository<Notification, String> {
    @Query(value = "{ 'toUserIds': { $in: ?0 } }", sort = "{ 'createdAt': -1 }")
    Page<Notification> findByToUserIdsInOrderByCreatedAtDesc(String userId, Pageable pageable);

    @Query(value = "{ 'recipientReadMap.?0': null }", count = true)
    Long countUnreadByUserId(String userId);

    @Query("{ 'recipientReadMap.?0': null }")
    @Update("{ '$set': { 'recipientReadMap.?0': ?1 } }")
    void markAllAsRead(String userId, LocalDateTime now);
}
