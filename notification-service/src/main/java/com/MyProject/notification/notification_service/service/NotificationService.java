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
            response.setMessage(noti.getType().getContent().replace("{sender}", response.getDisplayNameSender()));
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
}
