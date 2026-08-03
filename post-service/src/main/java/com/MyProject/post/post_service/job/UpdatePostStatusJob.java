package com.MyProject.post.post_service.job;

import com.MyProject.post.post_service.dto.event.NotificationEvent;
import com.MyProject.post.post_service.entity.ActionConfig;
import com.MyProject.post.post_service.entity.Post;
import com.MyProject.post.post_service.exception.AppException;
import com.MyProject.post.post_service.exception.ErrorCode;
import com.MyProject.post.post_service.repository.PostRepository;
import com.MyProject.post.post_service.entity.Outbox;
import com.MyProject.post.post_service.repository.OutboxRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
public class UpdatePostStatusJob implements Job {
    PostRepository postRepository;
    OutboxRepository outboxRepository;
    ObjectMapper objectMapper;

    Map<String, ActionConfig> actionConfigs = Map.of(
            "START", new ActionConfig("Up coming", "On going", "⏳ Post: %s is Ongoing!"),
            "END",   new ActionConfig("On going", "Completed", "✅ Post: %s is Completed!")
    );

    @Override
    public void execute(JobExecutionContext context) {
        JobDataMap dataMap = context.getJobDetail().getJobDataMap();
        String postId = dataMap.getString("postId");
        String action = dataMap.getString("action");

        var post = postRepository.findById(postId)
                .orElseThrow(() -> new AppException(ErrorCode.POST_NOT_FOUND));

        handleStatusTransition(post, action);
    }

    private void handleStatusTransition(Post post, String action) {
        ActionConfig config = actionConfigs.get(action);
        if (config == null) return;

        if (!post.getStatus().equals(config.requiredStatus())) {
            log.warn("⚠️ Post {} status is {}, skipping action {}", post.getId(), post.getStatus(), action);
            return;
        }

        post.setStatus(config.newStatus());
        postRepository.save(post);
        log.info("🚀 Post {} updated to {}", post.getId(), config.newStatus());

        sendNotification(post);
    }

    private void sendNotification(Post post) {
        NotificationEvent event = NotificationEvent.builder()
                .eventId(java.util.UUID.randomUUID().toString())
                .typeNotification("STATUS_CHANGE")
                .userIdSender(post.getUserId())
                .toUserIds(post.getListUsersJoin())
                .build();

        try {
            outboxRepository.save(Outbox.builder()
                    .aggregateId(post.getId())
                    .topic("notification.events")
                    .payload(objectMapper.writeValueAsString(event))
                    .build());
        } catch (Exception e) {
            log.error("Failed to save notification event to outbox", e);
        }
    }
}
