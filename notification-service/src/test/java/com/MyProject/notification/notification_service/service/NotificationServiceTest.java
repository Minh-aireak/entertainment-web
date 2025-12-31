package com.MyProject.notification.notification_service.service;

import com.MyProject.common_dto.event.dto.NotificationSocketData;
import com.MyProject.common_dto.event.dto.request.BulkUserProfileRequest;
import com.MyProject.common_dto.event.dto.request.NotificationRequest;
import com.MyProject.common_dto.event.dto.response.NotificationResponse;
import com.MyProject.common_dto.event.dto.response.UserProfileResponse;
import com.MyProject.common_dto.event.entity.TypeNotification;
import com.MyProject.notification.notification_service.dto.response.ApiResponse;
import com.MyProject.notification.notification_service.entity.Notification;
import com.MyProject.notification.notification_service.exception.AppException;
import com.MyProject.notification.notification_service.exception.ErrorCode;
import com.MyProject.notification.notification_service.mapper.NotificationMapper;
import com.MyProject.notification.notification_service.repository.NotificationRepository;
import com.MyProject.notification.notification_service.repository.httpclient.ProfileClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    NotificationRepository notificationRepository;

    @Mock
    NotificationMapper notificationMapper;

    @Mock
    ProfileClient profileClient;

    @Mock
    KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    NotificationService notificationService;

    String currentUserId = "user-123";

    private void mockUserId() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("userId")).thenReturn(currentUserId);

        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    void createNotification_success() {
        NotificationRequest request = NotificationRequest.builder()
                .typeNotification(TypeNotification.FRIEND_REQUEST)
                .userIdSender("sender-123")
                .toUserIds(List.of("recipient-123"))
                .metadata(Map.of("message", "Aireak sent a friend request"))
                .build();

        UserProfileResponse senderProfile = UserProfileResponse.builder()
                .userId("sender-123")
                .displayName("Aireak")
                .build();

        when(profileClient.getBulkUserProfiles(any())).thenReturn(
                ApiResponse.<List<UserProfileResponse>>builder().result(List.of(senderProfile)).build()
        );

        NotificationResponse mockResponse = NotificationResponse.builder()
                .userIdSender("sender-123")
                .message("Aireak sent a friend request")
                .build();
        when(notificationMapper.toNotificationResponse(any())).thenReturn(mockResponse);

        notificationService.createNotification(request);

        ArgumentCaptor<Notification> notiCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notiCaptor.capture());
        Notification savedNoti = notiCaptor.getValue();

        assertEquals("sender-123", savedNoti.getUserIdSender());
        assertEquals("Aireak sent a friend request", savedNoti.getMessage());
        assertEquals(TypeNotification.FRIEND_REQUEST, savedNoti.getType());
        assertTrue(savedNoti.getRecipientReadMap().containsKey("recipient-123"));
        assertNull(savedNoti.getRecipientReadMap().get("recipient-123"));

        ArgumentCaptor<NotificationSocketData> socketCaptor = ArgumentCaptor.forClass(NotificationSocketData.class);
        verify(kafkaTemplate).send(eq("notifications"), socketCaptor.capture());
        NotificationSocketData capturedSocket = socketCaptor.getValue();

        assertEquals("sender-123", capturedSocket.notificationResponse().getUserIdSender());
        assertEquals("Aireak sent a friend request", capturedSocket.notificationResponse().getMessage());
        assertEquals(1, capturedSocket.userIds().size());
    }

    @Test
    void getMyNotifications_success() {
        int page = 1, size = 10;
        mockUserId();
        Notification notification = Notification.builder()
                .userIdSender("sender-123")
                .recipientReadMap(Map.of(currentUserId, LocalDateTime.now()))
                .type(TypeNotification.FRIEND_REQUEST)
                .build();

        Page<Notification> mockPage = new PageImpl<>(List.of(notification));
        when(notificationRepository.findByToUserIdsInOrderByCreatedAtDesc(eq(currentUserId), any(Pageable.class)))
                .thenReturn(mockPage);

        UserProfileResponse senderProfile = UserProfileResponse.builder()
                .userId("sender-123").displayName("Aireak").build();
        when(profileClient.getBulkUserProfiles(any())).thenReturn(
                ApiResponse.<List<UserProfileResponse>>builder().result(List.of(senderProfile)).build()
        );

        when(notificationMapper.toNotificationResponse(any())).thenReturn(new NotificationResponse());

        var response = notificationService.getMyNotifications(page, size);

        assertNotNull(response);
        assertEquals(1, response.getData().size());
        verify(notificationRepository).findByToUserIdsInOrderByCreatedAtDesc(eq(currentUserId), any());
    }

    @Test
    void getMyNotifications_authenticationNull_unAuthorized() {
        int page = 1, size = 10;

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(null);
        SecurityContextHolder.setContext(securityContext);

        var exception = assertThrows(AppException.class, () -> notificationService.getMyNotifications(page, size));

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());

        verify(notificationRepository, never()).findByToUserIdsInOrderByCreatedAtDesc(any(), any());
        verify(profileClient, never()).getBulkUserProfiles(any(BulkUserProfileRequest.class));
    }

    @Test
    void getMyNotifications_principalNull_unAuthorized() {
        int page = 1, size = 10;
        JwtAuthenticationToken authentication = mock(JwtAuthenticationToken.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(null);
        SecurityContextHolder.setContext(securityContext);

        var exception = assertThrows(AppException.class, () -> notificationService.getMyNotifications(page, size));

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());

        verify(notificationRepository, never()).findByToUserIdsInOrderByCreatedAtDesc(any(), any());
        verify(profileClient, never()).getBulkUserProfiles(any(BulkUserProfileRequest.class));
    }

    @Test
    void getUnreadCount_success() {
        mockUserId();
        when(notificationRepository.countUnreadByUserId(currentUserId)).thenReturn(5L);

        Long count = notificationService.getUnreadCount();

        assertEquals(Optional.of(count), Optional.of(5L));
        verify(notificationRepository, times(1)).countUnreadByUserId(currentUserId);
    }

    @Test
    void getUnreadCount_authenticationNull_unAuthorized() {
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(null);
        SecurityContextHolder.setContext(securityContext);

        var exception = assertThrows(AppException.class, () -> notificationService.markAllAsRead());

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());

        verify(notificationRepository, never()).countUnreadByUserId(any());
    }

    @Test
    void getUnreadCount_principalNull_unAuthorized() {
        JwtAuthenticationToken authentication = mock(JwtAuthenticationToken.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(null);
        SecurityContextHolder.setContext(securityContext);

        var exception = assertThrows(AppException.class, () -> notificationService.markAllAsRead());

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());

        verify(notificationRepository, never()).countUnreadByUserId(any());
    }

    @Test
    void markAllAsRead_success() {
        mockUserId();

        LocalDateTime beforeCall = LocalDateTime.now().minusSeconds(1);

        notificationService.markAllAsRead();

        ArgumentCaptor<String> userIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<LocalDateTime> timeCaptor = ArgumentCaptor.forClass(LocalDateTime.class);

        verify(notificationRepository, times(1))
                .markAllAsRead(userIdCaptor.capture(), timeCaptor.capture());

        assertEquals("user-123", userIdCaptor.getValue());

        LocalDateTime capturedTime = timeCaptor.getValue();
        assertTrue(capturedTime.isAfter(beforeCall) || capturedTime.isEqual(beforeCall));
        assertTrue(capturedTime.isBefore(LocalDateTime.now().plusSeconds(1)));
    }

    @Test
    void markAllAsRead_authenticationNull_unAuthorized() {
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(null);
        SecurityContextHolder.setContext(securityContext);

        var exception = assertThrows(AppException.class, () -> notificationService.markAllAsRead());

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());

        verify(notificationRepository, never()).countUnreadByUserId(any());
    }

    @Test
    void markAllAsRead_principalNull_unAuthorized() {
        JwtAuthenticationToken authentication = mock(JwtAuthenticationToken.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(null);
        SecurityContextHolder.setContext(securityContext);

        var exception = assertThrows(AppException.class, () -> notificationService.markAllAsRead());

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());

        verify(notificationRepository, never()).countUnreadByUserId(any());
    }
}