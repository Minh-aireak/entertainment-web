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

    @Override
    public long countUnreadByUserId(String userId) {
        Query query = new Query(
                Criteria.where("toUserIds").in(userId)
                        .and("recipientReadMap." + userId).exists(false)
        );
        return mongoTemplate.count(query, Notification.class);
    }

    @Override
    public void markAllAsRead(String userId, LocalDateTime now) {
        Query query = new Query(
                Criteria.where("toUserIds").in(userId)
                        .and("recipientReadMap." + userId).exists(false)
        );
        Update update = new Update().set("recipientReadMap." + userId, now);
        mongoTemplate.updateMulti(query, update, Notification.class);
    }
}