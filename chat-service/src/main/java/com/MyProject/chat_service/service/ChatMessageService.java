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
import com.MyProject.chat_service.enums.MessageStatus;
import com.MyProject.chat_service.enums.MessageType;
import com.MyProject.chat_service.repository.mongo.OutboxRepository;
import com.MyProject.chat_service.repository.elasticsearch.ChatMessageElasticRepository;
import com.MyProject.chat_service.repository.mongo.ConversationMemberRepository;
import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.chat_service.exception.AppException;
import com.MyProject.chat_service.enums.ErrorCode;
import com.MyProject.chat_service.mapper.ChatMessageMapper;
import com.MyProject.chat_service.repository.mongo.ChatMessageRepository;
import com.MyProject.chat_service.repository.mongo.ConversationRepository;
import com.MyProject.chat_service.dto.response.ChatMessageResponse;
import com.MyProject.common.dto.response.UserProfileResponse;
import com.MyProject.common.redis.RedisService;
import com.MyProject.common.security.SecurityUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.*;

import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ChatMessageService {
    ChatProfileExternalService chatProfileExternalService;
    ChatMessageMapper chatMessageMapper;
    ConversationRepository conversationRepository;
    ChatMessageRepository chatMessageRepository;
    ConversationMemberRepository conversationMemberRepository;
    ChatMessageElasticRepository chatMessageElasticRepository;
    RedisService redisService;
    OutboxRepository outboxRepository;
    ObjectMapper objectMapper;
    ChatFileUrlResolver chatFileUrlResolver;

    private ChatMessageResponse buildMessageResponse(ChatMessage message) {
        ChatMessageResponse response = chatMessageMapper.toChatMessageResponse(message);
        response.setAttachmentFileUrl(chatFileUrlResolver.resolve(message.getAttachmentFileId()));
        return response;
    }

    private String getLastMessageCacheKey(String conversationId) {
        return "chat:last-message:" + conversationId;
    }

    private String getReadCacheKey(String userId, String conversationId) {
        return "chat:read:" + userId + ":" + conversationId;
    }

    private String getUnreadCountCacheKey(String userId, String conversationId) {
        return "chat:unread:" + userId + ":" + conversationId;
    }

    private String getConversationCacheKey(String conversationId) {
        return "chat:conversation:" + conversationId;
    }

    private String getProfileCacheKey(String userId) {
        return "profile:user:" + userId;
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

            if(request.getAttachmentFileId() == null
                    || request.getAttachmentFileId().isBlank()){

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

    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            action.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private void syncSeenCache(String conversationId, ChatMessage savedMessage, String userId) {
        String unreadKey = getUnreadCountCacheKey(userId, conversationId);
        String totalUnreadKey = getTotalUnreadCacheKey(userId);

        try {
            redisService.resetUnreadCount(unreadKey, totalUnreadKey);
        } catch (Exception e) {
            log.warn("Failed to reset unread counters in Redis for user {}", userId);
        }

        try {
            redisService.setWithExpiration(
                    getReadCacheKey(userId, conversationId),
                    String.valueOf(savedMessage.getSeq()),
                    12,
                    TimeUnit.HOURS
            );
        } catch (Exception e) {
            log.warn("Failed to update read pointer in Redis for user {}", userId);
        }
    }

    private void scheduleSeenCacheSync(String conversationId, ChatMessage savedMessage, String userId) {
        runAfterCommit(() -> syncSeenCache(conversationId, savedMessage, userId));
    }

    private ConversationMember updateSeenState(String conversationId, ChatMessage savedMessage, String userId) {
        ConversationMember member = conversationMemberRepository
                .findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_MEMBER_NOT_FOUND));

        member.setLastSeenMessageId(savedMessage.getId());
        member.setLastSeenSeq(savedMessage.getSeq());
        member.setLastSeenAt(Instant.now());
        return conversationMemberRepository.save(member);
    }

    @Transactional
    public ChatMessageResponse createChatMessage(ChatMessageCreateRequest request) {

        String userId = SecurityUtils.getCurrentUserId();

        // 1. Efficiently validate membership before any state change
        if (!conversationMemberRepository.existsByConversationIdAndUserId(request.getConversationId(), userId)) {
            throw new AppException(ErrorCode.USERID_NOT_FOUND);
        }

        // 2. Idempotency check: If clientMessageId is provided, check if it already exists
        if (request.getClientMessageId() != null && !request.getClientMessageId().isBlank()) {
            Optional<ChatMessage> existing = chatMessageRepository.findByClientMessageIdAndSenderIdAndConversationId(
                    request.getClientMessageId(),
                    userId,
                    request.getConversationId()
            );
            if (existing.isPresent()) {
                log.info("Duplicate message detected with clientMessageId: {}", request.getClientMessageId());
                ChatMessageResponse duplicateResponse = buildMessageResponse(existing.get());
                duplicateResponse.setMe(true);
                return duplicateResponse;
            }
        }

        // 3. If replying, the target message must exist in the same conversation
        ChatMessage replyTarget = null;
        if (request.getReplyToMessageId() != null && !request.getReplyToMessageId().isBlank()) {
            replyTarget = chatMessageRepository.findById(request.getReplyToMessageId())
                    .filter(m -> m.getConversationId().equals(request.getConversationId()))
                    .orElseThrow(() -> new AppException(ErrorCode.REPLY_TARGET_NOT_FOUND));
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
        
        ChatMessage savedMessage;
        try {
            savedMessage = chatMessageRepository.save(chatMessage);
        } catch (DuplicateKeyException duplicateKeyException) {
            if (request.getClientMessageId() != null && !request.getClientMessageId().isBlank()) {
                return chatMessageRepository.findByClientMessageIdAndSenderIdAndConversationId(
                                request.getClientMessageId(),
                                userId,
                                request.getConversationId()
                        )
                        .map(existing -> {
                            ChatMessageResponse duplicateResponse = buildMessageResponse(existing);
                            duplicateResponse.setMe(true);
                            return duplicateResponse;
                        })
                        .orElseThrow(() -> duplicateKeyException);
            }
            throw duplicateKeyException;
        }
        var response = buildMessageResponse(savedMessage);
        if (replyTarget != null) {
            applyReplyPreview(response, replyTarget, enrichProfiles(Set.of(replyTarget.getSenderId())));
        }

        // 5. Save to Outbox for CDC to elasticsearch
        publishMessageEvent("chat.message.created", response);

        // Save to Outbox for CDC to notification service
        List<String> otherUserIds = conversation.getUserIds()
                .stream()
                .filter(id -> !id.equals(userId))
                .toList();
        saveToOutbox(
                response.getId(),
                "notification.events",
                NotificationEvent.builder()
                        .eventId(java.util.UUID.randomUUID().toString())
                        .typeNotification("NEW_CHAT")
                        .userIdSender(response.getSenderId())
                        .toUserIds(otherUserIds)
                        .conversationId(request.getConversationId())
                        .build());

        // 7. Update sender seen (Pointer management)
        updateSeenState(request.getConversationId(), savedMessage, userId);
        scheduleSeenCacheSync(request.getConversationId(), savedMessage, userId);

        // 8. Refresh caches only after transaction commits successfully
        runAfterCommit(() -> {
            updateLastMessageCache(request.getConversationId(), response.getContent());
            invalidateConversationCache(request.getConversationId());
        });
        response.setMe(true);

        return response;
    }

    public PageResponse<ChatMessageResponse> getMyChatMessages(String conversationId, int page, int size) {
        String userId = SecurityUtils.getCurrentUserId();
        if (!conversationMemberRepository.existsByConversationIdAndUserId(conversationId, userId)) {
            throw new AppException(ErrorCode.USERID_NOT_FOUND);
        }

        Sort sort = Sort.by("createdDate").descending();
        Pageable pageable = PageRequest.of(page - 1, size, sort);
        Page<ChatMessage> pageData = chatMessageRepository.findByConversationId(conversationId, pageable);

        // 1. Batch-fetch reply targets for messages that are replies
        Map<String, ChatMessage> replyTargets = fetchReplyTargets(pageData.getContent());

        // 2. Collect unique senderIds (message senders + reply target senders)
        Set<String> senderIds = pageData.getContent().stream()
                .map(ChatMessage::getSenderId)
                .collect(Collectors.toSet());
        replyTargets.values().forEach(target -> senderIds.add(target.getSenderId()));

        // 3. Enrich profiles (Bulk)
        Map<String, UserProfileResponse> profileMap = enrichProfiles(senderIds);

        // 3b. Resolve attachment URLs for the whole page in parallel (tránh N request tuần tự)
        Map<String, String> attachmentUrls = chatFileUrlResolver.resolveBatch(
                pageData.getContent().stream().map(ChatMessage::getAttachmentFileId).toList());

        // 4. Map to response with profile info
        List<ChatMessageResponse> chatMessageResponseList = pageData.getContent().stream()
                .map(a -> {
                    var response = chatMessageMapper.toChatMessageResponse(a);
                    if (response.getMessageType() == MessageType.DELETED_FOR_EVERYONE)
                        response.setContent(MessageType.DELETED_FOR_EVERYONE.getDefaultContent());

                    response.setMe(a.getSenderId().equals(userId));
                    if (a.getAttachmentFileId() != null) {
                        response.setAttachmentFileUrl(attachmentUrls.get(a.getAttachmentFileId()));
                    }

                    // Fill profile info from map
                    UserProfileResponse profile = profileMap.get(a.getSenderId());
                    if (profile != null) {
                        response.setSenderName(profile.getDisplayName());
                        response.setSenderAvatar(profile.getAvatar());
                    }

                    ChatMessage replyTarget = replyTargets.get(a.getReplyToMessageId());
                    if (replyTarget != null) {
                        applyReplyPreview(response, replyTarget, profileMap);
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
        List<UserProfileResponse> cachedValues = Collections.nCopies(keys.size(), null);
        try {
            cachedValues = redisService.multiGet(keys, new TypeReference<UserProfileResponse>() {});
        } catch (Exception e) {
            log.error("Failed to multiGet profiles from cache for keys: {}", keys, e);
        }

        Map<String, UserProfileResponse> result = new HashMap<>();
        List<String> missingIds = new ArrayList<>();

        for (int i = 0; i < senderIdList.size(); i++) {
            UserProfileResponse cached = (i < cachedValues.size()) ? cachedValues.get(i) : null;
            if (Objects.nonNull(cached)) {
                result.put(senderIdList.get(i), cached);
            } else {
                missingIds.add(senderIdList.get(i));
            }
        }

        // Fetch missing từ Profile Service
        if (!missingIds.isEmpty()) {
            try {
                Map<String, UserProfileResponse> fetchedProfiles =
                        chatProfileExternalService.getBulkUserProfilesForApi(new HashSet<>(missingIds));

                fetchedProfiles.forEach((userId, profile) -> {
                    result.put(userId, profile);
                    try {
                        redisService.setWithExpiration(
                                getProfileCacheKey(userId), profile, 1, TimeUnit.HOURS);
                    } catch (Exception e) {
                        log.error("Failed to set cache for profile userId: {}", userId, e);
                    }
                });
            } catch (Exception e) {
                log.error("Failed to fetch bulk profiles for ids: {}", missingIds, e);
            }
        }

        return result;
    }

    private Map<String, ChatMessage> fetchReplyTargets(Collection<ChatMessage> messages) {
        Set<String> replyIds = messages.stream()
                .map(ChatMessage::getReplyToMessageId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());

        if (replyIds.isEmpty()) return Collections.emptyMap();

        return chatMessageRepository.findAllById(replyIds).stream()
                .collect(Collectors.toMap(ChatMessage::getId, m -> m));
    }

    private void applyReplyPreview(ChatMessageResponse response, ChatMessage replyTarget, Map<String, UserProfileResponse> profileMap) {
        response.setReplyToSenderId(replyTarget.getSenderId());
        response.setReplyToMessageType(replyTarget.getMessageType());
        response.setReplyToContent(
                replyTarget.getMessageType() == MessageType.TEXT
                        ? replyTarget.getContent()
                        : replyTarget.getMessageType().getDefaultContent());

        UserProfileResponse profile = profileMap.get(replyTarget.getSenderId());
        if (profile != null) {
            response.setReplyToSenderName(profile.getDisplayName());
        }
    }

    private void enrichReplyPreview(ChatMessageResponse response, ChatMessage message) {
        String replyId = message.getReplyToMessageId();
        if (replyId == null || replyId.isBlank()) return;

        chatMessageRepository.findById(replyId).ifPresent(target ->
                applyReplyPreview(response, target, enrichProfiles(Set.of(target.getSenderId()))));
    }

    private void updateLastMessageCache(String conversationId, String contentResponse) {

        String key = getLastMessageCacheKey(conversationId);

        try {
            redisService.setWithExpiration(
                    key,
                    contentResponse,
                    12,
                    TimeUnit.HOURS
            );
        } catch (Exception e) {
            log.warn("Failed to update last message cache for conversation: {}", conversationId, e);
        }
    }

    private void invalidateConversationCache(String conversationId) {
        try {
            redisService.delete(getConversationCacheKey(conversationId));
        } catch (Exception e) {
            log.warn("Failed to invalidate conversation cache for conversation: {}", conversationId, e);
        }
    }

    @Transactional
    public void deleteChatMessage(ChatMessageDeleteRequest request) {

        String userId = SecurityUtils.getCurrentUserId();

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
        var response = buildMessageResponse(chatMessage);
        response.setContent(MessageType.DELETED_FOR_EVERYONE.getDefaultContent());
        enrichReplyPreview(response, chatMessage);

        // 3. Save to Outbox for CDC (Sync to ES and notify other users)
        publishMessageEvent("chat.message.deleted", response);

        // 4. Update Conversation lastMessage if this was the last one
        Optional<ChatMessage> lastMessage = chatMessageRepository.findTopByConversationIdOrderByCreatedDateDesc(conversationId);
        if (lastMessage.isPresent() && lastMessage.get().getId().equals(chatMessage.getId())) {
            conversationRepository.updateLastMessage(
                    conversationId,
                    chatMessage.getMessageType().getDefaultContent());

            runAfterCommit(() -> {
                updateLastMessageCache(conversationId, chatMessage.getMessageType().getDefaultContent());
                invalidateConversationCache(conversationId);
            });

            // 5. Save to Outbox for Conversation sync to ES
            saveToOutbox(
                    chatMessage.getId(),
                    "chat.conversation.updated",
                    ConversationDoc.builder()
                            .id(conversationId)
                            .lastMessage(chatMessage.getMessageType().getDefaultContent())
                            .build()
            );
        }
    }

    @Transactional
    public ChatMessageResponse updateChatMessage(ChatMessageUpdateRequest request) {
        String userId = SecurityUtils.getCurrentUserId();

        var chatMessage = chatMessageRepository.findById(request.getChatMessageId())
                .orElseThrow(() -> new AppException(ErrorCode.MESSAGE_NOT_FOUND));

        if (!chatMessage.getSenderId().equals(userId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        if (chatMessage.getMessageType() != MessageType.TEXT)
            throw new AppException(ErrorCode.MESSAGE_TYPE_NOT_TEXT);

        // 1. Update content in MongoDB
        chatMessage.setContent(request.getContent());
        var saved = chatMessageRepository.save(chatMessage);
        var response = buildMessageResponse(saved);
        enrichReplyPreview(response, saved);

        // 2. Save to Outbox for CDC (Sync to ES and notify other users)
        publishMessageEvent("chat.message.updated", response);

        // 3. Update Conversation lastMessage and Cache if needed
        Optional<ChatMessage> lastMessage = chatMessageRepository.findTopByConversationIdOrderByCreatedDateDesc(chatMessage.getConversationId());
        if (lastMessage.isPresent() && lastMessage.get().getId().equals(chatMessage.getId())) {
            conversationRepository.updateLastMessage(chatMessage.getConversationId(), request.getContent());
            runAfterCommit(() -> {
                updateLastMessageCache(chatMessage.getConversationId(), request.getContent());
                invalidateConversationCache(chatMessage.getConversationId());
            });

            // 4. Save to Outbox for Conversation sync to ES
            saveToOutbox(
                    chatMessage.getId(),
                    "chat.conversation.updated",
                    ConversationDoc.builder()
                            .id(chatMessage.getConversationId())
                            .lastMessage(request.getContent())
                            .build()
            );
        }

        return response;
    }

    @Transactional
    public void seenAt(String conversationId) {

        String userId = SecurityUtils.getCurrentUserId();
        if (!conversationMemberRepository.existsByConversationIdAndUserId(conversationId, userId)) {
            throw new AppException(ErrorCode.CONVERSATION_MEMBER_NOT_FOUND);
        }

        // 1. check conversation + participant
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));

        // 2. lấy message mới nhất (VISIBLE message)
        Optional<ChatMessage> lastMessage = 
                chatMessageRepository.findTopByConversationIdOrderByCreatedDateDesc(conversationId);
        if (lastMessage.isEmpty())
            return;

        String lastMessageId = lastMessage.get().getId();

        // Update user seen in DB. Cache is synchronized only after commit to avoid rollback drift.
        ConversationMember currentMember = updateSeenState(conversationId, lastMessage.get(), userId);

        List<String> receiverIds = conversationMemberRepository.findByConversationIdAndUserIdNot(conversationId, userId)
                .stream()
                .map(ConversationMember::getUserId)
                .toList();

        if (!receiverIds.isEmpty()) {
            saveToOutbox(
                    currentMember.getId(),
                    "chat.conversation.seen",
                    ConversationSeenEvent.builder()
                            .eventId(UUID.randomUUID().toString())
                            .eventType("chat.conversation.seen")
                            .timestamp(Instant.now())
                            .conversationId(conversationId)
                            .userId(userId)
                            .lastSeenMessageId(lastMessageId)
                            .receiverIds(receiverIds)
                            .build()
            );
        }

        scheduleSeenCacheSync(conversationId, lastMessage.get(), userId);
    }

    public UnreadCountResponse getUnreadCount() {
        String userId = SecurityUtils.getCurrentUserId();
        String totalKey = getTotalUnreadCacheKey(userId);
        Long totalObj = redisService.get(totalKey, new TypeReference<Long>() {});

        List<ConversationMember> members = conversationMemberRepository.findByUserId(userId);
        Map<String, Long> data = new HashMap<>();
        long computedTotal = 0L;
        Map<String, Conversation> conversationsById = conversationRepository.findAllById(
                        members.stream()
                                .map(ConversationMember::getConversationId)
                                .collect(Collectors.toSet())
                ).stream()
                .collect(Collectors.toMap(Conversation::getId, conversation -> conversation));
        Map<String, Long> lastSeenSeqByConversation = new HashMap<>();

        for (ConversationMember member : members) {
            long lastSeenSeq = member.getLastSeenSeq() == null ? 0L : member.getLastSeenSeq();
            lastSeenSeqByConversation.merge(
                    member.getConversationId(),
                    lastSeenSeq,
                    Math::max
            );
        }

        for (Map.Entry<String, Long> entry : lastSeenSeqByConversation.entrySet()) {
            String conversationId = entry.getKey();
            String unreadKey = getUnreadCountCacheKey(userId, conversationId);

            Conversation conversation = conversationsById.get(conversationId);
            long lastSeenSeq = entry.getValue();
            long authoritativeUnread = conversation == null
                    ? 0L
                    : Math.max(0, conversation.getTotalSeq() - lastSeenSeq);

            Long cachedUnread = redisService.get(unreadKey, new TypeReference<Long>() {});
            if (!Objects.equals(cachedUnread, authoritativeUnread)) {
                redisService.setWithExpiration(unreadKey, authoritativeUnread, 12, TimeUnit.HOURS);
            }

            data.put(conversationId, authoritativeUnread);
            computedTotal += authoritativeUnread;
        }

        if (!Objects.equals(totalObj, computedTotal)) {
            redisService.setWithExpiration(totalKey, computedTotal, 12, TimeUnit.HOURS);
        }

        return UnreadCountResponse.builder()
                .data(data)
                .total(computedTotal)
                .build();
    }

    @CircuitBreaker(name = "chatSearchApi", fallbackMethod = "searchMessagesFallback")
    public PageResponse<ChatMessageResponse> searchMessages(String conversationId, String query, int page, int size) {
        String userId = SecurityUtils.getCurrentUserId();
        if (!conversationMemberRepository.existsByConversationIdAndUserId(conversationId, userId)) {
            throw new AppException(ErrorCode.USERID_NOT_FOUND);
        }

        Pageable pageable = PageRequest.of(page - 1, size, Sort.by("createdAt").descending());
        var searchResult = chatMessageElasticRepository.searchMessages(conversationId, query, pageable);

        // 1. Batch-fetch reply targets (search index doesn't carry the quoted preview, only the id)
        Set<String> replyIds = searchResult.getContent().stream()
                .map(ChatMessageDoc::getReplyToMessageId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());
        Map<String, ChatMessage> replyTargets = replyIds.isEmpty()
                ? Collections.emptyMap()
                : chatMessageRepository.findAllById(replyIds).stream()
                        .collect(Collectors.toMap(ChatMessage::getId, m -> m));

        // 2. Collect unique senderIds (message senders + reply target senders)
        Set<String> senderIds = searchResult.getContent().stream()
                .map(ChatMessageDoc::getSenderId)
                .collect(Collectors.toSet());
        replyTargets.values().forEach(target -> senderIds.add(target.getSenderId()));

        // 3. Enrich profiles (Bulk: Redis check -> Service fallback -> Warm cache)
        Map<String, UserProfileResponse> profileMap = enrichProfiles(senderIds);

        // 3b. Resolve attachment URLs for the whole page in parallel
        Map<String, String> attachmentUrls = chatFileUrlResolver.resolveBatch(
                searchResult.getContent().stream().map(ChatMessageDoc::getAttachmentFileId).toList());

        // 4. Map to response with profile info
        List<ChatMessageResponse> data = searchResult.getContent().stream()
                .map(doc -> {
                    var response = ChatMessageResponse.builder()
                            .id(doc.getId())
                            .conversationId(doc.getConversationId())
                            .senderId(doc.getSenderId())
                            .content(doc.getContent())
                            .messageType(doc.getMessageType() != null ? MessageType.valueOf(doc.getMessageType()) : null)
                            .messageStatus(doc.getMessageStatus() != null ? MessageStatus.valueOf(doc.getMessageStatus()) : null)
                            .attachmentFileUrl(attachmentUrls.get(doc.getAttachmentFileId()))
                            .replyToMessageId(doc.getReplyToMessageId())
                            .createdDate(doc.getCreatedAt())
                            .modifiedDate(doc.getModifiedAt())
                            .seq(doc.getSeq())
                            .clientMessageId(doc.getClientMessageId())
                            .me(Objects.equals(doc.getSenderId(), userId))
                            .build();
                    
                    // Fill profile info
                    UserProfileResponse profile = profileMap.get(doc.getSenderId());
                    if (profile != null) {
                        response.setSenderName(profile.getDisplayName());
                        response.setSenderAvatar(profile.getAvatar());
                    }

                    ChatMessage replyTarget = replyTargets.get(doc.getReplyToMessageId());
                    if (replyTarget != null) {
                        applyReplyPreview(response, replyTarget, profileMap);
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

    public PageResponse<ChatMessageResponse> searchMessagesFallback(
            String conversationId,
            String query,
            int page,
            int size,
            Throwable throwable
    ) {
        throw buildSearchFallbackException("search messages", throwable);
    }

    private void publishMessageEvent(String eventType, ChatMessageResponse response) {
        List<String> receiverIds = conversationMemberRepository
                .findByConversationIdAndUserIdNot(response.getConversationId(), response.getSenderId())
                .stream()
                .map(ConversationMember::getUserId)
                .toList();

       saveToOutbox(response.getId(), "chat.messages", MessageCreatedEvent.builder()
               .eventId(UUID.randomUUID().toString())
               .eventType(eventType)
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
            log.error("Failed to save outbox with topic: {}", topic, e);
            throw new AppException(ErrorCode.OUTBOX_SAVE_FAILED);
        }
    }

    private AppException buildSearchFallbackException(String action, Throwable throwable) {
        if (throwable instanceof AppException appException) {
            return appException;
        }
        if (throwable instanceof CallNotPermittedException) {
            log.warn("Circuit breaker is open while trying to {}.", action);
            return new AppException(ErrorCode.SERVICE_UNAVAILABLE);
        }
        log.error("Fallback triggered while trying to {}.", action, throwable);
        return new AppException(ErrorCode.SERVICE_UNAVAILABLE);
    }
}
