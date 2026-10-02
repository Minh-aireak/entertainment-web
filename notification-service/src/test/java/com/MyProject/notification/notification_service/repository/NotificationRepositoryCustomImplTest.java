package com.MyProject.notification.notification_service.repository;

import com.MyProject.notification.notification_service.entity.Notification;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * NotificationService writes every new notification's recipient entry as an explicit null
 * ({userId: null}), so "unread" must be matched with {field: null} (null or missing) - the old
 * {$exists: false} filter never matched a stored notification, leaving the unread badge at 0 and
 * "mark all as read" a no-op.
 */
class NotificationRepositoryCustomImplTest {
    static final String USER_ID = "10000000-0000-0000-0000-000000000002";

    MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    NotificationRepositoryCustomImpl repository = new NotificationRepositoryCustomImpl(mongoTemplate);

    private void assertTargetsUnreadEntriesOf(Query query) {
        Document filter = query.getQueryObject();
        assertEquals(new Document("$in", List.of(USER_ID)), filter.get("toUserIds"));
        assertTrue(filter.containsKey("recipientReadMap." + USER_ID));
        assertNull(filter.get("recipientReadMap." + USER_ID), "must match a null entry, not $exists:false");
    }

    @Test
    void countUnreadByUserId_countsEntriesStoredAsNull() {
        repository.countUnreadByUserId(USER_ID);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).count(query.capture(), eq(Notification.class));
        assertTargetsUnreadEntriesOf(query.getValue());
    }

    @Test
    void markAllAsRead_stampsEntriesStoredAsNull() {
        LocalDateTime now = LocalDateTime.now();

        repository.markAllAsRead(USER_ID, now);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateMulti(query.capture(), update.capture(), eq(Notification.class));
        assertTargetsUnreadEntriesOf(query.getValue());
        assertEquals(new Document("recipientReadMap." + USER_ID, now), update.getValue().getUpdateObject().get("$set"));
    }
}
