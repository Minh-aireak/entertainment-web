package com.MyProject.notification.notification_service.controller;

import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.post.post_service.configuration.CustomJwtDecoder;
import com.MyProject.post.post_service.configuration.JwtAuthenticationEntryPoint;
import com.MyProject.post.post_service.configuration.SecurityConfig;
import com.MyProject.post.post_service.dto.request.ScheduleRequest;
import com.MyProject.post.post_service.dto.response.ScheduleResponse;
import com.MyProject.post.post_service.exception.AppException;
import com.MyProject.post.post_service.exception.ErrorCode;
import com.MyProject.post.post_service.service.PostService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PostControllerTest.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationEntryPoint.class,
        CustomJwtDecoder.class
})
@FieldDefaults(level = AccessLevel.PRIVATE)
@AutoConfigureMockMvc
class PostControllerTest {
    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PostService postService;

    ObjectMapper objectMapper;
    ScheduleResponse scheduleResponse;
    ScheduleRequest scheduleRequest;
    PageResponse<ScheduleResponse> pageResponse;

    @BeforeEach
    void initData() {
        scheduleResponse = ScheduleResponse.builder()
                .id("schedule_123")
                .postType("BUSINESS_SCHEDULE")
                .userId("123456789")
                .displayName("aireak")
                .avatar(null)
                .title("Test controller")
                .content("Aireak test controller")
                .startTime(LocalDateTime.parse("2026-01-08T13:00"))
                .endTime(LocalDateTime.parse("2026-01-08T15:00"))
                .createdDate("1 hour ago")
                .modifiedDate(null)
                .startPosition(null)
                .endPosition(null)
                .build();
//
//        pageResponse = PageResponse.<NotificationResponse>builder()
//                .currentPage(1)
//                .pageSize(10)
//                .totalPages(1)
//                .totalElement(1L)
//                .data(List.of(notificationResponse))
//                .build();
    }

    @Test
    @WithMockUser
    void createPost_success() throws Exception {
        scheduleRequest = ScheduleRequest.builder()
                .postType("BUSINESS_SCHEDULE")
                .title("Test controller")
                .content("Aireak test controller")
                .startTime(LocalDateTime.parse("2026-01-08T13:00"))
                .endTime(LocalDateTime.parse("2026-01-08T15:00"))
                .listUsersJoin(List.of("user_123"))
                .build();
        String content = objectMapper.writeValueAsString(scheduleRequest);

        when(postService.createPost(scheduleRequest)).thenReturn(scheduleResponse);

        mockMvc.perform(MockMvcRequestBuilders.post("/my-posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isOk())
                .andExpect(jsonPath("code").value(1000))
                .andExpect(jsonPath("result.postType").value("BUSINESS_SCHEDULE"))
                .andExpect(jsonPath("result.title").value("Test controller"))
                .andExpect(jsonPath("result.content").value("Aireak test controller"))
                .andExpect(jsonPath("result.startTime").value("2026-01-08T13:00"))
                .andExpect(jsonPath("result.endTime").value("2026-01-08T15:00"))
                .andExpect(jsonPath("result.listUsersJoin[0]").value("user_123"))
                .andExpect(jsonPath("message").value("Created success!"));

        verify(postService, times(1)).createPost(any());
    }

    @Test
    void createPost_unAuthenticated() throws Exception {
        scheduleRequest = ScheduleRequest.builder()
                .postType("BUSINESS_SCHEDULE")
                .title("Test controller")
                .content("Aireak test controller")
                .startTime(LocalDateTime.parse("2026-01-08T13:00"))
                .endTime(LocalDateTime.parse("2026-01-08T15:00"))
                .build();
        String content = objectMapper.writeValueAsString(scheduleRequest);

        mockMvc.perform(MockMvcRequestBuilders.post("/my-posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("code").value(8301))
                .andExpect(jsonPath("message").value("Unauthenticated!"));

        verify(postService, never()).createPost(any());
    }

    @Test
    @WithMockUser
    void createPost_schedulerException() throws Exception {
        scheduleRequest = ScheduleRequest.builder()
                .postType("BUSINESS_SCHEDULE")
                .title("Test controller")
                .content("Aireak test controller")
                .startTime(LocalDateTime.parse("2026-01-08T13:00"))
                .endTime(LocalDateTime.parse("2026-01-08T15:00"))
                .build();
        String content = objectMapper.writeValueAsString(scheduleRequest);

        doThrow(new AppException(ErrorCode.SCHEDULER_EXCEPTION)).when(postService).createPost(scheduleRequest);

        mockMvc.perform(MockMvcRequestBuilders.post("/my-posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("code").value(8310))
                .andExpect(jsonPath("message").value("Scheduler exception!"));

        verify(postService, times(1)).createPost(any());
    }

//    @Test
//    @WithMockUser
//    void getUnreadCount_success() throws Exception {
//        when(notificationService.getUnreadCount()).thenReturn(1L);
//        mockMvc.perform(MockMvcRequestBuilders.get("/unread-count"))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("code").value(1000))
//                .andExpect(jsonPath("result").value("1"));
//
//        verify(notificationService, times(1)).getUnreadCount();
//    }
//
//    @Test
//    void getUnreadCount_unAuthenticated() throws Exception {
//        mockMvc.perform(MockMvcRequestBuilders.get("/unread-count"))
//                .andExpect(status().isUnauthorized())
//                .andExpect(jsonPath("code").value(8201))
//                .andExpect(jsonPath("message").value("Unauthenticated!"));
//
//        verify(notificationService, never()).getUnreadCount();
//    }
//
//    @Test
//    @WithMockUser
//    void markAllAsRead_success() throws Exception {
//        mockMvc.perform(MockMvcRequestBuilders.put("/mark-all-as-read"))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("code").value(1000));
//
//        verify(notificationService, times(1)).markAllAsRead();
//    }
//
//    @Test
//    void markAllAsRead_unAuthenticated() throws Exception {
//        mockMvc.perform(MockMvcRequestBuilders.put("/mark-all-as-read"))
//                .andExpect(status().isUnauthorized())
//                .andExpect(jsonPath("code").value(8201))
//                .andExpect(jsonPath("message").value("Unauthenticated!"));
//
//        verify(notificationService, never()).markAllAsRead();
//    }
}
