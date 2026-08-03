package com.MyProject.post.post_service.service;

import com.MyProject.post.post_service.entity.Post;
import com.MyProject.post.post_service.exception.AppException;
import com.MyProject.post.post_service.exception.ErrorCode;
import com.MyProject.post.post_service.repository.PostRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PostJobManagementService {
    PostJobScheduler postJobScheduler;
    Scheduler scheduler;
    PostRepository postRepository;

    public void scheduleStatusJobs(Post post) {
        Instant startTime = post.getStartTime().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        Instant endTime = post.getEndTime().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        String currentStatus = post.getStatus();

        if ("Up coming".equals(currentStatus)) {
            String key = postJobScheduler.schedule(post.getId(), post.getTitle(), startTime, "START");
            post.setStartJobKey(key);
        }

        if ("Up coming".equals(currentStatus) || "On going".equals(currentStatus)) {
            String key = postJobScheduler.schedule(post.getId(), post.getTitle(), endTime, "END");
            post.setEndJobKey(key);
        }

        postRepository.save(post);
    }

    public void cancelPost(String postId) {
        var post = postRepository.findById(postId)
                .orElseThrow(() -> new AppException(ErrorCode.POST_NOT_FOUND));

        if (post.getStartJobKey() != null) {
            try {
                scheduler.deleteJob(new JobKey(post.getStartJobKey(), "post-status-jobs")
                );
            } catch (SchedulerException e) {
                throw new AppException(ErrorCode.DELETE_JOB);
            }
        }

        if (post.getEndJobKey() != null) {
            try {
                scheduler.deleteJob(new JobKey(post.getEndJobKey(), "post-status-jobs")
                );
            } catch (SchedulerException e) {
                throw new AppException(ErrorCode.DELETE_JOB);
            }
        }

        post.setStartJobKey(null);
        post.setEndJobKey(null);
        postRepository.save(post);
    }
}
