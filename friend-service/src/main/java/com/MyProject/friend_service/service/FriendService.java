package com.MyProject.friend_service.service;

import com.MyProject.common_dto.event.dto.FriendRequestEvent;
import com.MyProject.common_dto.event.dto.UserProfileResponse;
import com.MyProject.friend_service.dto.request.BulkUserProfileRequest;
import com.MyProject.friend_service.dto.request.UpdateFriendRequestStatus;
import com.MyProject.friend_service.dto.request.UpdateRelationshipStatus;
import com.MyProject.friend_service.dto.response.FriendResponse;
import com.MyProject.friend_service.dto.response.PageResponse;
import com.MyProject.friend_service.entity.FriendRequest;
import com.MyProject.friend_service.entity.FriendRequestStatus;
import com.MyProject.friend_service.entity.RelationshipStatus;
import com.MyProject.friend_service.entity.UserRelationship;
import com.MyProject.friend_service.exception.AppException;
import com.MyProject.friend_service.exception.ErrorCode;
import com.MyProject.friend_service.repository.FriendRequestRepository;
import com.MyProject.friend_service.repository.UserRelationshipRepository;
import com.MyProject.friend_service.repository.httpclient.ProfileClient;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FriendService {
    FriendRequestRepository friendRequestRepository;
    ProfileClient profileClient;
    UserRelationshipRepository userRelationshipRepository;
    KafkaTemplate<String, Object> kafkaTemplate;

    private String getUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Jwt jwt = ((JwtAuthenticationToken) authentication).getToken();
        return jwt.getClaim("userId");
    }

    private String generateHash(List<String> ids) {
        StringJoiner stringJoiner  = new StringJoiner("_");
        ids.forEach(stringJoiner::add);
        return stringJoiner.toString();
    }

    @Transactional
    public void sendFriend(String toUserId) {
        String fromUserId = getUserId();
        List<String> listSorted = Stream.of(fromUserId, toUserId).sorted().toList();
        String hashFriendRequest = generateHash(listSorted);

        FriendRequest existingRequest = friendRequestRepository.findByHashFriendRequest(hashFriendRequest)
                .orElse(null);

        if (existingRequest != null)
            throw new AppException(ErrorCode.ALREADY_SEND_REQUEST);

        friendRequestRepository.save(FriendRequest.builder()
                .userId(fromUserId)
                .toUserId(toUserId)
                .hashFriendRequest(hashFriendRequest)
                .friendRequestStatus(FriendRequestStatus.PENDING)
                .createdAt(Instant.now())
                .build());

        FriendRequestEvent event = FriendRequestEvent.builder()
                .fromUserId(fromUserId)
                .toUserId(toUserId)
                .hashFriendRequest(hashFriendRequest)
                .createdAt(Instant.now())
                .build();

        kafkaTemplate.send("friend-request", event);
    }

    @Transactional
    public void updateFriendRequestStatus(UpdateFriendRequestStatus request) {
        String hash = generateHash(Stream.of(getUserId(), request.getUserId()).sorted().toList());
        var current = friendRequestRepository.findByHashFriendRequest(hash);

        if (Objects.isNull(current))
            throw new AppException(ErrorCode.HASH_FRIEND_REQUEST);

        if (request.getFriendRequestStatus().equals(FriendRequestStatus.ACCEPTED)) {
            userRelationshipRepository.save(UserRelationship.builder()
                    .userId(request.getUserId())
                    .relatedUserId(getUserId())
                    .hashFriend(hash)
                    .relationshipStatus(RelationshipStatus.FRIEND)
                    .createdDate(Instant.now())
                    .build());

            List<String> ids = List.of(request.getUserId(), getUserId());

            kafkaTemplate.send("friend-request-accepted", ids);
        }

        friendRequestRepository.deleteByHashFriendRequest(hash);
    }

    @Transactional
    public void updateRelationshipStatus(UpdateRelationshipStatus request) {
        List<String> listIds = Stream.of(getUserId(), request.getUserId()).sorted().toList();
        String hashFriend = generateHash(listIds);
        var current = userRelationshipRepository.findByHashFriend(hashFriend);

        if (Objects.isNull(current))
            throw new AppException(ErrorCode.HASH_FRIEND);

        userRelationshipRepository.updateRelationshipStatus(request.getFriendStatus(), hashFriend);

        if (request.getFriendStatus().equals(RelationshipStatus.UNFRIEND))
            userRelationshipRepository.deleteByHashFriend(hashFriend);
    }

    public PageResponse<FriendResponse> getListFriend(int page, int size) {
        Sort sort = Sort.by("createdDate").descending();
        Pageable pageable = PageRequest.of(page - 1, size, sort);

        Page<UserRelationship> friendsPerPage = userRelationshipRepository.getListFriend(getUserId(), RelationshipStatus.FRIEND, pageable);

        String currentUserId = getUserId();
        List<String> friendUserIds = friendsPerPage.stream()
                .map(friend -> friend.getUserId().equals(currentUserId) 
                    ? friend.getRelatedUserId() 
                    : friend.getUserId())
                .toList();
        
        List<FriendResponse> list;
        try {
            BulkUserProfileRequest bulkRequest = BulkUserProfileRequest.builder()
                    .userIds(friendUserIds)
                    .build();
            
            List<UserProfileResponse> userProfiles = profileClient.getBulkUserProfiles(bulkRequest).getResult();

            list = userProfiles.stream()
                    .map(userProfile -> FriendResponse.builder()
                            .userId(userProfile.getUserId())
                            .displayName(userProfile.getDisplayName())
                            .avatar(userProfile.getAvatar())
                            .build())
                    .toList();
        } catch (Exception e) {
            throw new AppException(ErrorCode.BULK_USER_PROFILE);
        }

        return PageResponse.<FriendResponse>builder()
                .currentPage(page)
                .pageSize(size)
                .totalPages(friendsPerPage.getTotalPages())
                .totalElement(friendsPerPage.getTotalElements())
                .data(list)
                .build();
    }

    public PageResponse<FriendResponse> getListFriendRequest(int page, int size) {
        Sort sort = Sort.by("createdAt").descending();
        Pageable pageable = PageRequest.of(page - 1, size, sort);

        Page<FriendRequest> friendRequestsPerPage = friendRequestRepository.getListFriendRequests(getUserId(), pageable);

        Map<String, Instant> userIdToCreatedAtMap = friendRequestsPerPage.stream()
                .collect(Collectors.toMap(
                        FriendRequest::getUserId,
                        FriendRequest::getCreatedAt
                ));

        List<FriendResponse> list;
        try {
            BulkUserProfileRequest bulkRequest = BulkUserProfileRequest.builder()
                    .userIds(new ArrayList<>(userIdToCreatedAtMap.keySet()))
                    .build();

            List<UserProfileResponse> userProfiles = profileClient.getBulkUserProfiles(bulkRequest).getResult();

            list = userProfiles.stream()
                    .map(userProfile -> FriendResponse.builder()
                            .userId(userProfile.getUserId())
                            .displayName(userProfile.getDisplayName())
                            .avatar(userProfile.getAvatar())
                            .date(userIdToCreatedAtMap.get(userProfile.getUserId()))
                            .build())
                    .toList();
        } catch (Exception e) {
            throw new AppException(ErrorCode.BULK_USER_PROFILE);
        }

        return PageResponse.<FriendResponse>builder()
                .currentPage(page)
                .pageSize(size)
                .totalPages(friendRequestsPerPage.getTotalPages())
                .totalElement(friendRequestsPerPage.getTotalElements())
                .data(list)
                .build();
    }
}
