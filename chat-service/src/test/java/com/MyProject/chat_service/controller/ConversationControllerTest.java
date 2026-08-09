package com.MyProject.chat_service.controller;

import com.MyProject.chat_service.configuration.SecurityConfig;
import com.MyProject.chat_service.dto.request.ConversationUpdateRequest;
import com.MyProject.chat_service.dto.response.ConversationResponse;
import com.MyProject.chat_service.enums.ErrorCode;
import com.MyProject.chat_service.exception.AppException;
import com.MyProject.chat_service.service.ChatApiRateLimitService;
import com.MyProject.chat_service.service.ConversationService;
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

@WebMvcTest(ConversationController.class)
@Import({SecurityConfig.class, CommonJwtAuthenticationEntryPoint.class, CommonJwtDecoder.class,
        ConversationControllerTest.TestBeans.class})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConversationControllerTest {

    static class TestBeans {
        @Bean
        JwtAuthenticationConverter jwtAuthenticationConverter() {
            return new JwtAuthenticationConverter();
        }
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ConversationService conversationService;

    @MockitoBean
    ChatApiRateLimitService chatApiRateLimitService;

    // See ChatMessageControllerTest for why these repository mocks are needed even though this
    // slice never calls them directly.
    @MockitoBean com.MyProject.chat_service.repository.mongo.ChatMessageRepository chatMessageRepository;
    @MockitoBean com.MyProject.chat_service.repository.mongo.ConversationRepository conversationRepository;
    @MockitoBean com.MyProject.chat_service.repository.mongo.ConversationDirectRepository conversationDirectRepository;
    @MockitoBean com.MyProject.chat_service.repository.mongo.ConversationGroupRepository conversationGroupRepository;
    @MockitoBean com.MyProject.chat_service.repository.mongo.ConversationMemberRepository conversationMemberRepository;
    @MockitoBean com.MyProject.chat_service.repository.mongo.OutboxRepository outboxRepository;
    @MockitoBean com.MyProject.chat_service.repository.elasticsearch.ChatMessageElasticRepository chatMessageElasticRepository;
    @MockitoBean com.MyProject.chat_service.repository.elasticsearch.ConversationElasticRepository conversationElasticRepository;

    final ObjectMapper objectMapper = new ObjectMapper();

    private RequestPostProcessor asUser(String userId) {
        return jwt().jwt(builder -> builder.claim("userId", userId).claim("scope", "USER"));
    }

    @Test
    void createConversation_authenticated_returnsConversation() throws Exception {
        when(conversationService.createConversationForApi(any())).thenReturn(
                ConversationResponse.builder().id("conv-1").type("DIRECT").build());

        mockMvc.perform(MockMvcRequestBuilders.post("/conversations")
                        .with(asUser("user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of("user-1", "user-2"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("result.id").value("conv-1"));

        verify(chatApiRateLimitService).checkConversationWrite("user-1");
    }

    @Test
    void createConversation_unauthenticated_returns401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of("user-1", "user-2"))))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(conversationService);
    }

    @Test
    void createConversation_serviceThrowsInvalidParticipants_returns400() throws Exception {
        when(conversationService.createConversationForApi(any()))
                .thenThrow(new AppException(ErrorCode.INVALID_CONVERSATION_PARTICIPANTS));

        mockMvc.perform(MockMvcRequestBuilders.post("/conversations")
                        .with(asUser("user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of("user-1"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value(ErrorCode.INVALID_CONVERSATION_PARTICIPANTS.getCode()));
    }

    @Test
    void updateConversation_authenticated_returnsUpdatedConversation() throws Exception {
        when(conversationService.updateGroupConversation(eq("conv-1"), any())).thenReturn(
                ConversationResponse.builder().id("conv-1").conversationName("New name").build());
        ConversationUpdateRequest request = ConversationUpdateRequest.builder().conversationName("New name").build();

        mockMvc.perform(MockMvcRequestBuilders.put("/conversations/conv-1")
                        .with(asUser("user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("result.conversationName").value("New name"));
    }

    @Test
    void updateConversation_conversationNotFound_returns404() throws Exception {
        when(conversationService.updateGroupConversation(eq("missing"), any()))
                .thenThrow(new AppException(ErrorCode.CONVERSATION_NOT_FOUND));
        ConversationUpdateRequest request = ConversationUpdateRequest.builder().conversationName("New name").build();

        mockMvc.perform(MockMvcRequestBuilders.put("/conversations/missing")
                        .with(asUser("user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getMyConversations_authenticated_returnsPage() throws Exception {
        when(conversationService.getMyConversations(1, 10)).thenReturn(
                PageResponse.<ConversationResponse>builder()
                        .currentPage(1).pageSize(10).totalPages(1).totalElement(1L)
                        .data(List.of(ConversationResponse.builder().id("conv-1").build()))
                        .build());

        mockMvc.perform(MockMvcRequestBuilders.get("/conversations/my-conversations")
                        .with(asUser("user-1"))
                        .param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("result.data[0].id").value("conv-1"));
    }

    @Test
    void searchConversations_missingQueryParam_returns400() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/conversations/search").with(asUser("user-1")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(conversationService);
    }
}
