package com.MyProject.notification.notification_service.controller;

import com.MyProject.notification.notification_service.dto.response.NotificationResponse;
import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.common.security.CommonJwtDecoder;
import com.MyProject.common.security.CommonJwtAuthenticationEntryPoint;
import com.MyProject.notification.notification_service.configuration.SecurityConfig;
import com.MyProject.notification.notification_service.service.NotificationApiRateLimitService;
import com.MyProject.notification.notification_service.service.NotificationService;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import({
        SecurityConfig.class,
        CommonJwtAuthenticationEntryPoint.class,
        CommonJwtDecoder.class,
        NotificationControllerTest.TestBeans.class
})
@FieldDefaults(level = AccessLevel.PRIVATE)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationControllerTest {

    static class TestBeans {
        @Bean
        JwtAuthenticationConverter jwtAuthenticationConverter() {
            return new JwtAuthenticationConverter();
        }
    }

    private RequestPostProcessor asUser(String userId) {
        return jwt().jwt(builder -> builder.claim("userId", userId));
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    NotificationService notificationService;

    @MockitoBean
    NotificationApiRateLimitService notificationApiRateLimitService;

    PageResponse<NotificationResponse> pageResponse;

    @BeforeEach
    void initData() {
        NotificationResponse notificationResponse = NotificationResponse.builder()
                .id("noti_123")
                .type("FRIEND_REQUEST")
                .userIdSender("123456789")
                .displayNameSender("aireak")
                .avatarSender(null)
                .read(false)
                .message("Aireak sent friend request")
                .createdAt(LocalDateTime.now().toString())
                .build();

        pageResponse = PageResponse.<NotificationResponse>builder()
                .currentPage(1)
                .pageSize(10)
                .totalPages(1)
                .totalElement(1L)
                .data(List.of(notificationResponse))
                .build();
    }

    @Test
    void getMyNotifications_success() throws Exception {
        when(notificationService.getMyNotifications(anyInt(), anyInt())).thenReturn(pageResponse);

        mockMvc.perform(MockMvcRequestBuilders.get("/my-notifications").with(asUser("user-1"))
                        .param("page", "1")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("code").value(1000))
                .andExpect(jsonPath("result.currentPage").value(1))
                .andExpect(jsonPath("result.pageSize").value(10))
                .andExpect(jsonPath("result.totalPages").value(1))
                .andExpect(jsonPath("result.totalElement").value(1L))
                .andExpect(jsonPath("result.data[0].id").value("noti_123"))
                .andExpect(jsonPath("result.data[0].message").value("Aireak sent friend request"));

        verify(notificationService, times(1)).getMyNotifications(1, 10);
    }

    @Test
    void getMyNotifications_unAuthenticated() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/my-notifications")
                        .param("page", "1")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("code").value(1401))
                .andExpect(jsonPath("message").value("Unauthenticated!"));

        verify(notificationService, never()).getMyNotifications(anyInt(), anyInt());
    }

    @Test
    void getUnreadCount_success() throws Exception {
        when(notificationService.getUnreadCount()).thenReturn(1L);
        mockMvc.perform(MockMvcRequestBuilders.get("/unread-count").with(asUser("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("code").value(1000))
                .andExpect(jsonPath("result").value("1"));

        verify(notificationService, times(1)).getUnreadCount();
    }

    @Test
    void getUnreadCount_unAuthenticated() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/unread-count"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("code").value(1401))
                .andExpect(jsonPath("message").value("Unauthenticated!"));

        verify(notificationService, never()).getUnreadCount();
    }

    @Test
    void markAllAsRead_success() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.put("/mark-all-as-read").with(asUser("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("code").value(1000));

        verify(notificationService, times(1)).markAllAsRead();
    }

    @Test
    void markAllAsRead_unAuthenticated() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.put("/mark-all-as-read"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("code").value(1401))
                .andExpect(jsonPath("message").value("Unauthenticated!"));

        verify(notificationService, never()).markAllAsRead();
    }
}
