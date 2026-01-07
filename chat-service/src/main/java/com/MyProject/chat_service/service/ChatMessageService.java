package com.MyProject.chat_service.service;

import com.MyProject.chat_service.dto.request.ChatMessageCreateRequest;
import com.MyProject.chat_service.dto.request.ChatMessageDeleteRequest;
import com.MyProject.chat_service.dto.request.ChatMessageUpdateRequest;
import com.MyProject.common.dto.request.BulkUserProfileRequest;
import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.chat_service.entity.*;
import com.MyProject.chat_service.exception.AppException;
import com.MyProject.chat_service.exception.ErrorCode;
import com.MyProject.chat_service.mapper.ChatMessageMapper;
import com.MyProject.chat_service.repository.ChatMessageRepository;
import com.MyProject.chat_service.repository.ConversationRepository;
import com.MyProject.chat_service.repository.httpclient.ProfileClient;
import com.MyProject.common.dto.response.ChatMessageResponse;
import com.MyProject.common.dto.response.UserProfileResponse;
import com.MyProject.common.redis.RedisService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ChatMessageService {
    ProfileClient profileClient;
    ChatMessageMapper chatMessageMapper;
    ConversationRepository conversationRepository;
    ChatMessageRepository chatMessageRepository;
    KafkaTemplate<String, Object> kafkaTemplate;
    RedisService redisService;

    private String getMessageCacheKey(String conversationId) {
        return "chat:messages:" + conversationId;
    }

    private String getUnreadSetKey(String conversationId, String userId) {
        return "chat:unread:" + conversationId + ":" + userId;
    }

    private String getUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
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

        String userCacheKey = "profile:user:" + userId;
        UserProfileResponse user = (UserProfileResponse) redisService.get(userCacheKey);
        if (user == null) {
            user = profileClient.getBulkUserProfiles(
                            BulkUserProfileRequest.builder().userIds(List.of(userId)).build())
                    .getResult().getFirst();
            redisService.setWithExpiration(userCacheKey, user, 24, TimeUnit.HOURS);
        }

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

        var savedMessage = chatMessageRepository.save(chatMessage);
        var response = toChatMessageOwnerResponse(savedMessage);

        String msgKey = getMessageCacheKey(request.getConversationId());
        redisService.listLeftPush(msgKey, response);
        redisService.listTrim(msgKey, 0, 99);

        participants.stream()
                .filter(p -> !p.getUserId().equals(userId))
                .forEach(p -> redisService.addSet(getUnreadSetKey(request.getConversationId(), p.getUserId()), savedMessage.getId()));

        kafkaTemplate.send("send-message", response);
        return response;
    }

    public PageResponse<ChatMessageResponse> getMyChatMessages(String conversationId, int page, int size) {
        String userId = getUserId();
        String redisKey = getMessageCacheKey(conversationId);
        conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND))
                .getParticipantInfos()
                .stream()
                .filter(participantInfo -> userId.equals(participantInfo.getUserId()))
                .findAny()
                .orElseThrow(() -> new AppException(ErrorCode.USERID_NOT_FOUND));

        if (page == 1) {
            List<Object> cached = redisService.listRange(redisKey, 0, size - 1);
            if (cached != null && !cached.isEmpty()) {
                return PageResponse.<ChatMessageResponse>builder()
                        .data(cached.stream().map(m -> (ChatMessageResponse) m).toList())
                        .currentPage(1)
                        .pageSize(size)
                        .build();
            }
        }

        Sort sort = Sort.by("createdDate").descending();
        Pageable pageable = PageRequest.of(page - 1, size, sort);
        Page<ChatMessage> pageData = chatMessageRepository.findChatMessage(conversationId, pageable);

        List<ChatMessageResponse> chatMessageList = pageData.getContent().stream()
                .map(this::toChatMessageOwnerResponse)
                .toList();

        if (page == 1 && !chatMessageList.isEmpty()) {
            redisService.delete(redisKey);
            List<ChatMessageResponse> reverseList = new ArrayList<>(chatMessageList);
            Collections.reverse(reverseList);
            reverseList.forEach(m -> redisService.listLeftPush(redisKey, m));
            redisService.setWithExpiration(redisKey, 24, 5000, TimeUnit.HOURS);
        }

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

        var saved = chatMessageRepository.save(chatMessage);

        redisService.delete(getMessageCacheKey(chatMessage.getConversationId()));

        return toChatMessageOwnerResponse(saved);
    }

    @Transactional
    public ChatMessageResponse updateChatMessage(ChatMessageUpdateRequest request) {
        var chatMessage = chatMessageRepository.findById(request.getChatMessageId())
                .orElseThrow(() -> new AppException(ErrorCode.MESSAGE_NOT_FOUND));
        chatMessage.setContent(request.getContent());
        chatMessage.setModifiedDate(Instant.now());

        var saved = chatMessageRepository.save(chatMessage);

        redisService.delete(getMessageCacheKey(chatMessage.getConversationId()));

        return toChatMessageOwnerResponse(saved);
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

        redisService.delete(getUnreadSetKey(conversationId, userId));

        redisService.delete(getMessageCacheKey(conversationId));
    }
}
