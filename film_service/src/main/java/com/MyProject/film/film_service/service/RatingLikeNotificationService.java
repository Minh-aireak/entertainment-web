package com.MyProject.film.film_service.service;

import com.MyProject.common.redis.ToggleDebounceService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Debounces "rating liked" notifications the same way post-service/comment-service debounce
 * their likes: a burst of like/unlike/like on the same rating by the same actor within
 * {@code app.notification.like-debounce-seconds} collapses into a single pending entry (see
 * {@link RatingLikeNotificationFlushJob}), which re-checks the real like state before deciding
 * whether to notify.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RatingLikeNotificationService {
    static final String QUEUE_KEY = "rating:like:notify:queue";
    private static final String DELIMITER = "|";

    ToggleDebounceService toggleDebounceService;

    @NonFinal
    @Value("${app.notification.like-debounce-seconds:3}")
    long debounceSeconds;

    public void scheduleNotification(String ratingId, String actorUserId) {
        toggleDebounceService.schedule(QUEUE_KEY, ratingId + DELIMITER + actorUserId, debounceSeconds);
    }

    static String[] splitMember(String member) {
        return member.split("\\" + DELIMITER, 2);
    }
}
