package com.MyProject.post.post_service.controller;

import com.MyProject.common.dto.ProfileUpdatedEvent;
import com.MyProject.post.post_service.service.PostService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EvenPostController {
    PostService postService;

    @KafkaListener(topics = "profile-updated")
    public void handleProfileUpdatedEvent(ProfileUpdatedEvent event) {
        postService.updateUserProfile(event);
    }
}
