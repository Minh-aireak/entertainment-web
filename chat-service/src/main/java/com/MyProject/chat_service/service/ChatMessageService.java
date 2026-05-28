package com.MyProject.chat_service.service;

import com.MyProject.chat_service.document.ChatMessageDoc;
import com.MyProject.chat_service.document.ConversationDoc;
import com.MyProject.chat_service.dto.event.ConversationSeenEvent;
import com.MyProject.chat_service.dto.event.MessageCreatedEvent;
import com.MyProject.chat_service.dto.event.NotificationEvent;
import com.MyProject.chat_service.dto.request.ChatMessageCreateRequest;
import com.MyProject.chat_service.dto.request.ChatMessageDeleteRequest;
import com.MyProject.chat_service.dto.request.ChatMessageUpdateRequest;
import com.MyProject.chat_service.dto.response.UnreadCountResponse;
import com.MyProject.chat_service.entity.*;
import com.MyProject.chat_service.repository.mongo.OutboxRepository;
import com.MyProject.chat_service.repository.elasticsearch.ChatMessageElasticRepository;
import com.MyProject.chat_service.repository.mongo.ConversationMemberRepository;
import com.MyProject.common.dto.request.BulkUserProfileRequest;
import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.chat_service.exception.AppException;
import com.MyProject.chat_service.exception.ErrorCode;
import com.MyProject.chat_service.mapper.ChatMessageMapper;
import com.MyProject.chat_service.repository.mongo.ChatMessageRepository;
import com.MyProject.chat_service.repository.mongo.ConversationRepository;
import com.MyProject.chat_service.repository.httpclient.ProfileClient;
import com.MyProject.chat_service.dto.response.ChatMessageResponse;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ChatMessageService {
    ProfileClient profileClient;
    ChatMessageMapper chatMessageMapper;
    ConversationRepository conversationRepository;
    ChatMessageRepository chatMessageRepository;
    ConversationMemberRepository conversationMemberRepository;
    ChatMessageElasticRepository chatMessageElasticRepository;
    RedisService redisService;
    OutboxRepository outboxRepository;
    com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private String getLastMessageCacheKey(String conversationId) {
        return "chat:last-message:" + conversationId;
    }

    private String getReadCacheKey(String userId, String conversationId) {
        return "chat:read:" + userId + ":" + conversationId;
    }

    private String getUnreadCountCacheKey(String userId, String conversationId) {
        return "chat:unread:" + userId + ":" + conversationId;
    }

    private String getProfileCacheKey(String userId) {
        return "profile:user:" + userId;
    }

    private String getUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        Jwt jwt = ((JwtAuthenticationToken) authentication).getToken();
        return jwt.getClaim("userId");
    }

    private ChatMessage handleMessageContent(String userId, ChatMessageCreateRequest request, String clientMessageId){

        var mapperChatMessage = chatMessageMapper.toChatMessage(request);
        MessageType type = request.getMessageType();

        if(type == MessageType.TEXT){

            if(request.getContent() == null
                    || request.getContent().isBlank()){

                throw new AppException(
                        ErrorCode.CONTENT_REQUIRED);
            }

            mapperChatMessage.setContent(request.getContent());

        } else {

            if(request.getAttachmentFileUrl() == null
                    || request.getAttachmentFileUrl().isBlank()){

                throw new AppException(
                        ErrorCode.FILE_REQUIRED);
            }

            mapperChatMessage.setContent(type.getDefaultContent());
        }
        mapperChatMessage.setSenderId(userId);
        mapperChatMessage.setMessageStatus(MessageStatus.SENT);

        return mapperChatMessage;
    }

    private String getTotalUnreadCacheKey(String userId) {
        return "chat:unread:total:" + userId;
    }

    private void updateSeen(String conversationId, ChatMessage savedMessage, String userId) {
        String unreadKey = getUnreadCountCacheKey(userId, conversationId);
        String totalUnreadKey = getTotalUnreadCacheKey(userId);

        try {
            // Atomic reset using Lua script
            redisService.resetUnreadCount(unreadKey, totalUnreadKey);
        } catch (Exception e) {
            log.warn("Failed to reset unread counters in Redis for user {}", userId);
        }

        // Redis: last seen pointer for read receipts (Store SEQ instead of content for better logic)
        redisService.setWithExpiration(
                getReadCacheKey(userId, conversationId),
                String.valueOf(savedMessage.getSeq()),
                12,
                TimeUnit.HOURS
        );

        // DB update
        conversationMemberRepository
                .findByConversationIdAndUserId(conversationId, userId)
                .ifPresent(member -> {
                    member.setLastSeenMessageId(savedMessage.getId());
                    member.setLastSeenSeq(savedMessage.getSeq());
                    member.setLastSeenAt(Instant.now());
                    conversationMemberRepository.save(member);
                });
    }

    @Transactional
    public ChatMessageResponse createChatMessage(ChatMessageCreateRequest request) {

        String userId = getUserId();

        // 1. Efficiently validate membership before any state change
        if (!conversationMemberRepository.existsByConversationIdAndUserId(request.getConversationId(), userId)) {
            throw new AppException(ErrorCode.USERID_NOT_FOUND);
        }

        // 2. Idempotency check: If clientMessageId is provided, check if it already exists
        if (request.getClientMessageId() != null && !request.getClientMessageId().isBlank()) {
            Optional<ChatMessage> existing = chatMessageRepository.findByClientMessageId(request.getClientMessageId());
            if (existing.isPresent()) {
                log.info("Duplicate message detected with clientMessageId: {}", request.getClientMessageId());
                return chatMessageMapper.toChatMessageResponse(existing.get());
            }
        }

        // 3. Prepare message content and validate input (No DB change yet)
        var chatMessage = handleMessageContent(userId, request, request.getClientMessageId());
        
        // 3. ATOMIC UPDATE: Increment sequence and update last message in ONE DB call
        Conversation conversation = conversationRepository.incrementSeqAndUpdateLastMessage(
                request.getConversationId(), 
                chatMessage.getContent()
        );

        if (conversation == null) {
            throw new AppException(ErrorCode.CONVERSATION_NOT_FOUND);
        }

        // 4. Assign the ACTUAL seq returned from DB
        chatMessage.setSeq(conversation.getTotalSeq());
        
        var savedMessage = chatMessageRepository.save(chatMessage);
        var response = chatMessageMapper.toChatMessageResponse(savedMessage);

        // 5. Save to Outbox for CDC to elasticsearch
        publishMessageEvent("chat.message.created", response);

        // 6. Save to Outbox for CDC to notification service
        List<String> otherUserIds = conversation.getUserIds()
                .stream()
                .filter(id -> !id.equals(userId))
                .toList();
        saveToOutbox(
                response.getId(),
                "chat.message.created.notification",
                NotificationEvent.builder()
                        .typeNotification(TypeNotification.NEW_CHAT)
                        .userIdSender(response.getSenderId())
                        .toUserIds(otherUserIds)
                        .build());

        // 7. Update sender seen (Pointer management)
        updateSeen(request.getConversationId(), savedMessage, userId);

        // 8. Update last message cache for conversation
        updateLastMessageCache(request.getConversationId(), response.getContent());
        response.setMe(true);

        return response;
    }

    public PageResponse<ChatMessageResponse> getMyChatMessages(String conversationId, int page, int size) {
        String userId = getUserId();

        var conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));

        var participants = conversationMemberRepository.findByUserId(userId);
        boolean isParticipant = participants.stream()
                .anyMatch(p -> userId.equals(p.getUserId()));

        if (!isParticipant) {
            throw new AppException(ErrorCode.USERID_NOT_FOUND);
        }

        Sort sort = Sort.by("createdDate").descending();
        Pageable pageable = PageRequest.of(page - 1, size, sort);
        Page<ChatMessage> pageData = chatMessageRepository.findByConversationId(conversationId, pageable);

        // 1. Collect unique senderIds
        Set<String> senderIds = pageData.getContent().stream()
                .map(ChatMessage::getSenderId)
                .collect(Collectors.toSet());

        // 2. Enrich profiles (Bulk)
        Map<String, UserProfileResponse> profileMap = enrichProfiles(senderIds);

        // 3. Map to response with profile info
        List<ChatMessageResponse> chatMessageResponseList = pageData.getContent().stream()
                .map(a -> {
                    var response = chatMessageMapper.toChatMessageResponse(a);
                    if (response.getMessageType() == MessageType.DELETED_FOR_EVERYONE)
                        response.setContent(MessageType.DELETED_FOR_EVERYONE.getDefaultContent());
                    
                    response.setMe(a.getSenderId().equals(userId));
                    
                    // Fill profile info from map
                    UserProfileResponse profile = profileMap.get(a.getSenderId());
                    if (profile != null) {
                        response.setSenderName(profile.getDisplayName());
                        response.setSenderAvatar(profile.getAvatar());
                    }
                    
                    return response;
                })
                .toList();

        return PageResponse.<ChatMessageResponse>builder()
                .currentPage(page)
                .pageSize(size)
                .totalPages(pageData.getTotalPages())
                .totalElement(pageData.getTotalElements())
                .data(chatMessageResponseList)
                .build();
    }

    private Map<String, UserProfileResponse> enrichProfiles(Set<String> senderIds) {
        if (senderIds.isEmpty()) return Collections.emptyMap();

        // Giữ thứ tự để map index với senderIds
        List<String> senderIdList = new ArrayList<>(senderIds);
        List<String> keys = senderIdList.stream()
                .map(this::getProfileCacheKey)
                .toList();

        // MultiGet 1 lần thay vì loop
        List<Object> cachedValues = redisService.multiGet(keys);

        Map<String, UserProfileResponse> result = new HashMap<>();
        List<String> missingIds = new ArrayList<>();

        for (int i = 0; i < senderIdList.size(); i++) {
            Object cached = cachedValues.get(i);
            if (Objects.nonNull(cached)) {
                result.put(senderIdList.get(i), (UserProfileResponse) cached);
            } else {
                missingIds.add(senderIdList.get(i));
            }
        }

        // Fetch missing từ Profile Service
        if (!missingIds.isEmpty()) {
            try {
                Map<String, UserProfileResponse> fetchedProfiles = profileClient.getBulkUserProfiles(
                                BulkUserProfileRequest.builder()
                                        .userIds(new HashSet<>(missingIds))
                                        .build())
                        .getResult();

                fetchedProfiles.forEach((userId, profile) -> {
                    result.put(userId, profile);
                    redisService.setWithExpiration(
                            getProfileCacheKey(userId), profile, 1, TimeUnit.HOURS);
                });
            } catch (Exception e) {
                log.error("Failed to fetch bulk profiles for ids: {}", missingIds, e);
            }
        }

        return result;
    }

    private void updateLastMessageCache(String conversationId, String contentResponse) {

        String key = getLastMessageCacheKey(conversationId);

        redisService.setWithExpiration(
                key,
                contentResponse,
                12,
                TimeUnit.HOURS
        );
    }

    @Transactional
    public void deleteChatMessage(ChatMessageDeleteRequest request) {

        String userId = getUserId();

        ChatMessage chatMessage = chatMessageRepository.findById(request.getChatMessageId())
                .orElseThrow(() -> new AppException(ErrorCode.MESSAGE_NOT_FOUND));
        String chatMessageId = chatMessage.getId();

        if (!chatMessage.getSenderId().equals(userId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        String conversationId = chatMessage.getConversationId();

        // 1. Update status in MongoDB
        chatMessage.setMessageType(MessageType.DELETED_FOR_EVERYONE);
        chatMessageRepository.save(chatMessage);

        // 2. Prepare response for event
        var response = chatMessageMapper.toChatMessageResponse(chatMessage);
        response.setContent(MessageType.DELETED_FOR_EVERYONE.getDefaultContent());

        // 3. Save to Outbox for CDC (Sync to ES and notify other users)
        publishMessageEvent("chat.message.deleted", response);

        // 4. Update Conversation lastMessage if this was the last one
        Optional<ChatMessage> lastMessage = chatMessageRepository.findTopByConversationIdOrderByCreatedDateDesc(conversationId);
        if (lastMessage.isPresent() && lastMessage.get().getId().equals(chatMessage.getId())) {
            conversationRepository.deleteLastMessage(
                    conversationId,
                    chatMessage.getMessageType().getDefaultContent());

            updateLastMessageCache(conversationId, chatMessage.getMessageType().getDefaultContent());

            // 5. Save to Outbox for Conversation sync to ES
            try {
                outboxRepository.save(Outbox.builder()
                        .aggregateId(chatMessage.getId())
                        .topic("chat.conversation.deleted")
                        .payload(objectMapper.writeValueAsString(ConversationDoc.builder()
                                .id(conversationId)
                                .lastMessage(chatMessage.getMessageType().getDefaultContent())
                                .deleted(true)
                                .build()))
                        .build());
            } catch (Exception e) {
                log.error("Failed to serialize conversation update for outbox", e);
            }
        }
    }

    @Transactional
    public ChatMessageResponse updateChatMessage(ChatMessageUpdateRequest request) {
        var chatMessage = chatMessageRepository.findById(request.getChatMessageId())
                .orElseThrow(() -> new AppException(ErrorCode.MESSAGE_NOT_FOUND));

        if (!chatMessage.getSenderId().equals(getUserId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        if (chatMessage.getMessageType() != MessageType.TEXT)
            throw new AppException(ErrorCode.MESSAGE_TYPE_NOT_TEXT);

        // 1. Update content in MongoDB
        chatMessage.setContent(request.getContent());
        var saved = chatMessageRepository.save(chatMessage);
        var response = chatMessageMapper.toChatMessageResponse(saved);

        // 2. Save to Outbox for CDC (Sync to ES and notify other users)
        publishMessageEvent("chat.message.updated", response);

        // 3. Update Conversation lastMessage and Cache if needed
        Optional<ChatMessage> lastMessage = chatMessageRepository.findTopByConversationIdOrderByCreatedDateDesc(chatMessage.getConversationId());
        if (lastMessage.isPresent() && lastMessage.get().getId().equals(chatMessage.getId())) {
            conversationRepository.updateLastMessage(chatMessage.getConversationId(), request.getContent());
            updateLastMessageCache(chatMessage.getConversationId(), request.getContent());

            // 4. Save to Outbox for Conversation sync to ES
            try {
                outboxRepository.save(Outbox.builder()
                        .aggregateId(chatMessage.getId())
                        .topic("chat.conversation.updated")
                        .payload(objectMapper.writeValueAsString(ConversationDoc.builder()
                                .id(chatMessage.getConversationId())
                                .lastMessage(request.getContent())
                                .build()))
                        .build());
            } catch (Exception e) {
                log.error("Failed to serialize conversation update for outbox", e);
            }
        }

        return response;
    }

    @Transactional
    public void seenAt(String conversationId) {

        String userId = getUserId();

        // 1. check conversation + participant
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));

        // 2. lấy message mới nhất (VISIBLE message)
        Optional<ChatMessage> lastMessage = 
                chatMessageRepository.findTopByConversationIdOrderByCreatedDateDesc(conversationId);
        if (lastMessage.isEmpty())
            return;

        String lastMessageId = lastMessage.get().getId();

        // Update user seen
        updateSeen(conversationId, lastMessage.get(), userId);

        // 3. Real-time notification: Notify others that this user has seen the message
        ConversationMember currentMember = conversationMemberRepository.findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_MEMBER_NOT_FOUND));

        List<String> receiverIds = conversationMemberRepository.findByConversationIdAndUserIdNot(conversationId, userId)
                .stream()
                .map(ConversationMember::getUserId)
                .toList();

        if (!receiverIds.isEmpty()) {
            try {
                outboxRepository.save(Outbox.builder()
                        .aggregateId(currentMember.getId())
                        .topic("chat.conversation.seen")
                        .payload(objectMapper.writeValueAsString(ConversationSeenEvent.builder()
                                .eventId(UUID.randomUUID().toString())
                                .eventType("chat.conversation.seen")
                                .timestamp(Instant.now())
                                .conversationId(conversationId)
                                .userId(userId)
                                .lastSeenMessageId(lastMessageId)
                                .receiverIds(receiverIds)
                                .build()))
                        .build());
            } catch (Exception e) {
                log.error("Failed to publish seen event to outbox", e);
            }
        }
    }

    public UnreadCountResponse getUnreadCount() {
        String userId = getUserId();
        String totalKey = getTotalUnreadCacheKey(userId);

        // 1. Try to get total from Redis first
        Object totalObj = redisService.get(totalKey);
        
        List<ConversationMember> members = conversationMemberRepository.findByUserId(userId);
        Map<String, Long> data = new HashMap<>();
        
        boolean hasTotalInRedis = totalObj != null;
        long total = hasTotalInRedis ? Long.parseLong(totalObj.toString()) : 0L;

        for (ConversationMember m : members) {
            Object convUnreadObj = redisService.get(getUnreadCountCacheKey(userId, m.getConversationId()));
            if (convUnreadObj != null) {
                data.put(m.getConversationId(), Long.parseLong(convUnreadObj.toString()));
            } else {
                // Fallback to DB and warm cache
                long unread = calculateAndCacheUnread(userId, m);
                data.put(m.getConversationId(), unread);
                if (!hasTotalInRedis) total += unread;
            }
        }

        if (!hasTotalInRedis) {
            redisService.set(totalKey, total);
        }

        return UnreadCountResponse.builder()
                .data(data)
                .total(total)
                .build();
    }

    private long calculateAndCacheUnread(String userId, ConversationMember m) {
        String conversationId = m.getConversationId();
        Long lastSeenSeq = m.getLastSeenSeq();
        
        Conversation conversation = conversationRepository.findById(conversationId).orElse(null);
        if (conversation == null) return 0L;

        long totalSeq = conversation.getTotalSeq();
        long unread = (lastSeenSeq == null) ? totalSeq : Math.max(0, totalSeq - lastSeenSeq);

        redisService.setWithExpiration(
                getUnreadCountCacheKey(userId, conversationId),
                unread,
                12,
                TimeUnit.HOURS
        );
        return unread;
    }

    public PageResponse<ChatMessageResponse> searchMessages(String conversationId, String query, int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by("createdAt").descending());
        var searchResult = chatMessageElasticRepository.searchMessages(conversationId, query, pageable);

        // 1. Collect unique senderIds
        Set<String> senderIds = searchResult.getContent().stream()
                .map(ChatMessageDoc::getSenderId)
                .collect(Collectors.toSet());

        // 2. Enrich profiles (Bulk: Redis check -> Service fallback -> Warm cache)
        Map<String, UserProfileResponse> profileMap = enrichProfiles(senderIds);

        // 3. Map to response with profile info
        List<ChatMessageResponse> data = searchResult.getContent().stream()
                .map(doc -> {
                    var response = ChatMessageResponse.builder()
                            .id(doc.getId())
                            .conversationId(doc.getConversationId())
                            .senderId(doc.getSenderId())
                            .content(doc.getContent())
                            .createdDate(doc.getCreatedAt())
                            .seq(doc.getSeq())
                            .clientMessageId(doc.getClientMessageId())
                            .build();
                    
                    // Fill profile info
                    UserProfileResponse profile = profileMap.get(doc.getSenderId());
                    if (profile != null) {
                        response.setSenderName(profile.getDisplayName());
                        response.setSenderAvatar(profile.getAvatar());
                    }
                    
                    return response;
                })
                .toList();

        return PageResponse.<ChatMessageResponse>builder()
                .currentPage(page)
                .pageSize(size)
                .totalPages(searchResult.getTotalPages())
                .totalElement(searchResult.getTotalElements())
                .data(data)
                .build();
    }

    private void publishMessageEvent(String topic, ChatMessageResponse response) {
        List<String> receiverIds = conversationMemberRepository
                .findByConversationIdAndUserIdNot(response.getConversationId(), response.getSenderId())
                .stream()
                .map(ConversationMember::getUserId)
                .toList();

       saveToOutbox(response.getId(), topic, MessageCreatedEvent.builder()
               .eventId(UUID.randomUUID().toString())
               .timestamp(Instant.now())
               .producer("chat-service")
               .receiverIds(receiverIds)
               .message(response)
               .build());
    }

    private void saveToOutbox(String aggregate, String topic, Object payload) {
        try {
            outboxRepository.save(Outbox.builder()
                    .aggregateId(aggregate)
                    .topic(topic)
                    .payload(objectMapper.writeValueAsString(payload))
                    .build());
        } catch (Exception e) {
            log.error("Failed save outbox with topic: {}", topic);
        }
    }
}
