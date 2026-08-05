package com.MyProject.socket_service.service;

import com.MyProject.common.dto.response.UserProfileResponse;
import com.MyProject.common.redis.RedisService;
import com.MyProject.socket_service.dto.event.ConversationCreatedEvent;
import com.MyProject.socket_service.dto.event.ConversationSeenEvent;
import com.MyProject.socket_service.dto.event.MessageCreatedEvent;
import com.MyProject.socket_service.dto.event.NotificationSocket;
import com.MyProject.socket_service.dto.event.ProfileSocketUpdatedEvent;
import com.MyProject.socket_service.dto.response.ChatMessageResponse;
import com.MyProject.socket_service.dto.response.NotificationUI;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.kafka.support.Acknowledgment;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SocketKafkaService {
    CustomWebSocketHandler webSocketSessionService;
    SocketDownstreamService socketDownstreamService;
    RedisService redisService;
    ObjectMapper objectMapper;

    @KafkaListener(topics = "chat.messages")
    public void consumeMessage(String message, Acknowledgment ack) {
        try {
            MessageCreatedEvent event = objectMapper.readValue(message, MessageCreatedEvent.class);

            String action = switch (event.getEventType()) {
                case "chat.message.created" -> "new-message";
                case "chat.message.updated" -> "update-message";
                case "chat.message.deleted" -> "delete-message";
                default -> "unknown";
            };

            log.info("Received event {} for conversation: {}",
                    action,
                    event.getMessage().getConversationId());

            handleChatMessageEvent(event, action);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process chat message event", e);
            throw new RuntimeException(e);
        }
    }

    @KafkaListener(topics = "socket.events")
    public void consumeSocketEvent(String payload, Acknowledgment ack) {
        try {
            ProfileSocketUpdatedEvent event = objectMapper.readValue(payload, ProfileSocketUpdatedEvent.class);
            String userId = event.getUserId();

            log.info("Received profile socket event for user: {}", userId);

            // 1. Read latest profile from Redis (since profile-service already updated it!)
            String keyProfile = "profile:user:" + userId;
            UserProfileResponse cachedProfile = redisService.get(keyProfile, new com.fasterxml.jackson.core.type.TypeReference<UserProfileResponse>() {});
            UserProfileResponse profileToBroadcast;

            // If not in Redis, fetch from profile-service (just in case)
            if (cachedProfile == null) {
                var fetchedProfiles = socketDownstreamService.getBulkUserProfiles(java.util.Set.of(userId));
                if (!fetchedProfiles.isEmpty()) {
                    profileToBroadcast = fetchedProfiles.get(userId);
                    // Update cache with fetched data
                    try {
                        redisService.setWithExpiration(keyProfile, profileToBroadcast, 12, TimeUnit.HOURS);
                    } catch (Exception e) {
                        log.error("Failed to update cache for userId: {}", userId, e);
                    }
                } else {
                    // If we couldn't fetch, we can't broadcast - just acknowledge and return
                    log.warn("Couldn't fetch profile for userId: {}", userId);
                    ack.acknowledge();
                    return;
                }
            } else {
                profileToBroadcast = cachedProfile;
            }

            // 2. Broadcast PROFILE_UPDATED to ALL connected users
            webSocketSessionService.broadcast("PROFILE_UPDATED", profileToBroadcast);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process profile socket event", e);
            throw new RuntimeException(e);
        }
    }

    @KafkaListener(topics = "conversation.sync")
    public void consumeConversationCreated(String payload, Acknowledgment ack) {
        try {
            ConversationCreatedEvent event = objectMapper.readValue(payload, ConversationCreatedEvent.class);
            if (event.getUserIds() == null || event.getUserIds().isEmpty()) {
                ack.acknowledge();
                return;
            }

            log.info("Received created conversation event for conversation: {}", event.getId());
            for (String userId : event.getUserIds()) {
                webSocketSessionService.sendToUser(userId, "conversation-created", event);
            }
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process created conversation event", e);
            throw new RuntimeException(e);
        }
    }

    private void handleChatMessageEvent(MessageCreatedEvent event, String socketEvent) {
        // 1. Enrich user info (Name, Avatar)
        enrichSenderInfo(event);

        String conversationId = event.getMessage().getConversationId();
        ChatMessageResponse message = event.getMessage();

        // 2. Gửi tin nhắn đến toàn bộ Room (Để cập nhật nội dung chat hiện tại)
        webSocketSessionService.sendToRoom(conversationId, socketEvent, message);

        // 3. Xử lý cập nhật danh sách hội thoại (Conversation List) real-time
        // Nếu là tin nhắn mới (create) -> Luôn cập nhật lastMessage
        // Nếu là update/delete -> Chỉ cập nhật nếu tin nhắn đó đang là tin nhắn cuối cùng (Dựa trên logic business đã xử lý ở chat-service)
        // Lưu ý: Ở phía socket-service, chúng ta tin tưởng vào event từ chat-service đã filter đúng.
        for (String receiverId : event.getReceiverIds()) {
            // Cập nhật "lastMessage" hiển thị ở danh sách chat bên ngoài cho tất cả người nhận
            webSocketSessionService.sendToUser(receiverId, "update-conversation-list", message);

            if (!receiverId.equals(message.getSenderId())) {
                boolean isUserInRoom = webSocketSessionService.isUserInRoom(receiverId, conversationId);
                if (!isUserInRoom) {
                    // Nếu user KHÔNG ở trong phòng chat -> Gửi thông báo/badge
                    webSocketSessionService.sendToUser(receiverId, "new-notification", message);
                }
            }
        }
    }

    private void enrichSenderInfo(MessageCreatedEvent event) {
        String userId = event.getMessage().getSenderId();
        String keyProfile = "profile:user:" + userId;

        try {
            UserProfileResponse dataProfile = redisService.get(keyProfile, new TypeReference<UserProfileResponse>() {});

            if (dataProfile == null) {
                var fetchedProfiles = socketDownstreamService.getBulkUserProfiles(Set.of(userId));

                if (!fetchedProfiles.isEmpty()) {
                    UserProfileResponse profile = fetchedProfiles.get(userId);
                    if (profile != null) {
                        redisService.setWithExpiration(
                                keyProfile,
                                profile,
                                12,
                                TimeUnit.HOURS
                        );
                        event.getMessage().setSenderName(profile.getDisplayName());
                        event.getMessage().setSenderAvatar(profile.getAvatar());
                    }
                }
            } else {
                event.getMessage().setSenderName(dataProfile.getDisplayName());
                event.getMessage().setSenderAvatar(dataProfile.getAvatar());
            }
            event.getMessage().setMe(false);
        } catch (Exception e) {
            log.error("Error enriching sender info for userId: {}", userId, e);
        }
    }

    @KafkaListener(topics = "chat.conversation.seen")
    public void consumeSeenEvent(String payload, Acknowledgment ack) {
        try {
            ConversationSeenEvent event = objectMapper.readValue(payload, ConversationSeenEvent.class);
            log.info("Received seen event for conversation: {} by user: {}", event.getConversationId(), event.getUserId());

            enrichSeenInfo(event);

            // Gửi sự kiện seen đến toàn bộ room (Chỉ gọi 1 lần)
            // Những người đang mở chat sẽ thấy avatar của user kia di chuyển real-time
            webSocketSessionService.sendToRoom(event.getConversationId(), "seen-message", event);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process seen event", e);
            throw new RuntimeException(e);
        }
    }

    private void enrichSeenInfo(ConversationSeenEvent event) {
        String userId = event.getUserId();
        String keyProfile = "profile:user:" + userId;

        try {
            UserProfileResponse dataProfile = redisService.get(keyProfile, new TypeReference<UserProfileResponse>() {});

            if (dataProfile == null) {
                var fetchedProfiles = socketDownstreamService.getBulkUserProfiles(Set.of(userId));

                if (!fetchedProfiles.isEmpty()) {
                    UserProfileResponse profile = fetchedProfiles.get(userId);
                    if (profile != null) {
                        redisService.setWithExpiration(
                                keyProfile,
                                profile,
                                1,
                                TimeUnit.HOURS
                        );
                        event.setUserName(profile.getDisplayName());
                        event.setUserAvatar(profile.getAvatar());
                    }
                }
            } else {
                event.setUserName(dataProfile.getDisplayName());
                event.setUserAvatar(dataProfile.getAvatar());
            }
        } catch (Exception e) {
            log.error("Error enriching seen info for userId: {}", userId, e);
        }
    }

    @KafkaListener(topics = "notification")
    public void consumeNotification(String payload, Acknowledgment ack) {
        try {
            NotificationSocket event = objectMapper.readValue(payload, NotificationSocket.class);
            log.info("Received notification event for {} users", event.toUserIds().size());
            for (String userId : event.toUserIds()) {
                webSocketSessionService.sendToUser(
                        userId,
                        "notification",
                        NotificationUI.builder()
                                .displayNameSender(event.displayNameSender())
                                .avatarSender(event.avatarSender())
                                .type(event.type())
                                .title(event.title())
                                .content(event.content())
                                .build()
                );
            }
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process notification event", e);
            throw new RuntimeException(e);
        }
    }

    @KafkaListener(topics = {"user.online", "user.offline"})
    public void consumeUserStatusChange(String payload, @org.springframework.messaging.handler.annotation.Header(org.springframework.kafka.support.KafkaHeaders.RECEIVED_TOPIC) String topic, Acknowledgment ack) {
        try {
            Map<String, Object> data = objectMapper.readValue(payload, Map.class);
            String userId = (String) data.get("userId");
            boolean isOnline = "user.online".equals(topic);

            log.info("User {} status changed to {}", userId, isOnline ? "ONLINE" : "OFFLINE");

            // Broadcast status change to all connected users
            webSocketSessionService.broadcast("user-status-changed", Map.of(
                    "userId", userId,
                    "status", isOnline ? "ONLINE" : "OFFLINE"
            ));
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process user status change event", e);
            throw new RuntimeException(e);
        }
    }
}
