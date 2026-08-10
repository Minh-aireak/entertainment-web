package com.MyProject.post.post_service.service;

import com.MyProject.common.redis.ToggleDebounceService;
import com.MyProject.post.post_service.dto.event.NotificationEvent;
import com.MyProject.post.post_service.entity.Outbox;
import com.MyProject.post.post_service.entity.Post;
import com.MyProject.post.post_service.repository.OutboxRepository;
import com.MyProject.post.post_service.repository.PostLikeRepository;
import com.MyProject.post.post_service.repository.PostRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Drains {@link PostLikeNotificationService}'s debounce queue: for every (postId, actorUserId)
 * pair whose debounce window elapsed, re-reads the current like state from Mongo (the actual
 * source of truth) and only notifies the post owner if the post is still liked at that moment.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostLikeNotificationFlushJob {
    private static final int BATCH_SIZE = 200;

    private final ToggleDebounceService toggleDebounceService;
    private final PostLikeRepository postLikeRepository;
    private final PostRepository postRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 1000)
    public void flushPendingLikeNotifications() {
        List<String> due = toggleDebounceService.pollDue(PostLikeNotificationService.QUEUE_KEY, BATCH_SIZE);
        for (String member : due) {
            try {
                processMember(member);
            } catch (Exception e) {
                log.error("Failed to flush like-notification for member {}", member, e);
            }
        }
    }

    private void processMember(String member) {
        String[] parts = PostLikeNotificationService.splitMember(member);
        if (parts.length != 2) {
            return;
        }
        String postId = parts[0];
        String actorUserId = parts[1];

        if (!postLikeRepository.existsByPostIdAndUserId(postId, actorUserId)) {
            return;
        }

        Post post = postRepository.findById(postId).orElse(null);
        if (post == null || actorUserId.equals(post.getUserId())) {
            return;
        }

        NotificationEvent event = NotificationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .typeNotification("SOCIAL_LIKE")
                .userIdSender(actorUserId)
                .toUserIds(List.of(post.getUserId()))
                .build();

        try {
            outboxRepository.save(Outbox.builder()
                    .id(UUID.randomUUID().toString())
                    .aggregateId(postId)
                    .topic("notification.events")
                    .payload(objectMapper.writeValueAsString(event))
                    .build());
        } catch (Exception e) {
            log.error("Failed to save like-notification outbox for post {}", postId, e);
        }
    }
}
