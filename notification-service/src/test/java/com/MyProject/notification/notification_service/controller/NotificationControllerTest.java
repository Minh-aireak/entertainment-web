package com.MyProject.notification.notification_service.controller;

import com.MyProject.common_dto.event.dto.response.NotificationResponse;
import com.MyProject.common_dto.event.dto.response.PageResponse;
import com.MyProject.notification.notification_service.configuration.CustomJwtDecoder;
import com.MyProject.notification.notification_service.configuration.JwtAuthenticationEntryPoint;
import com.MyProject.notification.notification_service.configuration.SecurityConfig;
import com.MyProject.notification.notification_service.service.NotificationService;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationEntryPoint.class,
        CustomJwtDecoder.class
})
@FieldDefaults(level = AccessLevel.PRIVATE)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationControllerTest {
    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    NotificationService notificationService;

    PageResponse<NotificationResponse> pageResponse;

    @BeforeEach
    void initData() {
        NotificationResponse notificationResponse = NotificationResponse.builder()
                .id("noti_123")
                .type("FRIEND_REQUEST")
                .userIdSender("123456789")
                .displayNameSender("aireak")
                .avatarSender(null)
                .isRead(false)
                .message("Aireak sent friend request")
                .createdAt(LocalDateTime.now())
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
    @WithMockUser
    void getMyNotifications_success() throws Exception {
        when(notificationService.getMyNotifications(anyInt(), anyInt())).thenReturn(pageResponse);

        mockMvc.perform(MockMvcRequestBuilders.get("/my-notifications")
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
                .andExpect(jsonPath("code").value(8201))
                .andExpect(jsonPath("message").value("Unauthenticated!"));

        verify(notificationService, never()).getMyNotifications(anyInt(), anyInt());
    }

    @Test
    @WithMockUser
    void getUnreadCount_success() throws Exception {
        when(notificationService.getUnreadCount()).thenReturn(1L);
        mockMvc.perform(MockMvcRequestBuilders.get("/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("code").value(1000))
                .andExpect(jsonPath("result").value("1"));

        verify(notificationService, times(1)).getUnreadCount();
    }

    @Test
    void getUnreadCount_unAuthenticated() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/unread-count"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("code").value(8201))
                .andExpect(jsonPath("message").value("Unauthenticated!"));

        verify(notificationService, never()).getUnreadCount();
    }

    @Test
    @WithMockUser
    void markAllAsRead_success() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.put("/mark-all-as-read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("code").value(1000));

        verify(notificationService, times(1)).markAllAsRead();
    }

    @Test
    void markAllAsRead_unAuthenticated() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.put("/mark-all-as-read"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("code").value(8201))
                .andExpect(jsonPath("message").value("Unauthenticated!"));

        verify(notificationService, never()).markAllAsRead();
    }
}
