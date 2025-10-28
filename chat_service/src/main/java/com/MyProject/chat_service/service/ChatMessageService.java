package com.MyProject.chat_service.service;

import com.MyProject.chat_service.dto.request.ChatMessageCreateRequest;
import com.MyProject.chat_service.dto.request.ChatMessageDeleteRequest;
import com.MyProject.chat_service.dto.request.ChatMessageUpdateRequest;
import com.MyProject.chat_service.dto.response.ChatMessageResponse;
import com.MyProject.chat_service.dto.response.PageResponse;
import com.MyProject.chat_service.entity.*;
import com.MyProject.chat_service.exception.AppException;
import com.MyProject.chat_service.exception.ErrorCode;
import com.MyProject.chat_service.mapper.ChatMessageMapper;
import com.MyProject.chat_service.repository.ChatMessageRepository;
import com.MyProject.chat_service.repository.ConversationRepository;
import com.MyProject.chat_service.repository.WebSocketSessionRepository;
import com.MyProject.chat_service.repository.httpclient.ProfileClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ChatMessageService {
    ProfileClient profileClient;
    WebSocketSessionRepository webSocketSessionRepository;
    ChatMessageMapper chatMessageMapper;
    ConversationRepository conversationRepository;
    ChatMessageRepository chatMessageRepository;
    SocketIOServer socketIOServer;

    private String getUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Jwt jwt = ((JwtAuthenticationToken) authentication).getToken();
        return jwt.getClaim("userId");
    }

    private ChatMessageResponse toChatMessageOwnerResponse(ChatMessage message) {
        String userId = getUserId();

        var chatMessageResponse = chatMessageMapper.toChatMessageResponse(message);
        chatMessageResponse.setMe(userId.equals(message.getSender().getUserId()));

        return chatMessageResponse;
    }

    @Transactional
    public ChatMessageResponse createChatMessage(ChatMessageCreateRequest request) {
        String userId = getUserId();

        var conversation = conversationRepository.findById(request.getConversationId())
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));

        var participants = conversation.getParticipantInfos();
        participants.stream()
                .filter(participantInfo -> userId.equals(participantInfo.getUserId()))
                .findAny()
                .orElseThrow(() -> new AppException(ErrorCode.USERID_NOT_FOUND));

        var user = profileClient.getUserProfile(userId).getResult();

        var chatMessage = chatMessageMapper.toChatMessage(request);
        chatMessage.setId(UUID.randomUUID().toString());
        chatMessage.setSender(ParticipantInfo.builder()
                        .userId(userId)
                        .displayName(user.getDisplayName())
                        .avatar(user.getAvatar())
                .build());
        chatMessage.setMessageType(MessageType.valueOf(request.getMessageType()));
        chatMessage.setCreatedDate(Instant.now());
        chatMessage.setModifiedDate(Instant.now());
        chatMessage.setMessageStatus(MessageStatus.SENT);
        chatMessage.setSeenAtMap(new HashMap<>());

        var response = toChatMessageOwnerResponse(chatMessageRepository.save(chatMessage));

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        try {
            String jsonResponse = objectMapper.writeValueAsString(response);
            Set<String> listUserIds = participants.stream()
                    .map(ParticipantInfo::getUserId)
                    .filter(id -> !Objects.equals(id, userId))
                    .collect(Collectors.toSet());

            Set<String> listSession = webSocketSessionRepository.findAllByUserIdIn(listUserIds).stream()
                    .map(WebSocketSession::getSocketSessionId)
                    .collect(Collectors.toSet());

            socketIOServer.getAllClients().forEach(socketIOClient -> {
                if (listSession.contains(socketIOClient.getSessionId().toString())) {
                    socketIOClient.sendEvent("message", jsonResponse);
                }
                    }
            );
        } catch (JsonProcessingException e) {
            throw new AppException(ErrorCode.JSON_PROCESSING);
        }

        return response;
    }

    public PageResponse<ChatMessageResponse> getMyChatMessages(String conversationId, int page, int size) {
        String userId = getUserId();
        conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND))
                .getParticipantInfos()
                .stream()
                .filter(participantInfo -> userId.equals(participantInfo.getUserId()))
                .findAny()
                .orElseThrow(() -> new AppException(ErrorCode.USERID_NOT_FOUND));

        Sort sort = Sort.by("createdDate").ascending();
        Pageable pageable = PageRequest.of(page - 1, size, sort);

        Page<ChatMessage> pageData = chatMessageRepository.findChatMessage(conversationId, pageable);

        List<ChatMessageResponse> chatMessageList = pageData.getContent().stream()
                .map(this::toChatMessageOwnerResponse)
                .toList();

        return PageResponse.<ChatMessageResponse>builder()
                .currentPage(page)
                .pageSize(size)
                .totalPages(pageData.getTotalPages())
                .totalElement(pageData.getTotalElements())
                .data(chatMessageList)
                .build();
    }

    @Transactional
    public ChatMessageResponse deleteChatMessage(ChatMessageDeleteRequest request) {
        var chatMessage = chatMessageRepository.findById(request.getChatMessageId())
                .orElseThrow(() -> new AppException(ErrorCode.MESSAGE_NOT_FOUND));
        chatMessage.setMessageType(MessageType.valueOf(request.getDeleteType()));

        return toChatMessageOwnerResponse(chatMessageRepository.save(chatMessage));
    }

    @Transactional
    public ChatMessageResponse updateChatMessage(ChatMessageUpdateRequest request) {
        var chatMessage = chatMessageRepository.findById(request.getChatMessageId())
                .orElseThrow(() -> new AppException(ErrorCode.MESSAGE_NOT_FOUND));
        chatMessage.setContent(request.getContent());
        chatMessage.setModifiedDate(Instant.now());

        return toChatMessageOwnerResponse(chatMessageRepository.save(chatMessage));
    }

    @Transactional
    public void seenAt(String conversationId) {
        var userId = getUserId();
        var check = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND))
                .getParticipantInfos().stream().anyMatch(participantInfo ->
                    participantInfo.getUserId().equals(userId)
                );

        if (!check)
            throw new AppException(ErrorCode.USERID_NOT_FOUND);

        List<ChatMessage> msg = chatMessageRepository
                .findAllByConversationIdAndSeenAtMapNotContainsKey(conversationId, userId);
        for (ChatMessage cm : msg) {
            cm.getSeenAtMap().put(userId, Instant.now());
        }

        chatMessageRepository.saveAll(msg);
    }
}
