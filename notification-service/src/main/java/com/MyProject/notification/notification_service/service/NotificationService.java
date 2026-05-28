package com.MyProject.notification.notification_service.service;

import com.MyProject.common.dto.request.BulkUserProfileRequest;
import com.MyProject.common.redis.RedisService;
import com.MyProject.notification.notification_service.dto.event.NotificationEvent;
import com.MyProject.notification.notification_service.dto.event.NotificationSocket;
import com.MyProject.notification.notification_service.dto.response.NotificationResponse;
import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.common.dto.response.UserProfileResponse;
import com.MyProject.notification.notification_service.entity.Notification;
import com.MyProject.notification.notification_service.exception.AppException;
import com.MyProject.notification.notification_service.exception.ErrorCode;
import com.MyProject.notification.notification_service.mapper.NotificationMapper;
import com.MyProject.notification.notification_service.repository.NotificationRepository;
import com.MyProject.notification.notification_service.repository.httpclient.ProfileClient;
import com.MyProject.notification.notification_service.entity.Outbox;
import com.MyProject.notification.notification_service.repository.OutboxRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NotificationService {
    NotificationRepository notificationRepository;
    NotificationMapper notificationMapper;
    ProfileClient profileClient;
    OutboxRepository outboxRepository;
    RedisService redisService;

    private String getUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        Jwt jwt = ((JwtAuthenticationToken) authentication).getToken();
        return jwt.getClaim("userId");
    }

    private List<NotificationResponse> toNotificationResponses(List<Notification> notification,
                                                        Map<String, UserProfileResponse> profileMaps,
                                                        String currentUserId) {
        return notification.stream().map(noti -> {
            NotificationResponse response = notificationMapper.toNotificationResponse(noti);
            UserProfileResponse sender = profileMaps.get(noti.getUserIdSender());
            response.setType(noti.getType().name());
            response.setDisplayNameSender(sender.getDisplayName());
            response.setAvatarSender(sender.getAvatar());
            Boolean isRead = noti.getRecipientReadMap().getOrDefault(currentUserId, null) != null;
            response.setRead(isRead);
            response.setMessage(noti.getType().getContent().replace("{sender}", response.getDisplayNameSender()));
            response.setCreatedAt(noti.getCreatedAt().toString());
            return response;
        }).toList();
    }

    public PageResponse<NotificationResponse> getMyNotifications(int page, int size) {
        String userId = getUserId();
        Pageable pageable = PageRequest.of(page - 1, size);

        Page<Notification> notificationsPage =
                notificationRepository.findByToUserIdsInOrderByCreatedAtDesc(userId, pageable);

        List<String> senderIds = notificationsPage.getContent().stream()
                .map(Notification::getUserIdSender)
                .distinct()
                .toList();

        // Check Redis
        List<String> keys = senderIds.stream()
                .map(id -> "profile:user:" + id)
                .toList();

        List<Object> cachedValues = redisService.multiGet(keys);

        Map<String, UserProfileResponse> profilesMap = new HashMap<>();
        List<String> missingUserIds = new ArrayList<>();

        for (int i = 0; i < senderIds.size(); i++) {
            Object cached = cachedValues.get(i);
            if (Objects.isNull(cached)) {
                missingUserIds.add(senderIds.get(i));
            } else {
                UserProfileResponse profile = (UserProfileResponse) cached;
                profilesMap.put(profile.getUserId(), profile);
            }
        }

        // Gọi API cho phần bị thiếu
        if (!missingUserIds.isEmpty()) {
            try {
                Map<String, UserProfileResponse> fetchedProfiles =
                        profileClient.getBulkUserProfiles(BulkUserProfileRequest.builder()
                                        .userIds(new HashSet<>(missingUserIds))
                                        .build())
                                .getResult();

                // Cache lại
                fetchedProfiles.forEach((id, profile) ->
                        redisService.setWithExpiration(
                                "profile:user:" + id, profile, 1, TimeUnit.HOURS));

                profilesMap.putAll(fetchedProfiles);
            } catch (Exception e) {
                log.error("Failed to fetch user profiles for notifications: {}", missingUserIds, e);
                throw new AppException(ErrorCode.BULK_USER_PROFILE);
            }
        }

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
        return notificationRepository.countUnreadByUserId(userId);
    }

    public void markAllAsRead() {
        String userId = getUserId();
        notificationRepository.markAllAsRead(userId, LocalDateTime.now());
    }

    @Transactional
    public void createNotification(NotificationEvent event) {
        Map<String, LocalDateTime> recipientReadMap = new HashMap<>();
        event.getToUserIds().forEach(userId -> recipientReadMap.put(userId, null));

        Notification notification = Notification.builder()
                .type(event.getTypeNotification())
                .userIdSender(event.getUserIdSender())
                .recipientReadMap(recipientReadMap)
                .build();

        UserProfileResponse userProfileResponse =
                (UserProfileResponse) redisService.get("profile:user:" + event.getUserIdSender());

        if (Objects.isNull(userProfileResponse)) {
            Map<String, UserProfileResponse> data = profileClient
                    .getBulkUserProfiles(new BulkUserProfileRequest(Set.of(event.getUserIdSender()))).getResult();

            userProfileResponse = data.get(event.getUserIdSender());
            redisService.setWithExpiration("profile:user:" + event.getUserIdSender(), userProfileResponse, 1, TimeUnit.HOURS);
        }

        var notificationSaved = notificationRepository.save(notification);

        NotificationSocket notificationSocket = new NotificationSocket(
                        userProfileResponse.getDisplayName(),
                        userProfileResponse.getAvatar(),
                        event.getTypeNotification().getTitle(),
                        event.getTypeNotification().getContent(),
                        event.getToUserIds());
        
        // Save to outbox instead of sending directly to Kafka
        outboxRepository.save(Outbox.builder()
                .aggregateId(notificationSaved.getId())
                .topic("notification")
                .payload(notificationSocket)
                .build());
    }
}
