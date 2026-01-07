package com.MyProject.post.post_service.job;

import com.MyProject.common.dto.StatusChangeData;
import com.MyProject.common.dto.request.NotificationRequest;
import com.MyProject.common.entity.TypeNotification;
import com.MyProject.post.post_service.entity.Post;
import com.MyProject.post.post_service.exception.AppException;
import com.MyProject.post.post_service.exception.ErrorCode;
import com.MyProject.post.post_service.repository.PostRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Job để cập nhật trạng thái Post
 * Job này sẽ được trigger 2 lần:
 * 1. Lần 1: Tại startTime → Chuyển status: UPCOMING → ONGOING
 * 2. Lần 2: Tại endTime → Chuyển status: ONGOING → COMPLETED
 */
@Slf4j
@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
public class UpdatePostStatusJob implements Job {
    PostRepository postRepository;
    KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void execute(JobExecutionContext context) {
        JobDataMap dataMap = context.getJobDetail().getJobDataMap();
        String postId = dataMap.getString("postId");
        String action = dataMap.getString("action");

        log.info("🚀 [Quartz] Executing UpdatePostStatusJob for post: {}, action: {}",
                postId, action);

        try {
            var post = postRepository.findById(postId)
                    .orElseThrow(() -> new AppException(ErrorCode.POST_NOT_FOUND));

            switch (action) {
                case "START":
                    handleStart(post);
                    break;
                case "END":
                    handleEnd(post);
                    break;
                default:
                    throw new AppException(ErrorCode.ACTION_NOT_FOUND);
            }

        } catch (Exception e) {
            log.error("❌ Error updating post status {}: {}", postId, e.getMessage(), e);
            throw new AppException(ErrorCode.JOB_EXECUTION_FAILED);
        }
    }

    /**
     * Xử lý khi đến startTime (Thời điểm A)
     * UPCOMING → ONGOING
     */
    private void handleStart(Post post) {
        if (!post.getStatus().equals("Up coming")) {
            log.warn("⚠️ Post {} is not UPCOMING (current: {}), skipping START",
                    post.getId(), post.getStatus());
            return;
        }

        post.setStatus("On going");

        postRepository.save(post);

        String message = "⏳ Post: " + post.getTitle() + " is Ongoing!";
        StatusChangeData data = new StatusChangeData(
                post.getId(), post.getListUserJoin(), message);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("message", message);
        metadata.put("postId", post.getId());

        NotificationRequest request = NotificationRequest.builder()
                .typeNotification(TypeNotification.STATUS_CHANGE)
                .userIdSender()
                .toUserIds(post.getListUserJoin())
                .metadata(metadata)
                .build();

        kafkaTemplate.send("create-notification", request);
    }

    /**
     * Xử lý khi đến endTime (Thời điểm B)
     * ONGOING → COMPLETED
     */
    private void handleEnd(Post post) {
        if (!post.getStatus().equals("On going")) {
            log.warn("⚠️ Post {} is not ONGOING (current: {}), skipping END",
                    post.getId(), post.getStatus());
            return;
        }

        post.setStatus("Completed");

        postRepository.save(post);

        String message = "✅ Post: " + post.getTitle() + " is Completed!";

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("message", message);
        metadata.put("postId", post.getId());

        NotificationRequest request = NotificationRequest.builder()
                .typeNotification(TypeNotification.STATUS_CHANGE)
                .userIdSender()
                .toUserIds(post.getListUserJoin())
                .metadata(metadata)
                .build();

        kafkaTemplate.send("create-notification", request);
    }
}
