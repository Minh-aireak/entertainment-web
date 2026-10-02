package com.MyProject.notification.notification_service.repository;

import com.MyProject.notification.notification_service.entity.Notification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.LocalDateTime;

@RequiredArgsConstructor
public class NotificationRepositoryCustomImpl implements NotificationRepositoryCustom {
    final MongoTemplate mongoTemplate;

    // Unread = the recipient's recipientReadMap entry is null, which is how NotificationService writes
    // every new notification ({userId: null}) - not a missing key. is(null) matches null *or* missing,
    // the same rule NotificationService uses for the per-item "read" flag; exists(false) only matched
    // missing keys, so it never counted (or marked read) anything actually stored.
    private Query unreadFor(String userId) {
        return new Query(
                Criteria.where("toUserIds").in(userId)
                        .and("recipientReadMap." + userId).is(null)
        );
    }

    @Override
    public long countUnreadByUserId(String userId) {
        return mongoTemplate.count(unreadFor(userId), Notification.class);
    }

    @Override
    public void markAllAsRead(String userId, LocalDateTime now) {
        Update update = new Update().set("recipientReadMap." + userId, now);
        mongoTemplate.updateMulti(unreadFor(userId), update, Notification.class);
    }
}