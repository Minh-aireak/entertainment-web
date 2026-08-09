package com.MyProject.chat_service.controller;

import com.MyProject.chat_service.configuration.SecurityConfig;
import com.MyProject.chat_service.dto.request.ChatMessageCreateRequest;
import com.MyProject.chat_service.dto.request.ChatMessageDeleteRequest;
import com.MyProject.chat_service.dto.request.ChatMessageUpdateRequest;
import com.MyProject.chat_service.dto.response.ChatMessageResponse;
import com.MyProject.chat_service.dto.response.UnreadCountResponse;
import com.MyProject.chat_service.enums.ErrorCode;
import com.MyProject.chat_service.enums.MessageType;
import com.MyProject.chat_service.exception.AppException;
import com.MyProject.chat_service.service.ChatApiRateLimitService;
import com.MyProject.chat_service.service.ChatMessageService;
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

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatMessageController.class)
@Import({SecurityConfig.class, CommonJwtAuthenticationEntryPoint.class, CommonJwtDecoder.class,
        ChatMessageControllerTest.TestBeans.class})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatMessageControllerTest {

    static class TestBeans {
        @Bean
        JwtAuthenticationConverter jwtAuthenticationConverter() {
            return new JwtAuthenticationConverter();
        }
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ChatMessageService chatMessageService;

    @MockitoBean
    ChatApiRateLimitService chatApiRateLimitService;

    // @EnableMongoRepositories / @EnableElasticsearchRepositories sit directly on
    // ChatServiceApplication, so @WebMvcTest still registers these repository bean definitions even
    // though nothing in this slice uses them (the real services above are fully mocked out) -
    // @MockitoBean replaces them before Spring tries to build a real Mongo/ES repository proxy.
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
    void createChatMessage_authenticated_returnsCreatedMessage() throws Exception {
        ChatMessageResponse response = ChatMessageResponse.builder().id("msg-1").content("hello").build();
        when(chatMessageService.createChatMessage(any())).thenReturn(response);
        ChatMessageCreateRequest request = ChatMessageCreateRequest.builder()
                .conversationId("conv-1").messageType(MessageType.TEXT).content("hello").build();

        mockMvc.perform(MockMvcRequestBuilders.post("/messages")
                        .with(asUser("user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("result.id").value("msg-1"))
                .andExpect(jsonPath("result.content").value("hello"));

        verify(chatApiRateLimitService).checkMessageWrite("user-1", "conv-1");
    }

    @Test
    void createChatMessage_unauthenticated_returns401() throws Exception {
        ChatMessageCreateRequest request = ChatMessageCreateRequest.builder()
                .conversationId("conv-1").messageType(MessageType.TEXT).content("hello").build();

        mockMvc.perform(MockMvcRequestBuilders.post("/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(chatMessageService);
    }

    @Test
    void createChatMessage_missingConversationId_returns400WithAttributeMessage() throws Exception {
        ChatMessageCreateRequest request = ChatMessageCreateRequest.builder()
                .messageType(MessageType.TEXT).content("hello").build();

        mockMvc.perform(MockMvcRequestBuilders.post("/messages")
                        .with(asUser("user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("message").value("Conversation Id must not be null!"));

        verifyNoInteractions(chatMessageService);
    }

    @Test
    void createChatMessage_serviceThrowsAppException_mapsToErrorStatus() throws Exception {
        when(chatMessageService.createChatMessage(any())).thenThrow(new AppException(ErrorCode.CONVERSATION_NOT_FOUND));
        ChatMessageCreateRequest request = ChatMessageCreateRequest.builder()
                .conversationId("conv-missing").messageType(MessageType.TEXT).content("hello").build();

        mockMvc.perform(MockMvcRequestBuilders.post("/messages")
                        .with(asUser("user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("code").value(ErrorCode.CONVERSATION_NOT_FOUND.getCode()));
    }

    @Test
    void updateChatMessage_blankContent_returns400() throws Exception {
        ChatMessageUpdateRequest request = ChatMessageUpdateRequest.builder().chatMessageId("msg-1").content("   ").build();

        mockMvc.perform(MockMvcRequestBuilders.put("/messages")
                        .with(asUser("user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(chatMessageService);
    }

    @Test
    void deleteChatMessage_authenticated_callsServiceAndReturnsOk() throws Exception {
        ChatMessageDeleteRequest request = ChatMessageDeleteRequest.builder().chatMessageId("msg-1").conversationId("conv-1").build();

        mockMvc.perform(MockMvcRequestBuilders.delete("/messages")
                        .with(asUser("user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(chatMessageService).deleteChatMessage(any());
    }

    @Test
    void deleteChatMessage_serviceThrowsUnauthorized_returns403() throws Exception {
        doThrow(new AppException(ErrorCode.UNAUTHORIZED)).when(chatMessageService).deleteChatMessage(any());
        ChatMessageDeleteRequest request = ChatMessageDeleteRequest.builder().chatMessageId("msg-1").conversationId("conv-1").build();

        mockMvc.perform(MockMvcRequestBuilders.delete("/messages")
                        .with(asUser("user-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("code").value(ErrorCode.UNAUTHORIZED.getCode()));
    }

    @Test
    void seenAt_authenticated_returnsOk() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.put("/messages/mark-as-seen/conv-1").with(asUser("user-1")))
                .andExpect(status().isOk());

        verify(chatMessageService).seenAt("conv-1");
    }

    @Test
    void getUnreadCount_authenticated_returnsBody() throws Exception {
        when(chatMessageService.getUnreadCount()).thenReturn(
                UnreadCountResponse.builder().total(5L).data(Map.of("conv-1", 5L)).build());

        mockMvc.perform(MockMvcRequestBuilders.get("/messages/unread-count").with(asUser("user-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("result.total").value(5));
    }
}
