package com.MyProject.comment_service.service;

import com.MyProject.common.redis.ToggleDebounceService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Debounces "comment liked" notifications the same way {@code post-service} debounces post likes:
 * a burst of reaction clicks on the same comment by the same actor within
 * {@code app.notification.like-debounce-seconds} collapses into a single pending entry (see
 * {@link CommentReactionNotificationFlushJob}), which re-checks the live Redis reaction state
 * before deciding whether to notify.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CommentReactionNotificationService {
    static final String QUEUE_KEY = "comment:reaction:notify:queue";
    private static final String DELIMITER = "|";

    ToggleDebounceService toggleDebounceService;

    @NonFinal
    @Value("${app.notification.like-debounce-seconds:3}")
    long debounceSeconds;

    public void scheduleNotification(String commentId, String actorUserId) {
        toggleDebounceService.schedule(QUEUE_KEY, commentId + DELIMITER + actorUserId, debounceSeconds);
    }

    static String[] splitMember(String member) {
        return member.split("\\" + DELIMITER, 2);
    }
}
