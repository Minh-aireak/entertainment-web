package com.MyProject.post.post_service.service;

import com.MyProject.post.post_service.exception.AppException;
import com.MyProject.post.post_service.exception.ErrorCode;
import com.MyProject.post.post_service.job.UpdatePostStatusJob;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;
import org.quartz.*;

import java.time.Instant;
import java.util.Date;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PostJobScheduler {
    Scheduler scheduler;

    public String schedule(String postId, String title, Instant executeTime, String action) {
        String jobKeyStr = action.toLowerCase() + "-post-" + postId;

        JobDetail job = JobBuilder.newJob(UpdatePostStatusJob.class)
                .withIdentity(jobKeyStr, "post-status-jobs")
                .withDescription(action + " post: " + title)
                .usingJobData("postId", postId)
                .usingJobData("action", action)
                .storeDurably(false)
                .build();

        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity("trigger-" + jobKeyStr, "post-triggers")
                .startAt(Date.from(executeTime))
                .forJob(job)
                .build();

        try {
            if (scheduler.checkExists(job.getKey())) {
                scheduler.deleteJob(job.getKey());
            }
            scheduler.scheduleJob(job, trigger);
        } catch (SchedulerException e) {
            throw new AppException(ErrorCode.SCHEDULER_EXCEPTION);
        }

        return jobKeyStr;
    }
}
