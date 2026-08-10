package com.MyProject.post.post_service.service;

import com.MyProject.common.redis.ToggleDebounceService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Debounces "post liked" notifications: a burst of like/unlike/like on the same post by the same
 * actor within {@code app.notification.like-debounce-seconds} collapses into a single pending
 * entry (see {@link PostLikeNotificationFlushJob}), which re-checks the real like state before
 * deciding whether to notify.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PostLikeNotificationService {
    static final String QUEUE_KEY = "post:like:notify:queue";
    private static final String DELIMITER = "|";

    ToggleDebounceService toggleDebounceService;

    @NonFinal
    @Value("${app.notification.like-debounce-seconds:3}")
    long debounceSeconds;

    public void scheduleNotification(String postId, String actorUserId) {
        toggleDebounceService.schedule(QUEUE_KEY, postId + DELIMITER + actorUserId, debounceSeconds);
    }

    static String[] splitMember(String member) {
        return member.split("\\" + DELIMITER, 2);
    }
}
