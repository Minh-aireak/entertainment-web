package com.MyProject.chat_service.controller;

import event.dto.ProfileUpdatedEvent;
import com.MyProject.chat_service.service.UserProfileService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EventChatController {
    UserProfileService userProfileService;

    @KafkaListener(topics = "chat-profile-updated")
    public void handleProfileUpdatedEvent(ProfileUpdatedEvent event) {
        userProfileService.updateUserProfile(event);
    }
}
