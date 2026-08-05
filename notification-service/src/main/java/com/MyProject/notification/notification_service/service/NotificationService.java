package com.MyProject.notification.notification_service.service;

import com.MyProject.common.redis.RedisService;
import com.MyProject.notification.notification_service.dto.event.NotificationEvent;
import com.MyProject.notification.notification_service.dto.event.NotificationSocket;
import com.MyProject.notification.notification_service.dto.response.NotificationResponse;
import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.common.dto.response.UserProfileResponse;
import com.MyProject.notification.notification_service.entity.Notification;
import com.MyProject.notification.notification_service.entity.TypeNotification;
import com.MyProject.notification.notification_service.exception.AppException;
import com.MyProject.notification.notification_service.exception.ErrorCode;
import com.MyProject.notification.notification_service.mapper.NotificationMapper;
import com.MyProject.notification.notification_service.repository.NotificationRepository;
import com.MyProject.common.security.SecurityUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NotificationService {
    NotificationRepository notificationRepository;
    NotificationMapper notificationMapper;
    OutboxEventPublisher outboxEventPublisher;
    NotificationProfileService notificationProfileService;
    RedisService redisService;

    private static final String UNREAD_COUNT_KEY_PREFIX = "notification:unread-count:";
    private static final String PRESENCE_KEY_PREFIX = "presence:active-chat:";
    private static final String CHAT_NOTIF_OPEN_KEY_PREFIX = "chat-notif-open:";
    private static final long CHAT_NOTIF_POINTER_TTL_HOURS = 24;

    private String getUserId() {
        return SecurityUtils.getCurrentUserId();
    }

    private List<NotificationResponse> toNotificationResponses(List<Notification> notification,
                                                        Map<String, UserProfileResponse> profileMaps,
                                                        String currentUserId) {
        return notification.stream().map(noti -> {
            NotificationResponse response = notificationMapper.toNotificationResponse(noti);
            UserProfileResponse sender = profileMaps.get(noti.getUserIdSender());
            response.setType(noti.getType().name());
            
            // Set default values if sender is null
            if (sender != null) {
                response.setDisplayNameSender(sender.getDisplayName() != null ? sender.getDisplayName() : "Unknown User");
                response.setAvatarSender(sender.getAvatar() != null ? sender.getAvatar() : "");
            } else {
                response.setDisplayNameSender("Unknown User");
                response.setAvatarSender("");
            }
            
            Boolean isRead = noti.getRecipientReadMap().getOrDefault(currentUserId, null) != null;
            response.setRead(isRead);
            response.setMessage(buildContent(noti.getType(), response.getDisplayNameSender(), noti.getCount()));
            response.setCreatedAt(noti.getCreatedAt() != null ? noti.getCreatedAt().toString() : "");
            return response;
        }).toList();
    }

    public PageResponse<NotificationResponse> getMyNotifications(int page, int size) {
        String userId = getUserId();
        Pageable pageable = PageRequest.of(page - 1, size);

        Page<Notification> notificationsPage =
                notificationRepository.findByToUserIdsOrderByCreatedAtDesc(userId, pageable);

        List<String> senderIds = notificationsPage.getContent().stream()
                .map(Notification::getUserIdSender)
                .distinct()
                .toList();

        Map<String, UserProfileResponse> profilesMap = notificationProfileService.getProfiles(senderIds);

        return PageResponse.<NotificationResponse>builder()
                .currentPage(page)
                .pageSize(size)
                .totalPages(notificationsPage.getTotalPages())
                .totalElement(notificationsPage.getTotalElements())
                .data(toNotificationResponses(notificationsPage.getContent(), profilesMap, userId))
                .build();
    }

    public Long getUnreadCount() {
        String userId = getUserId();
        String key = UNREAD_COUNT_KEY_PREFIX + userId;
        
        // Try to get from Redis
        try {
            Long cachedCount = redisService.get(key, new TypeReference<Long>() {});
            if (cachedCount != null) {
                return cachedCount;
            }
        } catch (Exception e) {
            log.error("Failed to get unread count from cache for userId: {}", userId, e);
        }
        
        // If not in cache, fetch from MongoDB and cache it
        Long unreadCount = notificationRepository.countUnreadByUserId(userId);
        try {
            redisService.setWithExpiration(key, unreadCount, 5, TimeUnit.MINUTES); // Cache for 5 minutes
        } catch (Exception e) {
            log.error("Failed to cache unread count for userId: {}", userId, e);
        }
        
        return unreadCount;
    }

    public void markAllAsRead() {
        String userId = getUserId();
        notificationRepository.markAllAsRead(userId, LocalDateTime.now());
        
        // Invalidate cache
        String key = UNREAD_COUNT_KEY_PREFIX + userId;
        try {
            redisService.delete(key);
        } catch (Exception e) {
            log.error("Failed to invalidate unread count cache for userId: {}", userId, e);
        }
    }

    @Transactional
    public void createNotification(NotificationEvent event) {
        TypeNotification typeNotification = TypeNotification.valueOf(event.getTypeNotification());

        if (typeNotification == TypeNotification.NEW_CHAT && event.getConversationId() != null) {
            handleChatNotification(event, typeNotification);
            return;
        }

        Map<String, LocalDateTime> recipientReadMap = new java.util.HashMap<>();
        event.getToUserIds().forEach(userId -> recipientReadMap.put(userId, null));

        Notification notification = Notification.builder()
                .type(typeNotification)
                .userIdSender(event.getUserIdSender())
                .toUserIds(event.getToUserIds())
                .recipientReadMap(recipientReadMap)
                .build();

        // Save notification first (in transaction)
        var notificationSaved = notificationRepository.save(notification);

        // Try to get profile, but don't fail the transaction if it fails (use defaults)
        String displayNameSender = "Unknown User";
        String avatarSender = "";
        try {
            UserProfileResponse userProfileResponse = notificationProfileService.getProfile(event.getUserIdSender());
            if (userProfileResponse != null) {
                displayNameSender = userProfileResponse.getDisplayName() != null ? userProfileResponse.getDisplayName() : "Unknown User";
                avatarSender = userProfileResponse.getAvatar() != null ? userProfileResponse.getAvatar() : "";
            }
        } catch (Exception e) {
            log.error("Failed to get sender profile for notification: {}", event.getUserIdSender(), e);
        }

        NotificationSocket notificationSocket = new NotificationSocket(
                        displayNameSender,
                        avatarSender,
                        typeNotification.name(),
                        typeNotification.getTitle(),
                        typeNotification.getContent().replace("{sender}", displayNameSender),
                        event.getToUserIds());

        // Publish to outbox (in same transaction)
        outboxEventPublisher.publish(notificationSaved.getId(), "notification", notificationSocket);
        
        // Invalidate unread count cache for all recipients
        event.getToUserIds().forEach(userId -> {
            String key = UNREAD_COUNT_KEY_PREFIX + userId;
            try {
                redisService.delete(key);
            } catch (Exception e) {
                log.error("Failed to invalidate unread count cache for userId: {}", userId, e);
            }
        });
    }

    private void handleChatNotification(NotificationEvent event, TypeNotification typeNotification) {
        String senderId = event.getUserIdSender();
        String conversationId = event.getConversationId();

        String displayNameSender = "Unknown User";
        String avatarSender = "";
        try {
            UserProfileResponse userProfileResponse = notificationProfileService.getProfile(senderId);
            if (userProfileResponse != null) {
                displayNameSender = userProfileResponse.getDisplayName() != null ? userProfileResponse.getDisplayName() : "Unknown User";
                avatarSender = userProfileResponse.getAvatar() != null ? userProfileResponse.getAvatar() : "";
            }
        } catch (Exception e) {
            log.error("Failed to get sender profile for chat notification: {}", senderId, e);
        }

        for (String recipientId : event.getToUserIds()) {
            if (isUserActiveInAnyChat(recipientId)) {
                // Already covered by the realtime chat.messages -> update-conversation-list pipeline.
                continue;
            }

            Notification notification = upsertChatNotification(recipientId, senderId, conversationId, typeNotification);

            NotificationSocket notificationSocket = new NotificationSocket(
                    displayNameSender,
                    avatarSender,
                    typeNotification.name(),
                    typeNotification.getTitle(),
                    buildContent(typeNotification, displayNameSender, notification.getCount()),
                    List.of(recipientId));

            outboxEventPublisher.publish(notification.getId(), "notification", notificationSocket);

            String key = UNREAD_COUNT_KEY_PREFIX + recipientId;
            try {
                redisService.delete(key);
            } catch (Exception e) {
                log.error("Failed to invalidate unread count cache for userId: {}", recipientId, e);
            }
        }
    }

    private boolean isUserActiveInAnyChat(String userId) {
        try {
            Map<Object, Object> activeConversations = redisService.hashGetAll(PRESENCE_KEY_PREFIX + userId);
            return activeConversations != null && !activeConversations.isEmpty();
        } catch (Exception e) {
            log.error("Failed to read chat presence for userId: {}", userId, e);
            return false;
        }
    }

    private Notification upsertChatNotification(String recipientId, String senderId, String conversationId, TypeNotification type) {
        String pointerKey = chatNotifOpenKey(recipientId, conversationId);
        String existingId = redisService.getAsString(pointerKey);

        if (existingId != null) {
            Notification existing = notificationRepository.findById(existingId).orElse(null);
            boolean stillOpenForSameSender = existing != null
                    && existing.getRecipientReadMap().getOrDefault(recipientId, null) == null
                    && senderId.equals(existing.getUserIdSender());

            if (stillOpenForSameSender) {
                existing.setCount((existing.getCount() == null ? 1 : existing.getCount()) + 1);
                existing.setCreatedAt(LocalDateTime.now());
                Notification saved = notificationRepository.save(existing);
                redisService.setWithExpiration(pointerKey, saved.getId(), CHAT_NOTIF_POINTER_TTL_HOURS, TimeUnit.HOURS);
                return saved;
            }
        }

        Map<String, LocalDateTime> recipientReadMap = new java.util.HashMap<>();
        recipientReadMap.put(recipientId, null);

        Notification created = Notification.builder()
                .type(type)
                .userIdSender(senderId)
                .toUserIds(List.of(recipientId))
                .recipientReadMap(recipientReadMap)
                .conversationId(conversationId)
                .count(1)
                .build();

        Notification saved = notificationRepository.save(created);
        redisService.setWithExpiration(pointerKey, saved.getId(), CHAT_NOTIF_POINTER_TTL_HOURS, TimeUnit.HOURS);
        return saved;
    }

    // Clears the aggregation pointer so the next message starts a fresh notification instead of appending to a read one.
    public void markChatConversationRead(String userId, String conversationId) {
        String pointerKey = chatNotifOpenKey(userId, conversationId);
        String notificationId = redisService.getAsString(pointerKey);
        if (notificationId == null) {
            return;
        }

        notificationRepository.findById(notificationId).ifPresent(notification -> {
            if (notification.getRecipientReadMap().getOrDefault(userId, null) == null) {
                notification.getRecipientReadMap().put(userId, LocalDateTime.now());
                notificationRepository.save(notification);

                String key = UNREAD_COUNT_KEY_PREFIX + userId;
                try {
                    redisService.delete(key);
                } catch (Exception e) {
                    log.error("Failed to invalidate unread count cache for userId: {}", userId, e);
                }
            }
        });

        redisService.delete(pointerKey);
    }

    private String chatNotifOpenKey(String recipientId, String conversationId) {
        return CHAT_NOTIF_OPEN_KEY_PREFIX + recipientId + ":" + conversationId;
    }

    private String buildContent(TypeNotification type, String displayNameSender, Integer count) {
        if (type == TypeNotification.NEW_CHAT && count != null && count > 1) {
            return displayNameSender + " đã gửi " + count + " tin nhắn mới";
        }
        return type.getContent().replace("{sender}", displayNameSender);
    }
}
