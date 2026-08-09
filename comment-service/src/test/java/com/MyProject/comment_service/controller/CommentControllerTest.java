package com.MyProject.comment_service.controller;

import com.MyProject.comment_service.configuration.SecurityConfig;
import com.MyProject.comment_service.dto.request.CreateCommentRequest;
import com.MyProject.comment_service.dto.request.ReactionRequest;
import com.MyProject.comment_service.dto.response.CommentReactionResponse;
import com.MyProject.comment_service.dto.response.CommentResponse;
import com.MyProject.comment_service.enums.CommentReactionType;
import com.MyProject.comment_service.enums.CommentType;
import com.MyProject.comment_service.enums.ErrorCode;
import com.MyProject.comment_service.exception.AppException;
import com.MyProject.comment_service.repository.CommentRepository;
import com.MyProject.comment_service.repository.OutboxRepository;
import com.MyProject.comment_service.service.CommentApiRateLimitService;
import com.MyProject.comment_service.service.CommentReactionService;
import com.MyProject.comment_service.service.CommentService;
import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.common.security.CommonJwtAuthenticationEntryPoint;
import com.MyProject.common.security.CommonJwtDecoder;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CommentController.class)
@Import({SecurityConfig.class, CommonJwtAuthenticationEntryPoint.class, CommonJwtDecoder.class,
        CommentControllerTest.TestBeans.class})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CommentControllerTest {

    static class TestBeans {
        @Bean
        JwtAuthenticationConverter jwtAuthenticationConverter() {
            return new JwtAuthenticationConverter();
        }
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean CommentService commentService;
    @MockitoBean CommentReactionService commentReactionService;
    @MockitoBean CommentApiRateLimitService commentApiRateLimitService;
    @MockitoBean CommentRepository commentRepository;
    @MockitoBean OutboxRepository outboxRepository;

    final ObjectMapper objectMapper = new ObjectMapper();

    private RequestPostProcessor asUser(String userId) {
        return jwt().jwt(builder -> builder.claim("userId", userId).claim("scope", "USER"));
    }

    @Test
    void createComment_authenticated_returnsCreatedComment() throws Exception {
        when(commentService.createComment(any())).thenReturn(
                CommentResponse.builder().id("c-1").content("hello").build());
        CreateCommentRequest request = CreateCommentRequest.builder()
                .sourceId("post-1").content("hello").type(CommentType.TEXT).build();

        mockMvc.perform(MockMvcRequestBuilders.post("/")
                        .with(asUser("user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("result.id").value("c-1"));
    }

    @Test
    void createComment_unauthenticated_returns401() throws Exception {
        CreateCommentRequest request = CreateCommentRequest.builder()
                .sourceId("post-1").content("hello").type(CommentType.TEXT).build();

        mockMvc.perform(MockMvcRequestBuilders.post("/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(commentService);
    }

    @Test
    void createComment_serviceRejectsEmptyContent_returns400() throws Exception {
        when(commentService.createComment(any())).thenThrow(new AppException(ErrorCode.COMMENT_CONTENT_EMPTY));
        CreateCommentRequest request = CreateCommentRequest.builder()
                .sourceId("post-1").content("   ").type(CommentType.TEXT).build();

        mockMvc.perform(MockMvcRequestBuilders.post("/")
                        .with(asUser("user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value(ErrorCode.COMMENT_CONTENT_EMPTY.getCode()));
    }

    @Test
    void getComments_noAuthRequired_returnsPage() throws Exception {
        when(commentService.getComments("post-1", 1, 10)).thenReturn(
                PageResponse.<CommentResponse>builder().currentPage(1).pageSize(10).totalPages(1).totalElement(1L)
                        .data(List.of(CommentResponse.builder().id("c-1").build())).build());

        mockMvc.perform(MockMvcRequestBuilders.get("/").param("sourceId", "post-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("result.data[0].id").value("c-1"));
    }

    @Test
    void updateComment_notOwner_returns403() throws Exception {
        when(commentService.updateComment(eq("c-1"), any())).thenThrow(new AppException(ErrorCode.UNAUTHORIZED));

        mockMvc.perform(MockMvcRequestBuilders.put("/c-1")
                        .with(asUser("user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"edited\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("code").value(ErrorCode.UNAUTHORIZED.getCode()));
    }

    @Test
    void deleteComment_authenticated_callsServiceAndReturnsOk() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.delete("/c-1").with(asUser("user-1")))
                .andExpect(status().isOk());

        verify(commentService).deleteComment("c-1");
    }

    @Test
    void deleteComment_unauthenticated_returns401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.delete("/c-1"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(commentService);
    }

    @Test
    void react_authenticated_returnsUpdatedCounts() throws Exception {
        when(commentReactionService.react("c-1", "user-1", CommentReactionType.LIKE)).thenReturn(
                CommentReactionResponse.builder().commentId("c-1").likeCount(1).loveCount(0).myReaction("LIKE").build());
        ReactionRequest request = ReactionRequest.builder().type(CommentReactionType.LIKE).build();

        mockMvc.perform(MockMvcRequestBuilders.post("/c-1/reactions")
                        .with(asUser("user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("result.likeCount").value(1))
                .andExpect(jsonPath("result.myReaction").value("LIKE"));
    }

    @Test
    void countComments_missingSourceId_returns400() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/count"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(commentService);
    }
}
