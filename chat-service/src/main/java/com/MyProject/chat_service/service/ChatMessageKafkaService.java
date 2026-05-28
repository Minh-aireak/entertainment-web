package com.MyProject.chat_service.service;

import com.MyProject.chat_service.document.ChatMessageDoc;
import com.MyProject.chat_service.document.ConversationDoc;
import com.MyProject.chat_service.dto.event.MessageCreatedEvent;
import com.MyProject.chat_service.dto.response.ChatMessageResponse;
import com.MyProject.chat_service.repository.elasticsearch.ChatMessageElasticRepository;
import com.MyProject.chat_service.repository.elasticsearch.ConversationElasticRepository;
import com.MyProject.chat_service.repository.mongo.ConversationMemberRepository;
import com.MyProject.common.redis.RedisService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ChatMessageKafkaService {
    ChatMessageElasticRepository chatMessageElasticRepository;
    ConversationService conversationService;
    ConversationElasticRepository conversationElasticRepository;
    ConversationMemberRepository conversationMemberRepository;
    ElasticsearchOperations elasticsearchOperations;
    RedisService redisService;
    ObjectMapper objectMapper;

    @KafkaListener(topics = "friend.request.accepted.conversation")
    public void handleFriendRequestAccepted(String message) {
        try {
            List<String> ids = objectMapper.readValue(message, new TypeReference<List<String>>() {});
            conversationService.createConversation(ids);
        } catch (Exception e) {
            log.error("Failed to process friend.request.accepted event", e);
        }
    }

    @KafkaListener(topics = {"chat.message.created", "chat.message.updated", "chat.message.deleted"})
    public void listenChatMessageSync(String payload) {
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
        } catch (Exception e) {
            log.error("Failed to process chat message sync event", e);
        }
    }

    private void handleMessageCreated(ChatMessageResponse response) {
        try {
            ChatMessageDoc doc = ChatMessageDoc.builder()
                    .id(response.getId())
                    .conversationId(response.getConversationId())
                    .senderId(response.getSenderId())
                    .content(response.getContent())
                    .createdAt(response.getCreatedDate())
                    .seq(response.getSeq())
                    .clientMessageId(response.getClientMessageId())
                    .build();
            chatMessageElasticRepository.save(doc);

            // 1. Update last message in ConversationDoc (Elasticsearch)
            conversationElasticRepository.findById(response.getConversationId())
                    .ifPresent(convDoc -> {
                        convDoc.setLastMessage(response.getContent());
                        conversationElasticRepository.save(convDoc);
                    });

            // 2. Increment unread count in Redis for other members
            String conversationId = response.getConversationId();
            String senderId = response.getSenderId();

            conversationMemberRepository.findByConversationId(conversationId).stream()
                    .filter(m -> !m.getUserId().equals(senderId))
                    .forEach(m -> {
                        String unreadKey = "chat:unread:" + m.getUserId() + ":" + conversationId;
                        String totalUnreadKey = "chat:unread:total:" + m.getUserId();
                        try {
                            redisService.increment(unreadKey);
                            redisService.increment(totalUnreadKey);
                        } catch (Exception e) {
                            log.warn("Failed to increment unread count in Redis for user {}", m.getUserId());
                        }
                    });
        } catch (Exception e) {
            log.error("Failed to sync created message to ES", e);
        }
    }

    private void handleMessageUpdated(ChatMessageResponse response) {
        try {
            Document document = Document.create();
            document.put("content", response.getContent());

            UpdateQuery updateQuery = UpdateQuery.builder(response.getId())
                    .withDocument(document)
                    .build();

            elasticsearchOperations.update(updateQuery, elasticsearchOperations.getIndexCoordinatesFor(ChatMessageDoc.class));
            log.info("Successfully updated message content in ES for ID: {}", response.getId());
        } catch (Exception e) {
            log.error("Failed to sync updated message to ES", e);
        }
    }

    private void handleMessageDeleted(ChatMessageResponse response) {
        try {
            Document document = Document.create();
            document.put("content", response.getMessageType().getDefaultContent());
            document.put("deleted", true);

            UpdateQuery updateQuery = UpdateQuery.builder(response.getId())
                    .withDocument(document)
                    .build();

            elasticsearchOperations.update(updateQuery, elasticsearchOperations.getIndexCoordinatesFor(ChatMessageDoc.class));
            log.info("Successfully marked message as deleted in ES for ID: {}", response.getId());
        } catch (Exception e) {
            log.error("Failed to sync deleted message to ES", e);
        }
    }

    @KafkaListener(topics = "chat.conversation.deleted")
    public void listenConversationDeleted(String payload) {
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
        } catch (Exception e) {
            log.error("Failed to sync conversation delete to ES", e);
        }
    }

    @KafkaListener(topics = "chat.conversation.updated")
    public void listenConversationUpdated(String payload) {
        log.info("Received conversation update sync: {}", payload);
        try {
            ConversationDoc update = objectMapper.readValue(payload, ConversationDoc.class);

            // Optimized Partial Update for Conversation
            Document document = Document.create();
            document.put("lastMessage", update.getLastMessage());

            UpdateQuery updateQuery = UpdateQuery.builder(update.getId())
                    .withDocument(document)
                    .build();

            elasticsearchOperations.update(updateQuery, elasticsearchOperations.getIndexCoordinatesFor(ConversationDoc.class));
            log.info("Successfully updated conversation last message in ES for ID: {}", update.getId());
        } catch (Exception e) {
            log.error("Failed to sync conversation update to ES", e);
        }
    }

    @KafkaListener(topics = "conversation.sync")
    public void listenConversationSync(String payload) {
        log.info("Received conversation sync: {}", payload);
        try {
            ConversationDoc doc = objectMapper.readValue(payload, ConversationDoc.class);
            conversationElasticRepository.save(doc);
        } catch (Exception e) {
            log.error("Failed to sync conversation to ES", e);
        }
    }
}
