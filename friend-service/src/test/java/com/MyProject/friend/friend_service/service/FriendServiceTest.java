package com.MyProject.friend.friend_service.service;

import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.common.dto.response.UserProfileResponse;
import com.MyProject.common.redis.RedisService;
import com.MyProject.friend.friend_service.dto.event.NotificationEvent;
import com.MyProject.friend.friend_service.entity.FriendRequest;
import com.MyProject.friend.friend_service.entity.FriendRequestStatus;
import com.MyProject.friend.friend_service.entity.RelationshipStatus;
import com.MyProject.friend.friend_service.entity.UserRelationship;
import com.MyProject.friend.friend_service.exception.AppException;
import com.MyProject.friend.friend_service.exception.ErrorCode;
import com.MyProject.friend.friend_service.repository.elasticsearch.FriendElasticRepository;
import com.MyProject.friend.friend_service.repository.mongo.FriendRequestRepository;
import com.MyProject.friend.friend_service.repository.mongo.UserRelationshipRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FriendServiceTest {
    @Mock
    FriendRequestRepository friendRequestRepository;

    @Mock
    FriendProfileExternalService friendProfileExternalService;

    @Mock
    UserRelationshipRepository userRelationshipRepository;

    @Mock
    FriendElasticRepository friendElasticRepository;

    @Mock
    RedisService redisService;

    @Mock
    OutboxEventPublisher outboxEventPublisher;

    @InjectMocks
    FriendService friendService;

    @BeforeEach
    void authenticate() {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .claim("userId", "current-user")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getFriendSuggestions_excludesFriendsAndPendingRequestsInBothDirections() {
        UserRelationship sentRelationship = UserRelationship.builder()
                .senderId("current-user")
                .receiverId("friend-one")
                .relationshipStatus(RelationshipStatus.FRIEND)
                .build();
        UserRelationship receivedRelationship = UserRelationship.builder()
                .senderId("friend-two")
                .receiverId("current-user")
                .relationshipStatus(RelationshipStatus.FRIEND)
                .build();
        FriendRequest sentRequest = FriendRequest.builder()
                .senderId("current-user")
                .receiverId("pending-one")
                .friendRequestStatus(FriendRequestStatus.PENDING)
                .build();
        FriendRequest receivedRequest = FriendRequest.builder()
                .senderId("pending-two")
                .receiverId("current-user")
                .friendRequestStatus(FriendRequestStatus.PENDING)
                .build();
        UserProfileResponse candidate = UserProfileResponse.builder()
                .userId("candidate")
                .displayName("Candidate")
                .build();
        PageResponse<UserProfileResponse> downstreamResponse = PageResponse.<UserProfileResponse>builder()
                .currentPage(0)
                .pageSize(20)
                .totalPages(1)
                .totalElement(1)
                .data(List.of(candidate))
                .build();
        Set<String> expectedExclusions = Set.of(
                "current-user",
                "friend-one",
                "friend-two",
                "pending-one",
                "pending-two"
        );

        when(userRelationshipRepository.findRelationshipsForSuggestions(
                "current-user", RelationshipStatus.FRIEND
        )).thenReturn(List.of(sentRelationship, receivedRelationship));
        when(friendRequestRepository.findRequestsForSuggestions(
                "current-user", FriendRequestStatus.PENDING
        )).thenReturn(List.of(sentRequest, receivedRequest));
        when(friendProfileExternalService.getSuggestionProfiles(expectedExclusions, 0, 20))
                .thenReturn(downstreamResponse);

        PageResponse<UserProfileResponse> result = friendService.getFriendSuggestions(1, 20);

        assertEquals(1, result.getCurrentPage());
        assertEquals(1, result.getTotalElement());
        assertEquals(List.of(candidate), result.getData());
        verify(friendProfileExternalService).getSuggestionProfiles(expectedExclusions, 0, 20);
    }

    @Test
    void sendFriendRequest_rejectsExistingFriendship() {
        when(userRelationshipRepository.existsByHashFriend("current-user_friend-one"))
                .thenReturn(true);

        AppException exception = assertThrows(
                AppException.class,
                () -> friendService.sendFriendRequest("friend-one")
        );

        assertEquals(ErrorCode.ALREADY_FRIEND, exception.getErrorCode());
        verify(friendRequestRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void countMyFriends_usesMongoCountQueryAndReturnsRepositoryCount() throws NoSuchMethodException {
        var repositoryMethod = UserRelationshipRepository.class.getMethod(
                "countMyFriends",
                String.class,
                RelationshipStatus.class
        );
        Query query = repositoryMethod.getAnnotation(Query.class);

        assertTrue(query.count());
        assertEquals(long.class, repositoryMethod.getReturnType());

        when(userRelationshipRepository.countMyFriends("current-user", RelationshipStatus.FRIEND))
                .thenReturn(2L);

        assertEquals(2, friendService.countMyFriends());
    }

    @Test
    void acceptFriendRequest_notifiesOnlyOriginalSenderWithAcceptorAsNotificationSender() {
        FriendRequest request = FriendRequest.builder()
                .senderId("requester")
                .receiverId("current-user")
                .hashFriendRequest("current-user_requester")
                .friendRequestStatus(FriendRequestStatus.PENDING)
                .build();
        UserRelationship savedRelationship = UserRelationship.builder()
                .id("relationship-id")
                .senderId("requester")
                .receiverId("current-user")
                .hashFriend("current-user_requester")
                .relationshipStatus(RelationshipStatus.FRIEND)
                .build();
        ArgumentCaptor<NotificationEvent> eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);

        when(friendRequestRepository.findByHashFriendRequest("current-user_requester"))
                .thenReturn(java.util.Optional.of(request));
        when(userRelationshipRepository.save(any(UserRelationship.class)))
                .thenReturn(savedRelationship);

        friendService.friendRequestStatus("requester", FriendRequestStatus.ACCEPTED);

        verify(outboxEventPublisher).publish(
                eq("relationship-id"),
                eq("notification.events"),
                eventCaptor.capture()
        );
        NotificationEvent event = eventCaptor.getValue();
        assertEquals("FRIEND_ACCEPTED", event.getTypeNotification());
        assertEquals("current-user", event.getUserIdSender());
        assertEquals(List.of("requester"), event.getToUserIds());
        verify(friendRequestRepository).deleteByHashFriendRequest("current-user_requester");
    }

    @Test
    void sendFriendRequest_toSelf_throwsCannotSendToSelf() {
        AppException exception = assertThrows(AppException.class,
                () -> friendService.sendFriendRequest("current-user"));

        assertEquals(ErrorCode.CANNOT_SEND_REQUEST_TO_SELF, exception.getErrorCode());
        verify(userRelationshipRepository, never()).existsByHashFriend(any());
    }

    @Test
    void sendFriendRequest_alreadyPending_throwsAlreadySendRequest() {
        when(userRelationshipRepository.existsByHashFriend("current-user_friend-one")).thenReturn(false);
        when(friendRequestRepository.findByHashFriendRequest("current-user_friend-one"))
                .thenReturn(java.util.Optional.of(FriendRequest.builder().build()));

        AppException exception = assertThrows(AppException.class,
                () -> friendService.sendFriendRequest("friend-one"));

        assertEquals(ErrorCode.ALREADY_SEND_REQUEST, exception.getErrorCode());
        verify(friendRequestRepository, never()).save(any());
    }

    @Test
    void sendFriendRequest_happyPath_savesRequestAndPublishesNotification() {
        when(userRelationshipRepository.existsByHashFriend("current-user_friend-one")).thenReturn(false);
        when(friendRequestRepository.findByHashFriendRequest("current-user_friend-one"))
                .thenReturn(java.util.Optional.empty());
        when(friendRequestRepository.save(any(FriendRequest.class))).thenAnswer(inv -> {
            FriendRequest req = inv.getArgument(0);
            req.setId("req-1");
            return req;
        });

        friendService.sendFriendRequest("friend-one");

        verify(friendRequestRepository).save(argThat(r ->
                r.getSenderId().equals("current-user") && r.getReceiverId().equals("friend-one")
                        && r.getFriendRequestStatus() == FriendRequestStatus.PENDING));
        verify(outboxEventPublisher).publish(eq("req-1"), eq("notification.events"), any());
    }

    @Test
    void friendRequestStatus_hashNotFound_throwsHashFriendRequest() {
        when(friendRequestRepository.findByHashFriendRequest("current-user_requester"))
                .thenReturn(java.util.Optional.empty());

        AppException exception = assertThrows(AppException.class,
                () -> friendService.friendRequestStatus("requester", FriendRequestStatus.ACCEPTED));

        assertEquals(ErrorCode.HASH_FRIEND_REQUEST, exception.getErrorCode());
    }

    @Test
    void friendRequestStatus_currentUserNotReceiver_throwsUnauthorized() {
        FriendRequest request = FriendRequest.builder()
                .senderId("current-user").receiverId("someone-else")
                .friendRequestStatus(FriendRequestStatus.PENDING).build();
        when(friendRequestRepository.findByHashFriendRequest("current-user_requester"))
                .thenReturn(java.util.Optional.of(request));

        AppException exception = assertThrows(AppException.class,
                () -> friendService.friendRequestStatus("requester", FriendRequestStatus.ACCEPTED));

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
    }

    @Test
    void friendRequestStatus_alreadyProcessed_throws() {
        FriendRequest request = FriendRequest.builder()
                .senderId("requester").receiverId("current-user")
                .friendRequestStatus(FriendRequestStatus.ACCEPTED).build();
        when(friendRequestRepository.findByHashFriendRequest("current-user_requester"))
                .thenReturn(java.util.Optional.of(request));

        AppException exception = assertThrows(AppException.class,
                () -> friendService.friendRequestStatus("requester", FriendRequestStatus.ACCEPTED));

        assertEquals(ErrorCode.FRIEND_REQUEST_ALREADY_PROCESSED, exception.getErrorCode());
    }

    @Test
    void friendRequestStatus_invalidStatus_throws() {
        FriendRequest request = FriendRequest.builder()
                .senderId("requester").receiverId("current-user")
                .hashFriendRequest("current-user_requester")
                .friendRequestStatus(FriendRequestStatus.PENDING).build();
        when(friendRequestRepository.findByHashFriendRequest("current-user_requester"))
                .thenReturn(java.util.Optional.of(request));

        AppException exception = assertThrows(AppException.class,
                () -> friendService.friendRequestStatus("requester", FriendRequestStatus.PENDING));

        assertEquals(ErrorCode.INVALID_STATUS, exception.getErrorCode());
        verify(userRelationshipRepository, never()).save(any());
    }

    @Test
    void friendRequestStatus_cancel_deletesWithoutCreatingRelationship() {
        FriendRequest request = FriendRequest.builder()
                .senderId("requester").receiverId("current-user")
                .hashFriendRequest("current-user_requester")
                .friendRequestStatus(FriendRequestStatus.PENDING).build();
        when(friendRequestRepository.findByHashFriendRequest("current-user_requester"))
                .thenReturn(java.util.Optional.of(request));

        friendService.friendRequestStatus("requester", FriendRequestStatus.CANCEL);

        verify(userRelationshipRepository, never()).save(any());
        verify(friendRequestRepository).deleteByHashFriendRequest("current-user_requester");
    }

    // Note: updateRelationshipStatus's "not found" guard checks Objects.isNull(current) on the
    // result of a Spring Data Optional-returning finder, which never returns null (only
    // Optional.empty()) - that branch is currently unreachable, so no test asserts ErrorCode.HASH_FRIEND
    // here. Flagged for the team; not fixed as part of writing tests for existing behavior.

    @Test
    void updateRelationshipStatus_unfriend_deletesRelationship() {
        UserRelationship relationship = UserRelationship.builder().hashFriend("current-user_friend-one").build();
        when(userRelationshipRepository.findByHashFriend("current-user_friend-one"))
                .thenReturn(java.util.Optional.of(relationship));

        friendService.updateRelationshipStatus("friend-one", RelationshipStatus.UNFRIEND);

        verify(userRelationshipRepository).deleteByHashFriend("current-user_friend-one");
        verify(userRelationshipRepository, never()).updateRelationshipStatus(any(), any());
    }

    @Test
    void updateRelationshipStatus_block_updatesStatusWithoutDeleting() {
        UserRelationship relationship = UserRelationship.builder().hashFriend("current-user_friend-one").build();
        when(userRelationshipRepository.findByHashFriend("current-user_friend-one"))
                .thenReturn(java.util.Optional.of(relationship));

        friendService.updateRelationshipStatus("friend-one", RelationshipStatus.BLOCKED_FROM_SENDER);

        verify(userRelationshipRepository).updateRelationshipStatus("current-user_friend-one", RelationshipStatus.BLOCKED_FROM_SENDER);
        verify(userRelationshipRepository, never()).deleteByHashFriend(any());
    }
}
