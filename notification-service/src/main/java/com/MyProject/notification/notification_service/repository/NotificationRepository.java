package com.MyProject.notification.notification_service.repository;

import com.MyProject.notification.notification_service.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends MongoRepository<Notification, String> {
    @Query(value = "{ 'toUserIds': { $in: ?0 } }", sort = "{ 'createdAt': -1 }")
    Page<Notification> findByToUserIdsInOrderByCreatedAtDesc(String userId, Pageable pageable);
//
//    @Query(value = "{ 'recipientReadMap.?0': null }", sort = "{ 'createdAt': -1 }")
//    List<Notification> findUnreadByUserId(String userId);
//
//    @Query(value = "{ 'recipientReadMap.?0': null }", count = true)
//    Long countUnreadByUserId(String userId);
}
