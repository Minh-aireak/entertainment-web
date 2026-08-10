package com.MyProject.comment_service.service;

import com.MyProject.comment_service.entity.Comment;
import com.MyProject.comment_service.entity.Outbox;
import com.MyProject.comment_service.repository.CommentRepository;
import com.MyProject.comment_service.repository.OutboxRepository;
import com.MyProject.common.redis.ToggleDebounceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Drains {@link CommentReactionNotificationService}'s debounce queue: for every
 * (commentId, actorUserId) pair whose debounce window elapsed, re-reads the live reaction state
 * from Redis (the source of truth for reactions, see {@link CommentReactionService}) and only
 * notifies the comment owner if a reaction is still present at that moment.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentReactionNotificationFlushJob {
    private static final int BATCH_SIZE = 200;

    private final ToggleDebounceService toggleDebounceService;
    private final CommentReactionService commentReactionService;
    private final CommentRepository commentRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 1000)
    public void flushPendingReactionNotifications() {
        List<String> due = toggleDebounceService.pollDue(CommentReactionNotificationService.QUEUE_KEY, BATCH_SIZE);
        for (String member : due) {
            try {
                processMember(member);
            } catch (Exception e) {
                log.error("Failed to flush reaction-notification for member {}", member, e);
            }
        }
    }

    private void processMember(String member) {
        String[] parts = CommentReactionNotificationService.splitMember(member);
        if (parts.length != 2) {
            return;
        }
        String commentId = parts[0];
        String actorUserId = parts[1];

        if (commentReactionService.getUserReaction(commentId, actorUserId) == null) {
            return;
        }

        Comment comment = commentRepository.findById(commentId).orElse(null);
        if (comment == null || actorUserId.equals(comment.getUserId())) {
            return;
        }

        try {
            Map<String, Object> event = new HashMap<>();
            event.put("eventId", UUID.randomUUID().toString());
            event.put("typeNotification", "COMMENT_LIKE");
            event.put("userIdSender", actorUserId);
            event.put("toUserIds", new ArrayList<>(List.of(comment.getUserId())));

            outboxRepository.save(Outbox.builder()
                    .aggregateId(commentId)
                    .topic("notification.events")
                    .payload(objectMapper.writeValueAsString(event))
                    .build());
        } catch (Exception e) {
            log.error("Failed to save reaction-notification outbox for comment {}", commentId, e);
        }
    }
}
