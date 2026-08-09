package com.MyProject.chat_service.service;

import com.MyProject.chat_service.dto.request.ChatMessageCreateRequest;
import com.MyProject.chat_service.dto.request.ChatMessageDeleteRequest;
import com.MyProject.chat_service.dto.request.ChatMessageUpdateRequest;
import com.MyProject.chat_service.dto.response.ChatMessageResponse;
import com.MyProject.chat_service.dto.response.UnreadCountResponse;
import com.MyProject.chat_service.entity.ChatMessage;
import com.MyProject.chat_service.entity.Conversation;
import com.MyProject.chat_service.entity.ConversationMember;
import com.MyProject.chat_service.enums.ErrorCode;
import com.MyProject.chat_service.enums.MessageType;
import com.MyProject.chat_service.exception.AppException;
import com.MyProject.chat_service.mapper.ChatMessageMapper;
import com.MyProject.chat_service.repository.elasticsearch.ChatMessageElasticRepository;
import com.MyProject.chat_service.repository.mongo.ChatMessageRepository;
import com.MyProject.chat_service.repository.mongo.ConversationMemberRepository;
import com.MyProject.chat_service.repository.mongo.ConversationRepository;
import com.MyProject.chat_service.repository.mongo.OutboxRepository;
import com.MyProject.common.redis.RedisService;
import com.MyProject.common.security.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatMessageServiceTest {

    private static final String USER_ID = "user-1";
    private static final String CONVERSATION_ID = "conv-1";

    @Mock ChatProfileExternalService chatProfileExternalService;
    @Mock ChatMessageMapper chatMessageMapper;
    @Mock ConversationRepository conversationRepository;
    @Mock ChatMessageRepository chatMessageRepository;
    @Mock ConversationMemberRepository conversationMemberRepository;
    @Mock ChatMessageElasticRepository chatMessageElasticRepository;
    @Mock RedisService redisService;
    @Mock OutboxRepository outboxRepository;
    @Mock ChatFileUrlResolver chatFileUrlResolver;

    ChatMessageService chatMessageService;
    MockedStatic<SecurityUtils> securityUtils;

    @BeforeEach
    void setUp() {
        chatMessageService = new ChatMessageService(chatProfileExternalService, chatMessageMapper,
                conversationRepository, chatMessageRepository, conversationMemberRepository,
                chatMessageElasticRepository, redisService, outboxRepository,
                new ObjectMapper().registerModule(new JavaTimeModule()), chatFileUrlResolver);

        securityUtils = mockStatic(SecurityUtils.class);
        securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);

        lenient().when(chatMessageMapper.toChatMessage(any(ChatMessageCreateRequest.class))).thenAnswer(inv -> {
            ChatMessageCreateRequest req = inv.getArgument(0);
            return ChatMessage.builder()
                    .conversationId(req.getConversationId())
                    .messageType(req.getMessageType())
                    .attachmentFileId(req.getAttachmentFileId())
                    .replyToMessageId(req.getReplyToMessageId())
                    .clientMessageId(req.getClientMessageId())
                    .build();
        });
        lenient().when(chatMessageMapper.toChatMessageResponse(any(ChatMessage.class))).thenAnswer(inv -> {
            ChatMessage m = inv.getArgument(0);
            return ChatMessageResponse.builder()
                    .id(m.getId())
                    .conversationId(m.getConversationId())
                    .senderId(m.getSenderId())
                    .content(m.getContent())
                    .messageType(m.getMessageType())
                    .seq(m.getSeq())
                    .build();
        });
        lenient().when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> {
            ChatMessage m = inv.getArgument(0);
            if (m.getId() == null) {
                m.setId("msg-" + System.nanoTime());
            }
            return m;
        });
    }

    @AfterEach
    void tearDown() {
        securityUtils.close();
    }

    private ChatMessageCreateRequest textRequest() {
        return ChatMessageCreateRequest.builder()
                .conversationId(CONVERSATION_ID)
                .messageType(MessageType.TEXT)
                .content("hello")
                .build();
    }

    // ---------- createChatMessage ----------

    @Test
    void createChatMessage_notMember_throwsUserIdNotFound() {
        when(conversationMemberRepository.existsByConversationIdAndUserId(CONVERSATION_ID, USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> chatMessageService.createChatMessage(textRequest()))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.USERID_NOT_FOUND);
        verifyNoInteractions(conversationRepository);
    }

    @Test
    void createChatMessage_duplicateClientMessageId_returnsExistingMessageWithoutReSaving() {
        when(conversationMemberRepository.existsByConversationIdAndUserId(CONVERSATION_ID, USER_ID)).thenReturn(true);
        ChatMessage existing = ChatMessage.builder().id("msg-existing").conversationId(CONVERSATION_ID)
                .senderId(USER_ID).content("hello").messageType(MessageType.TEXT).build();
        ChatMessageCreateRequest request = ChatMessageCreateRequest.builder()
                .conversationId(CONVERSATION_ID).messageType(MessageType.TEXT).content("hello")
                .clientMessageId("client-1").build();
        when(chatMessageRepository.findByClientMessageIdAndSenderIdAndConversationId("client-1", USER_ID, CONVERSATION_ID))
                .thenReturn(Optional.of(existing));

        ChatMessageResponse response = chatMessageService.createChatMessage(request);

        assertThat(response.getId()).isEqualTo("msg-existing");
        assertThat(response.isMe()).isTrue();
        verify(conversationRepository, never()).incrementSeqAndUpdateLastMessage(any(), any());
        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    void createChatMessage_replyTargetInDifferentConversation_throwsReplyTargetNotFound() {
        when(conversationMemberRepository.existsByConversationIdAndUserId(CONVERSATION_ID, USER_ID)).thenReturn(true);
        ChatMessage otherConvMessage = ChatMessage.builder().id("reply-1").conversationId("other-conv").build();
        when(chatMessageRepository.findById("reply-1")).thenReturn(Optional.of(otherConvMessage));
        ChatMessageCreateRequest request = ChatMessageCreateRequest.builder()
                .conversationId(CONVERSATION_ID).messageType(MessageType.TEXT).content("hello")
                .replyToMessageId("reply-1").build();

        assertThatThrownBy(() -> chatMessageService.createChatMessage(request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.REPLY_TARGET_NOT_FOUND);
    }

    @Test
    void createChatMessage_textTypeBlankContent_throwsContentRequired() {
        when(conversationMemberRepository.existsByConversationIdAndUserId(CONVERSATION_ID, USER_ID)).thenReturn(true);
        ChatMessageCreateRequest request = ChatMessageCreateRequest.builder()
                .conversationId(CONVERSATION_ID).messageType(MessageType.TEXT).content("   ").build();

        assertThatThrownBy(() -> chatMessageService.createChatMessage(request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.CONTENT_REQUIRED);
    }

    @Test
    void createChatMessage_nonTextTypeMissingAttachment_throwsFileRequired() {
        when(conversationMemberRepository.existsByConversationIdAndUserId(CONVERSATION_ID, USER_ID)).thenReturn(true);
        ChatMessageCreateRequest request = ChatMessageCreateRequest.builder()
                .conversationId(CONVERSATION_ID).messageType(MessageType.IMAGE).build();

        assertThatThrownBy(() -> chatMessageService.createChatMessage(request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.FILE_REQUIRED);
    }

    @Test
    void createChatMessage_conversationDisappearedMidRequest_throwsConversationNotFound() {
        when(conversationMemberRepository.existsByConversationIdAndUserId(CONVERSATION_ID, USER_ID)).thenReturn(true);
        when(conversationRepository.incrementSeqAndUpdateLastMessage(CONVERSATION_ID, "hello")).thenReturn(null);

        assertThatThrownBy(() -> chatMessageService.createChatMessage(textRequest()))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.CONVERSATION_NOT_FOUND);
        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    void createChatMessage_happyPath_savesMessageAndUpdatesSenderSeenState() {
        when(conversationMemberRepository.existsByConversationIdAndUserId(CONVERSATION_ID, USER_ID)).thenReturn(true);
        Conversation conversation = Conversation.builder().id(CONVERSATION_ID)
                .userIds(List.of(USER_ID, "user-2")).totalSeq(5L).build();
        when(conversationRepository.incrementSeqAndUpdateLastMessage(CONVERSATION_ID, "hello")).thenReturn(conversation);
        when(conversationMemberRepository.findByConversationIdAndUserId(CONVERSATION_ID, USER_ID))
                .thenReturn(Optional.of(ConversationMember.builder().id("member-1").conversationId(CONVERSATION_ID).userId(USER_ID).build()));

        ChatMessageResponse response = chatMessageService.createChatMessage(textRequest());

        assertThat(response.getContent()).isEqualTo("hello");
        assertThat(response.getSeq()).isEqualTo(5L);
        assertThat(response.isMe()).isTrue();
        verify(conversationMemberRepository).save(any(ConversationMember.class));
        verify(outboxRepository, atLeastOnce()).save(any());
    }

    @Test
    void createChatMessage_duplicateKeyOnSaveWithClientMessageId_returnsExistingInstead() {
        when(conversationMemberRepository.existsByConversationIdAndUserId(CONVERSATION_ID, USER_ID)).thenReturn(true);
        Conversation conversation = Conversation.builder().id(CONVERSATION_ID).userIds(List.of(USER_ID)).totalSeq(1L).build();
        when(conversationRepository.incrementSeqAndUpdateLastMessage(eq(CONVERSATION_ID), any())).thenReturn(conversation);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenThrow(new DuplicateKeyException("dup"));
        ChatMessage existing = ChatMessage.builder().id("msg-existing").conversationId(CONVERSATION_ID)
                .senderId(USER_ID).content("hello").build();
        when(chatMessageRepository.findByClientMessageIdAndSenderIdAndConversationId("client-1", USER_ID, CONVERSATION_ID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        ChatMessageCreateRequest request = ChatMessageCreateRequest.builder()
                .conversationId(CONVERSATION_ID).messageType(MessageType.TEXT).content("hello")
                .clientMessageId("client-1").build();

        ChatMessageResponse response = chatMessageService.createChatMessage(request);

        assertThat(response.getId()).isEqualTo("msg-existing");
    }

    @Test
    void createChatMessage_duplicateKeyWithoutClientMessageId_rethrowsException() {
        when(conversationMemberRepository.existsByConversationIdAndUserId(CONVERSATION_ID, USER_ID)).thenReturn(true);
        Conversation conversation = Conversation.builder().id(CONVERSATION_ID).userIds(List.of(USER_ID)).totalSeq(1L).build();
        when(conversationRepository.incrementSeqAndUpdateLastMessage(eq(CONVERSATION_ID), any())).thenReturn(conversation);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenThrow(new DuplicateKeyException("dup"));

        assertThatThrownBy(() -> chatMessageService.createChatMessage(textRequest()))
                .isInstanceOf(DuplicateKeyException.class);
    }

    // ---------- deleteChatMessage ----------

    @Test
    void deleteChatMessage_notFound_throwsMessageNotFound() {
        when(chatMessageRepository.findById("missing")).thenReturn(Optional.empty());
        ChatMessageDeleteRequest request = ChatMessageDeleteRequest.builder().chatMessageId("missing").build();

        assertThatThrownBy(() -> chatMessageService.deleteChatMessage(request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.MESSAGE_NOT_FOUND);
    }

    @Test
    void deleteChatMessage_notOwner_throwsUnauthorized() {
        ChatMessage message = ChatMessage.builder().id("msg-1").senderId("someone-else").conversationId(CONVERSATION_ID).build();
        when(chatMessageRepository.findById("msg-1")).thenReturn(Optional.of(message));
        ChatMessageDeleteRequest request = ChatMessageDeleteRequest.builder().chatMessageId("msg-1").build();

        assertThatThrownBy(() -> chatMessageService.deleteChatMessage(request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    void deleteChatMessage_happyPath_marksDeletedAndUpdatesConversationLastMessage() {
        ChatMessage message = ChatMessage.builder().id("msg-1").senderId(USER_ID).conversationId(CONVERSATION_ID)
                .messageType(MessageType.TEXT).content("hello").build();
        when(chatMessageRepository.findById("msg-1")).thenReturn(Optional.of(message));
        when(chatMessageRepository.findTopByConversationIdOrderByCreatedDateDesc(CONVERSATION_ID))
                .thenReturn(Optional.of(message));
        ChatMessageDeleteRequest request = ChatMessageDeleteRequest.builder().chatMessageId("msg-1").build();

        chatMessageService.deleteChatMessage(request);

        assertThat(message.getMessageType()).isEqualTo(MessageType.DELETED_FOR_EVERYONE);
        verify(chatMessageRepository).save(message);
        verify(conversationRepository).updateLastMessage(CONVERSATION_ID, MessageType.DELETED_FOR_EVERYONE.getDefaultContent());
    }

    @Test
    void deleteChatMessage_notLastMessage_doesNotUpdateConversation() {
        ChatMessage message = ChatMessage.builder().id("msg-1").senderId(USER_ID).conversationId(CONVERSATION_ID)
                .messageType(MessageType.TEXT).content("hello").build();
        ChatMessage newerMessage = ChatMessage.builder().id("msg-2").senderId(USER_ID).conversationId(CONVERSATION_ID).build();
        when(chatMessageRepository.findById("msg-1")).thenReturn(Optional.of(message));
        when(chatMessageRepository.findTopByConversationIdOrderByCreatedDateDesc(CONVERSATION_ID))
                .thenReturn(Optional.of(newerMessage));
        ChatMessageDeleteRequest request = ChatMessageDeleteRequest.builder().chatMessageId("msg-1").build();

        chatMessageService.deleteChatMessage(request);

        verify(conversationRepository, never()).updateLastMessage(any(), any());
    }

    // ---------- updateChatMessage ----------

    @Test
    void updateChatMessage_notOwner_throwsUnauthorized() {
        ChatMessage message = ChatMessage.builder().id("msg-1").senderId("someone-else").messageType(MessageType.TEXT).build();
        when(chatMessageRepository.findById("msg-1")).thenReturn(Optional.of(message));
        ChatMessageUpdateRequest request = ChatMessageUpdateRequest.builder().chatMessageId("msg-1").content("edited").build();

        assertThatThrownBy(() -> chatMessageService.updateChatMessage(request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void updateChatMessage_nonTextType_throwsMessageTypeNotText() {
        ChatMessage message = ChatMessage.builder().id("msg-1").senderId(USER_ID).messageType(MessageType.IMAGE).build();
        when(chatMessageRepository.findById("msg-1")).thenReturn(Optional.of(message));
        ChatMessageUpdateRequest request = ChatMessageUpdateRequest.builder().chatMessageId("msg-1").content("edited").build();

        assertThatThrownBy(() -> chatMessageService.updateChatMessage(request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.MESSAGE_TYPE_NOT_TEXT);
    }

    @Test
    void updateChatMessage_happyPath_updatesContentAndConversationLastMessage() {
        ChatMessage message = ChatMessage.builder().id("msg-1").senderId(USER_ID).conversationId(CONVERSATION_ID)
                .messageType(MessageType.TEXT).content("old").build();
        when(chatMessageRepository.findById("msg-1")).thenReturn(Optional.of(message));
        when(chatMessageRepository.findTopByConversationIdOrderByCreatedDateDesc(CONVERSATION_ID))
                .thenReturn(Optional.of(message));
        ChatMessageUpdateRequest request = ChatMessageUpdateRequest.builder().chatMessageId("msg-1").content("edited").build();

        ChatMessageResponse response = chatMessageService.updateChatMessage(request);

        assertThat(message.getContent()).isEqualTo("edited");
        assertThat(response.getContent()).isEqualTo("edited");
        verify(conversationRepository).updateLastMessage(CONVERSATION_ID, "edited");
    }

    // ---------- seenAt ----------

    @Test
    void seenAt_notMember_throwsConversationMemberNotFound() {
        when(conversationMemberRepository.existsByConversationIdAndUserId(CONVERSATION_ID, USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> chatMessageService.seenAt(CONVERSATION_ID))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.CONVERSATION_MEMBER_NOT_FOUND);
    }

    @Test
    void seenAt_noMessagesYet_returnsEarlyWithoutTouchingMemberState() {
        when(conversationMemberRepository.existsByConversationIdAndUserId(CONVERSATION_ID, USER_ID)).thenReturn(true);
        when(conversationRepository.findById(CONVERSATION_ID))
                .thenReturn(Optional.of(Conversation.builder().id(CONVERSATION_ID).build()));
        when(chatMessageRepository.findTopByConversationIdOrderByCreatedDateDesc(CONVERSATION_ID))
                .thenReturn(Optional.empty());

        chatMessageService.seenAt(CONVERSATION_ID);

        verify(conversationMemberRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void seenAt_conversationNotFound_throws() {
        when(conversationMemberRepository.existsByConversationIdAndUserId(CONVERSATION_ID, USER_ID)).thenReturn(true);
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatMessageService.seenAt(CONVERSATION_ID))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.CONVERSATION_NOT_FOUND);
    }

    // ---------- getUnreadCount ----------

    @Test
    void getUnreadCount_noConversationsForUser_returnsZeroTotal() {
        when(redisService.get(eq("chat:unread:total:" + USER_ID), any())).thenReturn(null);
        when(conversationMemberRepository.findByUserId(USER_ID)).thenReturn(List.of());

        UnreadCountResponse response = chatMessageService.getUnreadCount();

        assertThat(response.getTotal()).isZero();
        assertThat(response.getData()).isEmpty();
    }

    @Test
    void getUnreadCount_usesAuthoritativeSeqDiffNotStaleRedisValue() {
        when(redisService.get(eq("chat:unread:total:" + USER_ID), any())).thenReturn(999L);
        ConversationMember member = ConversationMember.builder()
                .conversationId(CONVERSATION_ID).userId(USER_ID).lastSeenSeq(3L).build();
        when(conversationMemberRepository.findByUserId(USER_ID)).thenReturn(List.of(member));
        Conversation conversation = Conversation.builder().id(CONVERSATION_ID).totalSeq(10L).build();
        when(conversationRepository.findAllById(anyCollection())).thenReturn(List.of(conversation));

        UnreadCountResponse response = chatMessageService.getUnreadCount();

        assertThat(response.getData()).containsEntry(CONVERSATION_ID, 7L);
        assertThat(response.getTotal()).isEqualTo(7L);
        verify(redisService).setWithExpiration(eq("chat:unread:total:" + USER_ID), eq(7L), eq(12L), any());
    }
}
