package com.MyProject.chat_service.service;

import com.MyProject.chat_service.document.ChatMessageDoc;
import com.MyProject.chat_service.document.ConversationDoc;
import com.MyProject.chat_service.dto.event.MessageCreatedEvent;
import com.MyProject.chat_service.dto.event.ProfileSearchUpdatedEvent;
import com.MyProject.chat_service.dto.response.ChatMessageResponse;
import com.MyProject.chat_service.entity.Conversation;
import com.MyProject.chat_service.entity.ConversationMember;
import com.MyProject.chat_service.repository.elasticsearch.ChatMessageElasticRepository;
import com.MyProject.chat_service.repository.elasticsearch.ConversationElasticRepository;
import com.MyProject.chat_service.repository.mongo.ConversationMemberRepository;
import com.MyProject.chat_service.repository.mongo.ConversationRepository;
import com.MyProject.common.redis.RedisService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ChatMessageKafkaService {
    ChatMessageElasticRepository chatMessageElasticRepository;
    ConversationService conversationService;
    ConversationElasticRepository conversationElasticRepository;
    ConversationMemberRepository conversationMemberRepository;
    ConversationRepository conversationRepository;
    ElasticsearchOperations elasticsearchOperations;
    RedisService redisService;
    ObjectMapper objectMapper;

    private static final String SEARCH_SYNC_IDEMPOTENCY_PREFIX = "chat:search-sync:processed:";
    private static final long SEARCH_SYNC_TTL_DAYS = 7;

    private RuntimeException kafkaProcessingException(String action, Exception exception) {
        log.error("Failed to {}", action, exception);
        return new IllegalStateException(action, exception);
    }

    @KafkaListener(topics = "friend.request.accepted.conversation")
    public void handleFriendRequestAccepted(String message, Acknowledgment acknowledgment) {
        try {
            List<String> ids = objectMapper.readValue(message, new TypeReference<List<String>>() {});
            conversationService.createConversationFromEvent(ids);
            acknowledgment.acknowledge();
        } catch (Exception e) {
            throw kafkaProcessingException("process friend.request.accepted event", e);
        }
    }

    @KafkaListener(topics = "chat.messages")
    public void listenChatMessageSync(String payload, Acknowledgment acknowledgment) {
        log.info("Received chat message sync event: {}", payload);
        try {
            MessageCreatedEvent event = objectMapper.readValue(payload, MessageCreatedEvent.class);
            ChatMessageResponse response = event.getMessage();
            String eventType = event.getEventType();

            if ("chat.message.created".equals(eventType)) {
                handleMessageCreated(response);
            } else if ("chat.message.updated".equals(eventType)) {
                handleMessageUpdated(response);
            } else if ("chat.message.deleted".equals(eventType)) {
                handleMessageDeleted(response);
            }

            acknowledgment.acknowledge();
        } catch (Exception e) {
            throw kafkaProcessingException("process chat message sync event", e);
        }
    }

    private void handleMessageCreated(ChatMessageResponse response) {
        ChatMessageDoc doc = ChatMessageDoc.builder()
                .id(response.getId())
                .conversationId(response.getConversationId())
                .senderId(response.getSenderId())
                .content(response.getContent())
                .messageType(response.getMessageType() != null ? response.getMessageType().name() : null)
                .messageStatus(response.getMessageStatus() != null ? response.getMessageStatus().name() : null)
                .attachmentFileUrl(response.getAttachmentFileUrl())
                .replyToMessageId(response.getReplyToMessageId())
                .createdAt(response.getCreatedDate())
                .modifiedAt(response.getModifiedDate())
                .seq(response.getSeq())
                .clientMessageId(response.getClientMessageId())
                .build();
        chatMessageElasticRepository.save(doc);

        conversationElasticRepository.findById(response.getConversationId())
                .ifPresent(convDoc -> {
                    convDoc.setLastMessage(response.getContent());
                    conversationElasticRepository.save(convDoc);
                });

        String conversationId = response.getConversationId();
        String senderId = response.getSenderId();

        conversationMemberRepository.findByConversationId(conversationId).stream()
                .filter(m -> !m.getUserId().equals(senderId))
                .forEach(m -> {
                    if (m.getLastSeenSeq() != null && m.getLastSeenSeq() >= response.getSeq()) {
                        return;
                    }
                    incrementUnreadCountForUser(m.getUserId(), conversationId, response.getSeq());
                });
    }

    private void incrementUnreadCountForUser(String userId, String conversationId, long messageSeq) {
        try {
            String lastProcessedSeqKey = "chat:unread:last_seq:" + userId + ":" + conversationId;
            String lastProcessedSeqStr = redisService.getAsString(lastProcessedSeqKey);
            long lastProcessedSeq = lastProcessedSeqStr != null ? Long.parseLong(lastProcessedSeqStr) : 0L;

            // Only increment if this message seq is newer than the last processed one (idempotent)
            if (messageSeq > lastProcessedSeq) {
                String unreadKey = "chat:unread:" + userId + ":" + conversationId;
                redisService.increment(unreadKey);
                redisService.expire(unreadKey, 12, TimeUnit.HOURS);

                String totalUnreadKey = "chat:unread:total:" + userId;
                redisService.increment(totalUnreadKey);
                redisService.expire(totalUnreadKey, 12, TimeUnit.HOURS);

                // Update the last processed seq
                redisService.setWithExpiration(lastProcessedSeqKey, String.valueOf(messageSeq), 12, TimeUnit.HOURS);
            }
        } catch (Exception e) {
            log.warn("Failed to increment unread count for user {} in conversation {}, will recalculate later", userId, conversationId, e);
        }
    }

    private void handleMessageUpdated(ChatMessageResponse response) {
        Document document = Document.create();
        document.put("content", response.getContent());
        document.put("modifiedAt", response.getModifiedDate());

        UpdateQuery updateQuery = UpdateQuery.builder(response.getId())
                .withDocument(document)
                .build();

        elasticsearchOperations.update(updateQuery, elasticsearchOperations.getIndexCoordinatesFor(ChatMessageDoc.class));
        log.info("Successfully updated message content in ES for ID: {}", response.getId());
    }

    private void handleMessageDeleted(ChatMessageResponse response) {
        Document document = Document.create();
        document.put("content", response.getMessageType().getDefaultContent());
        document.put("messageType", response.getMessageType() != null ? response.getMessageType().name() : null);
        document.put("modifiedAt", response.getModifiedDate());
        document.put("deleted", true);

        UpdateQuery updateQuery = UpdateQuery.builder(response.getId())
                .withDocument(document)
                .build();

        elasticsearchOperations.update(updateQuery, elasticsearchOperations.getIndexCoordinatesFor(ChatMessageDoc.class));
        log.info("Successfully marked message as deleted in ES for ID: {}", response.getId());
    }

    @KafkaListener(topics = "chat.conversation.deleted")
    public void listenConversationDeleted(String payload, Acknowledgment acknowledgment) {
        log.info("Received conversation delete sync: {}", payload);
        try {
            ConversationDoc update = objectMapper.readValue(payload, ConversationDoc.class);

            Document document = Document.create();
            document.put("lastMessage", update.getLastMessage());
            document.put("deleted", true);

            UpdateQuery updateQuery = UpdateQuery.builder(update.getId())
                    .withDocument(document)
                    .build();

            elasticsearchOperations.update(updateQuery, elasticsearchOperations.getIndexCoordinatesFor(ConversationDoc.class));
            log.info("Successfully deleted conversation last message in ES for ID: {}", update.getId());
            acknowledgment.acknowledge();
        } catch (Exception e) {
            throw kafkaProcessingException("sync conversation delete to ES", e);
        }
    }

    @KafkaListener(topics = "chat.conversation.updated")
    public void listenConversationUpdated(String payload, Acknowledgment acknowledgment) {
        log.info("Received conversation update sync: {}", payload);
        try {
            ConversationDoc update = objectMapper.readValue(payload, ConversationDoc.class);

            Document document = Document.create();
            document.put("lastMessage", update.getLastMessage());

            UpdateQuery updateQuery = UpdateQuery.builder(update.getId())
                    .withDocument(document)
                    .build();

            elasticsearchOperations.update(updateQuery, elasticsearchOperations.getIndexCoordinatesFor(ConversationDoc.class));
            log.info("Successfully updated conversation last message in ES for ID: {}", update.getId());
            acknowledgment.acknowledge();
        } catch (Exception e) {
            throw kafkaProcessingException("sync conversation update to ES", e);
        }
    }

    @KafkaListener(topics = "conversation.sync")
    public void listenConversationSync(String payload, Acknowledgment acknowledgment) {
        log.info("Received conversation sync: {}", payload);
        try {
            ConversationDoc doc = objectMapper.readValue(payload, ConversationDoc.class);
            conversationElasticRepository.save(doc);
            acknowledgment.acknowledge();
        } catch (Exception e) {
            throw kafkaProcessingException("sync conversation to ES", e);
        }
    }

    @KafkaListener(topics = "search.sync")
    public void listenSearchSync(String payload, Acknowledgment acknowledgment) {
        log.info("Received profile search sync for conversation refresh");
        try {
            ProfileSearchUpdatedEvent event = objectMapper.readValue(payload, ProfileSearchUpdatedEvent.class);
            String eventId = event.getEventId();
            
            // Idempotency check using eventId
            String idempotencyKey = SEARCH_SYNC_IDEMPOTENCY_PREFIX + eventId;
            if (redisService.getAsString(idempotencyKey) != null) {
                log.info("Search sync event already processed: {}", eventId);
                acknowledgment.acknowledge();
                return;
            }
            
            conversationService.syncConversationDocumentsForUser(event.getUserId());
            
            // Mark as processed
            redisService.setWithExpiration(idempotencyKey, "true", SEARCH_SYNC_TTL_DAYS, TimeUnit.DAYS);
            
            acknowledgment.acknowledge();
        } catch (Exception e) {
            throw kafkaProcessingException("refresh conversation documents after profile search sync", e);
        }
    }
}
