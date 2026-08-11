package com.MyProject.film.film_service.service;

import com.MyProject.common.redis.ToggleDebounceService;
import com.MyProject.film.film_service.dto.event.NotificationEvent;
import com.MyProject.film.film_service.entity.Outbox;
import com.MyProject.film.film_service.entity.Rating;
import com.MyProject.film.film_service.repository.mysql.OutboxRepository;
import com.MyProject.film.film_service.repository.mysql.RatingLikeRepository;
import com.MyProject.film.film_service.repository.mysql.RatingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Drains {@link RatingLikeNotificationService}'s debounce queue: for every (ratingId,
 * actorUserId) pair whose debounce window elapsed, re-reads the current like state from MySQL
 * (the source of truth) and only notifies the rating's owner if it is still liked at that moment.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RatingLikeNotificationFlushJob {
    private static final int BATCH_SIZE = 200;

    private final ToggleDebounceService toggleDebounceService;
    private final RatingLikeRepository ratingLikeRepository;
    private final RatingRepository ratingRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 1000)
    public void flushPendingLikeNotifications() {
        List<String> due = toggleDebounceService.pollDue(RatingLikeNotificationService.QUEUE_KEY, BATCH_SIZE);
        for (String member : due) {
            try {
                processMember(member);
            } catch (Exception e) {
                log.error("Failed to flush rating like-notification for member {}", member, e);
            }
        }
    }

    private void processMember(String member) {
        String[] parts = RatingLikeNotificationService.splitMember(member);
        if (parts.length != 2) {
            return;
        }
        String ratingId = parts[0];
        String actorUserId = parts[1];

        if (!ratingLikeRepository.existsByRatingIdAndUserId(ratingId, actorUserId)) {
            return;
        }

        Rating rating = ratingRepository.findById(ratingId).orElse(null);
        if (rating == null || actorUserId.equals(rating.getUserId())) {
            return;
        }

        NotificationEvent event = NotificationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .typeNotification("FILM_LIKE")
                .userIdSender(actorUserId)
                .toUserIds(List.of(rating.getUserId()))
                .build();

        try {
            outboxRepository.save(Outbox.builder()
                    .aggregateId(ratingId)
                    .topic("notification.events")
                    .payload(objectMapper.writeValueAsString(event))
                    .build());
        } catch (Exception e) {
            log.error("Failed to save rating like-notification outbox for rating {}", ratingId, e);
        }
    }
}
