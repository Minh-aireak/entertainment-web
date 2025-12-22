package com.MyProject.notification.notification_service.service;

import com.MyProject.common_dto.event.dto.FriendRequestEvent;
import com.MyProject.common_dto.event.dto.NotificationData;
import com.MyProject.common_dto.event.dto.NotificationResponse;
import com.MyProject.common_dto.event.dto.UserProfileResponse;
import com.MyProject.notification.notification_service.dto.response.PageResponse;
import com.MyProject.notification.notification_service.entity.Notification;
import com.MyProject.notification.notification_service.entity.TypeNotification;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        Jwt jwt = ((JwtAuthenticationToken) authentication).getToken();
        return jwt.getClaim("userId");
    }

    private NotificationResponse toNotificationResponse(Notification notification,
                                                        Map<String, UserProfileResponse> profileMap,
                                                        String currentUserId) {
        NotificationResponse response = notificationMapper.toNotificationResponse(notification);
        UserProfileResponse sender = profileMap.get(notification.getFromUserId());
        response.setType(notification.getType().name());
        response.setUserIdSender(notification.getFromUserId());
        response.setDisplayNameSender(sender != null ? sender.getDisplayName() : null);
        response.setAvatarSender(sender != null ? sender.getAvatar() : null);
        Boolean isRead = notification.getRecipientReadMap() != null
                && notification.getRecipientReadMap().getOrDefault(currentUserId, null) != null;
        response.setIsRead(isRead);

        return response;
    }

    @Transactional
    public NotificationResponse createNotification(TypeNotification typeNotification,
                                                   String fromUserId,
                                                   List<String> toUserIds,
                                                   String message) {
        Map<String, LocalDateTime> recipientReadMap = new HashMap<>();
        toUserIds.forEach(userId -> recipientReadMap.put(userId, null));

        Notification notification = Notification.builder()
                .type(typeNotification)
                .fromUserId(fromUserId)
                .recipientReadMap(recipientReadMap)
                .message(message)
                .createdAt(LocalDateTime.now())
                .build();

        Notification savedNotification = notificationRepository.save(notification);

        Map<String, UserProfileResponse> profileMap = java.util.Collections.singletonMap(
                fromUserId,
                profileClient.getProfile(fromUserId).getResult()
        );

        NotificationResponse response = toNotificationResponse(savedNotification, profileMap, null);
        NotificationData notificationData = new NotificationData(response, toUserIds);
        kafkaTemplate.send("notifications", notificationData);

        return response;
    }

    public PageResponse<NotificationResponse> getMyNotifications(int page, int size) {
        String userId = getUserId();
        Pageable pageable = PageRequest.of(page - 1, size);

        Page<Notification> notificationsPage = notificationRepository
                .findByToUserIdsInOrderByCreatedAtDesc(userId, pageable);

        List<String> senderIds = notificationsPage.getContent().stream()
                .map(Notification::getFromUserId)
                .distinct()
                .toList();

        Map<String, UserProfileResponse> profileMap = profileClient
                .getBulkUserProfiles(com.MyProject.notification.notification_service.dto.request.BulkUserProfileRequest
                        .builder()
                        .userIds(senderIds)
                        .build())
                .getResult()
                .stream()
                .collect(java.util.stream.Collectors.toMap(UserProfileResponse::getUserId, p -> p));

        List<NotificationResponse> notifications = notificationsPage.getContent().stream()
                .map(n -> toNotificationResponse(n, profileMap, userId))
                .toList();

        return PageResponse.<NotificationResponse>builder()
                .currentPage(page)
                .pageSize(size)
                .totalPages(notificationsPage.getTotalPages())
                .totalElement(notificationsPage.getTotalElements())
                .data(notifications)
                .build();
    }

//    @Transactional
//    public void seenAt(String conversationId) {
//        var userId = getUserId();
//        var check = conversationRepository.findById(conversationId)
//                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND))
//                .getParticipantInfos().stream().anyMatch(participantInfo ->
//                        participantInfo.getUserId().equals(userId)
//                );
//
//        if (!check)
//            throw new AppException(ErrorCode.USERID_NOT_FOUND);
//
//        List<ChatMessage> msg = chatMessageRepository
//                .findAllByConversationIdAndSeenAtMapNotContainsKey(conversationId, userId);
//        for (ChatMessage cm : msg) {
//            cm.getSeenAtMap().put(userId, Instant.now());
//        }
//
//        chatMessageRepository.saveAll(msg);
//    }

    @Transactional
    public NotificationResponse createFriendRequest(FriendRequestEvent event) {
        UserProfileResponse profile = profileClient.getProfile(event.getFromUserId()).getResult();
        String message = profile.getDisplayName() + " send you a friend request";

        return createNotification(
                TypeNotification.FRIEND_REQUEST,
                event.getFromUserId(),
                List.of(event.getToUserId()),
                message
        );
    }

//    @Transactional
//    public NotificationResponse createPostStatus(FriendRequestEvent event) {
//        UserProfileResponse profile = profileClient.getProfile(event.getFromUserId()).getResult();
//        String message = profile.getDisplayName() + " send you a friend request";
//
//        return createNotification(TypeNotification.POST_STATUS, event.getFromUserId(), List.of(event.getToUserId()), message);
//    }

//    public Long getUnreadCount() {
//        String userId = getUserId();
//        return notificationRepository.countUnreadByUserId(userId);
//    }
//
//    public void markAsRead(String notificationId) {
//        String userId = getUserId();
//        Notification notification = notificationRepository.findById(notificationId)
//                .orElseThrow(() -> new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION));
//
//        if (notification.getRecipientReadMap() != null && notification.getRecipientReadMap().containsKey(userId)) {
//            notification.getRecipientReadMap().put(userId, Instant.now());
//            notificationRepository.save(notification);
//        }
//    }
//
//    public void markAllAsRead() {
//        String userId = getUserId();
//        List<Notification> unreadNotifications = notificationRepository.findUnreadByUserId(userId);
//
//        Instant now = Instant.now();
//        unreadNotifications.forEach(notification -> {
//            if (notification.getRecipientReadMap() != null) {
//                notification.getRecipientReadMap().put(userId, now);
//            }
//        });
//        notificationRepository.saveAll(unreadNotifications);
//    }
}
