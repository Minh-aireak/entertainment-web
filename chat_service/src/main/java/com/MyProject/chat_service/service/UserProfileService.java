package com.MyProject.chat_service.service;

import event.dto.ProfileUpdatedEvent;
import com.MyProject.chat_service.entity.ChatMessage;
import com.MyProject.chat_service.entity.Conversation;
import com.MyProject.chat_service.entity.ParticipantInfo;
import com.MyProject.chat_service.repository.ChatMessageRepository;
import com.MyProject.chat_service.repository.ConversationRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserProfileService {
    ConversationRepository conversationRepository;
    ChatMessageRepository chatMessageRepository;

    @Transactional
    public void updateUserProfile(ProfileUpdatedEvent event) {
        updateUserInConversations(event);
        updateUserInChatMessages(event);
    }

    private void updateUserInConversations(ProfileUpdatedEvent event) {
        List<Conversation> conversations = conversationRepository.findAllByUserId(event.getUserId());
        
        for (Conversation conversation : conversations) {
            for (ParticipantInfo participant : conversation.getParticipantInfos()) {
                if (event.getUserId().equals(participant.getUserId())) {
                    participant.setDisplayName(event.getDisplayName());
                    participant.setAvatar(event.getAvatar());
                }
            }
            conversationRepository.save(conversation);
        }
    }

    private void updateUserInChatMessages(ProfileUpdatedEvent event) {
        List<ChatMessage> messages = chatMessageRepository.findAllBySenderUserId(event.getUserId());
        
        for (ChatMessage message : messages) {
            ParticipantInfo sender = message.getSender();
            if (event.getUserId().equals(sender.getUserId())) {
                sender.setDisplayName(event.getDisplayName());
                sender.setAvatar(event.getAvatar());
                
                chatMessageRepository.save(message);
            }
        }
    }
}