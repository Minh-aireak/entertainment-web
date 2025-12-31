package com.MyProject.notification.notification_service.service;

import com.MyProject.common_dto.event.dto.*;
import com.MyProject.common_dto.event.dto.request.BulkUserProfileRequest;
import com.MyProject.common_dto.event.dto.request.NotificationRequest;
import com.MyProject.common_dto.event.dto.response.NotificationResponse;
import com.MyProject.common_dto.event.dto.response.PageResponse;
import com.MyProject.common_dto.event.dto.response.UserProfileResponse;
import com.MyProject.notification.notification_service.entity.Notification;
import com.MyProject.notification.notification_service.exception.AppException;
import com.MyProject.notification.notification_service.exception.ErrorCode;
import com.MyProject.notification.notification_service.mapper.NotificationMapper;
import com.MyProject.notification.notification_service.repository.NotificationRepository;
import com.MyProject.notification.notification_service.repository.httpclient.ProfileClient;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NotificationService {
    NotificationRepository notificationRepository;
    NotificationMapper notificationMapper;
    ProfileClient profileClient;
    KafkaTemplate<String, Object> kafkaTemplate;

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
            response.setIsRead(isRead);
            return response;
        }).toList();
    }

    public void createNotification(NotificationRequest request) {
        Map<String, LocalDateTime> recipientReadMap = new HashMap<>();
        request.getToUserIds().forEach(userId -> recipientReadMap.put(userId, null));

        Notification notification = Notification.builder()
                .id(UUID.randomUUID().toString())
                .type(request.getTypeNotification())
                .userIdSender(request.getUserIdSender())
                .recipientReadMap(recipientReadMap)
                .message(request.getMetadata().get("message").toString())
                .createdAt(LocalDateTime.now())
                .build();

        notificationRepository.save(notification);

        List<UserProfileResponse> profiles = profileClient.getBulkUserProfiles(BulkUserProfileRequest.builder()
                .userIds(List.of(request.getUserIdSender()))
                .build()).getResult();

        Map<String, UserProfileResponse> profilesMap = profiles.stream()
                .collect(Collectors.toMap(
                        UserProfileResponse::getUserId,
                        Function.identity()
                ));

        List<NotificationResponse> responses = toNotificationResponses(List.of(notification), profilesMap, null);
        NotificationSocketData notificationSocketData = new NotificationSocketData(responses.getFirst(), request.getToUserIds());
        kafkaTemplate.send("notifications", notificationSocketData);
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

        Map<String, UserProfileResponse> profilesMap =
                profileClient.getBulkUserProfiles(BulkUserProfileRequest.builder()
                        .userIds(senderIds)
                        .build())
                .getResult()
                .stream()
                .collect(java.util.stream.Collectors.toMap(UserProfileResponse::getUserId, Function.identity()));

        List<NotificationResponse> notifications =
                toNotificationResponses(notificationsPage.getContent(), profilesMap, userId);

        return PageResponse.<NotificationResponse>builder()
                .currentPage(page)
                .pageSize(size)
                .totalPages(notificationsPage.getTotalPages())
                .totalElement(notificationsPage.getTotalElements())
                .data(notifications)
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
}
