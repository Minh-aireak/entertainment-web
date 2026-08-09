package com.MyProject.chat_service.service;

import com.MyProject.chat_service.dto.request.ConversationUpdateRequest;
import com.MyProject.chat_service.dto.response.ConversationResponse;
import com.MyProject.chat_service.entity.Conversation;
import com.MyProject.chat_service.entity.ConversationDirect;
import com.MyProject.chat_service.entity.ConversationGroup;
import com.MyProject.chat_service.entity.ConversationMember;
import com.MyProject.chat_service.enums.ErrorCode;
import com.MyProject.chat_service.exception.AppException;
import com.MyProject.chat_service.mapper.ConversationMapper;
import com.MyProject.chat_service.repository.elasticsearch.ConversationElasticRepository;
import com.MyProject.chat_service.repository.mongo.ConversationDirectRepository;
import com.MyProject.chat_service.repository.mongo.ConversationGroupRepository;
import com.MyProject.chat_service.repository.mongo.ConversationMemberRepository;
import com.MyProject.chat_service.repository.mongo.ConversationRepository;
import com.MyProject.chat_service.repository.mongo.OutboxRepository;
import com.MyProject.common.dto.response.UserProfileResponse;
import com.MyProject.common.redis.RedisService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock ChatProfileExternalService chatProfileExternalService;
    @Mock ConversationMapper conversationMapper;
    @Mock ConversationRepository conversationRepository;
    @Mock ConversationDirectRepository conversationDirectRepository;
    @Mock ConversationGroupRepository conversationGroupRepository;
    @Mock ConversationMemberRepository conversationMemberRepository;
    @Mock ConversationElasticRepository conversationElasticRepository;
    @Mock RedisService redisService;
    @Mock OutboxRepository outboxRepository;
    @Mock ChatFileUrlResolver chatFileUrlResolver;

    ConversationService conversationService;

    @BeforeEach
    void setUp() {
        conversationService = new ConversationService(chatProfileExternalService, conversationMapper,
                conversationRepository, conversationDirectRepository, conversationGroupRepository,
                conversationMemberRepository, conversationElasticRepository, redisService, outboxRepository,
                new ObjectMapper().registerModule(new JavaTimeModule()), chatFileUrlResolver);

        lenient().when(conversationMapper.toConversationDirectResponse(any(ConversationDirect.class)))
                .thenAnswer(inv -> {
                    ConversationDirect d = inv.getArgument(0);
                    return ConversationResponse.builder().id(d.getId()).type("DIRECT").userIds(d.getUserIds()).build();
                });
        lenient().when(conversationMapper.toConversationGroupResponse(any(ConversationGroup.class)))
                .thenAnswer(inv -> {
                    ConversationGroup g = inv.getArgument(0);
                    return ConversationResponse.builder().id(g.getId()).type("GROUP").userIds(g.getUserIds()).build();
                });
        lenient().when(conversationDirectRepository.save(any(ConversationDirect.class))).thenAnswer(inv -> {
            ConversationDirect d = inv.getArgument(0);
            return d.getId() == null ? d.toBuilder().id("direct-" + System.nanoTime()).build() : d;
        });
        lenient().when(conversationGroupRepository.save(any(ConversationGroup.class))).thenAnswer(inv -> {
            ConversationGroup g = inv.getArgument(0);
            return g.getId() == null ? (ConversationGroup) g.toBuilder().id("group-" + System.nanoTime()).build() : g;
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setAuthenticatedUser(String userId) {
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "HS512"), Map.of("userId", userId));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private void stubProfiles(String... userIds) {
        Map<String, UserProfileResponse> profiles = new java.util.HashMap<>();
        for (String id : userIds) {
            profiles.put(id, UserProfileResponse.builder().userId(id).displayName("User-" + id).build());
        }
        lenient().when(chatProfileExternalService.getBulkUserProfilesForApi(anySet())).thenReturn(profiles);
        lenient().when(chatProfileExternalService.getBulkUserProfilesForInternal(anySet())).thenReturn(profiles);
    }

    // ---------- createConversationForApi ----------

    @Test
    void createConversationForApi_nullIds_throwsInvalidParticipants() {
        setAuthenticatedUser("user-1");

        assertThatThrownBy(() -> conversationService.createConversationForApi(null))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CONVERSATION_PARTICIPANTS);
    }

    @Test
    void createConversationForApi_fewerThanTwoDistinctIds_throwsInvalidParticipants() {
        setAuthenticatedUser("user-1");

        assertThatThrownBy(() -> conversationService.createConversationForApi(List.of("user-1", "user-1")))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CONVERSATION_PARTICIPANTS);
    }

    @Test
    void createConversationForApi_currentUserNotAmongParticipants_throwsUnauthorized() {
        setAuthenticatedUser("user-1");

        assertThatThrownBy(() -> conversationService.createConversationForApi(List.of("user-2", "user-3")))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void createConversationForApi_direct_existingConversationReused_doesNotCreateNew() {
        setAuthenticatedUser("user-1");
        stubProfiles("user-1", "user-2");
        ConversationDirect existing = ConversationDirect.builder()
                .id("direct-existing").userIds(List.of("user-1", "user-2")).participantsHash("user-1_user-2").build();
        when(conversationDirectRepository.findByParticipantsHash("user-1_user-2")).thenReturn(Optional.of(existing));

        ConversationResponse response = conversationService.createConversationForApi(List.of("user-2", "user-1"));

        assertThat(response.getId()).isEqualTo("direct-existing");
        verify(conversationDirectRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void createConversationForApi_direct_newConversation_savesAndCreatesMembers() {
        setAuthenticatedUser("user-1");
        stubProfiles("user-1", "user-2");
        when(conversationDirectRepository.findByParticipantsHash("user-1_user-2")).thenReturn(Optional.empty());

        ConversationResponse response = conversationService.createConversationForApi(List.of("user-1", "user-2"));

        assertThat(response.getId()).isNotBlank();
        verify(conversationDirectRepository).save(any(ConversationDirect.class));
        verify(outboxRepository).save(any());
        verify(conversationMemberRepository, times(2)).save(any(ConversationMember.class));
    }

    @Test
    void createConversationForApi_threeParticipants_createsGroupWithGeneratedName() {
        setAuthenticatedUser("user-1");
        stubProfiles("user-1", "user-2", "user-3");

        ConversationResponse response = conversationService.createConversationForApi(
                List.of("user-1", "user-2", "user-3"));

        assertThat(response.getType()).isEqualTo("GROUP");
        ArgumentCaptor<ConversationGroup> captor = ArgumentCaptor.forClass(ConversationGroup.class);
        verify(conversationGroupRepository).save(captor.capture());
        assertThat(captor.getValue().getGroupOwner()).isEqualTo("user-1");
        assertThat(captor.getValue().getGroupName()).contains("User-user-1", "User-user-2", "User-user-3");
    }

    @Test
    void createConversationFromEvent_someProfilesUnresolvable_throwsInvalidParticipants() {
        Map<String, UserProfileResponse> partial = Map.of("user-1",
                UserProfileResponse.builder().userId("user-1").build());
        when(chatProfileExternalService.getBulkUserProfilesForInternal(anySet())).thenReturn(partial);

        assertThatThrownBy(() -> conversationService.createConversationFromEvent(List.of("user-1", "user-2")))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CONVERSATION_PARTICIPANTS);
    }

    // ---------- updateGroupConversation ----------

    @Test
    void updateGroupConversation_conversationNotFound_throws() {
        setAuthenticatedUser("user-1");
        when(conversationRepository.findById("conv-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> conversationService.updateGroupConversation("conv-1",
                ConversationUpdateRequest.builder().conversationName("New name").build()))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.CONVERSATION_NOT_FOUND);
    }

    @Test
    void updateGroupConversation_directConversation_throwsTypeConversationError() {
        setAuthenticatedUser("user-1");
        ConversationDirect direct = ConversationDirect.builder().id("conv-1").userIds(List.of("user-1", "user-2")).build();
        when(conversationRepository.findById("conv-1")).thenReturn(Optional.of(direct));

        assertThatThrownBy(() -> conversationService.updateGroupConversation("conv-1",
                ConversationUpdateRequest.builder().conversationName("New name").build()))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.TYPE_CONVERSATION_ERROR);
    }

    @Test
    void updateGroupConversation_userNotMember_throwsUnauthorized() {
        setAuthenticatedUser("user-1");
        ConversationGroup group = ConversationGroup.builder().id("conv-1").userIds(List.of("user-2", "user-3"))
                .groupName("Old name").build();
        when(conversationRepository.findById("conv-1")).thenReturn(Optional.of(group));

        assertThatThrownBy(() -> conversationService.updateGroupConversation("conv-1",
                ConversationUpdateRequest.builder().conversationName("New name").build()))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void updateGroupConversation_happyPath_updatesNameAndAvatar() {
        setAuthenticatedUser("user-1");
        ConversationGroup group = ConversationGroup.builder().id("conv-1")
                .type(com.MyProject.chat_service.enums.ConversationType.GROUP)
                .userIds(List.of("user-1", "user-2")).groupName("Old name").build();
        when(conversationRepository.findById("conv-1")).thenReturn(Optional.of(group));
        stubProfiles("user-1", "user-2");
        when(chatFileUrlResolver.resolve("new-avatar-id")).thenReturn("https://cdn/new-avatar-id");

        ConversationResponse response = conversationService.updateGroupConversation("conv-1",
                ConversationUpdateRequest.builder().conversationName("  New name  ").groupAvatarFileId("new-avatar-id").build());

        assertThat(group.getGroupName()).isEqualTo("New name");
        assertThat(group.getGroupAvatarFileId()).isEqualTo("new-avatar-id");
        assertThat(response.getConversationAvatar()).isEqualTo("https://cdn/new-avatar-id");
        verify(outboxRepository).save(any());
    }

    @Test
    void updateGroupConversation_blankFieldsInRequest_leavesExistingValuesUnchanged() {
        setAuthenticatedUser("user-1");
        ConversationGroup group = ConversationGroup.builder().id("conv-1")
                .type(com.MyProject.chat_service.enums.ConversationType.GROUP)
                .userIds(List.of("user-1")).groupName("Old name").groupAvatarFileId("old-avatar").build();
        when(conversationRepository.findById("conv-1")).thenReturn(Optional.of(group));
        stubProfiles("user-1");

        conversationService.updateGroupConversation("conv-1",
                ConversationUpdateRequest.builder().conversationName("   ").groupAvatarFileId(null).build());

        assertThat(group.getGroupName()).isEqualTo("Old name");
        assertThat(group.getGroupAvatarFileId()).isEqualTo("old-avatar");
    }
}
