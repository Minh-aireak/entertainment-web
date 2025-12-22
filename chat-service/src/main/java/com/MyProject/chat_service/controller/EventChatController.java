package com.MyProject.chat_service.controller;

import com.MyProject.chat_service.service.ConversationService;
import com.MyProject.common_dto.event.dto.ProfileUpdatedEvent;
import com.MyProject.chat_service.service.UserProfileService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EventChatController {
    UserProfileService userProfileService;
    ConversationService conversationService;

    @KafkaListener(topics = "profile-updated")
    public void handleProfileUpdatedEvent(ProfileUpdatedEvent event) {
        userProfileService.updateUserProfile(event);
    }

    @KafkaListener(topics = "friend-request-accepted")
    public void handleFriendRequestAccepted(List<String> ids) {
        conversationService.createConversation(ids);
    }
}
