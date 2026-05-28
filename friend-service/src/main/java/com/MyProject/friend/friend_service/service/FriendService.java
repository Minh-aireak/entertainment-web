package com.MyProject.friend.friend_service.service;

import com.MyProject.common.dto.request.BulkUserProfileRequest;
import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.common.dto.response.UserProfileResponse;
import com.MyProject.common.redis.RedisService;
import com.MyProject.friend.friend_service.document.FriendDoc;
import com.MyProject.friend.friend_service.dto.event.NotificationEvent;
import com.MyProject.friend.friend_service.dto.response.FriendRequestResponse;
import com.MyProject.friend.friend_service.dto.response.UserRelationshipResponse;
import com.MyProject.friend.friend_service.entity.*;
import com.MyProject.friend.friend_service.repository.elasticsearch.FriendElasticRepository;
import com.MyProject.friend.friend_service.repository.mongo.UserRelationshipRepository;
import com.MyProject.friend.friend_service.repository.httpclient.ProfileClient;
import com.MyProject.friend.friend_service.exception.AppException;
import com.MyProject.friend.friend_service.exception.ErrorCode;
import com.MyProject.friend.friend_service.repository.mongo.FriendRequestRepository;
import com.MyProject.friend.friend_service.repository.mongo.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.*;
import java.util.concurrent.TimeUnit;
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
    FriendElasticRepository friendElasticRepository;
    OutboxRepository outboxRepository;
    ObjectMapper objectMapper;
    RedisService redisService;

    private void saveToOutbox(String aggregate, String topic, Object payload) {
        try {
            outboxRepository.save(Outbox.builder()
                    .aggregateId(aggregate)
                    .topic(topic)
                    .payload(objectMapper.writeValueAsString(payload))
                    .build());
        } catch (Exception e) {
            log.error("Failed to save to outbox", e);
        }
    }

    private String getUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null)
            throw new AppException(ErrorCode.UNAUTHORIZED);
        Jwt jwt = ((JwtAuthenticationToken) authentication).getToken();
        return jwt.getClaim("userId");
    }

    private String generateHash(List<String> ids) {
        StringJoiner stringJoiner  = new StringJoiner("_");
        ids.forEach(stringJoiner::add);
        return stringJoiner.toString();
    }

    @Transactional
    public void sendFriendRequest(String toUserId) {
        String senderUserId = getUserId();
        List<String> listSorted = Stream.of(senderUserId, toUserId).sorted().toList();
        String hashFriendRequest = generateHash(listSorted);

        FriendRequest existingRequest = friendRequestRepository.findByHashFriendRequest(hashFriendRequest)
                .orElseThrow(() -> new AppException(ErrorCode.ALREADY_SEND_REQUEST));

        var friendRequestSaved = friendRequestRepository.save(FriendRequest.builder()
                .senderId(senderUserId)
                .receiverId(toUserId)
                .hashFriendRequest(hashFriendRequest)
                .friendRequestStatus(FriendRequestStatus.PENDING)
                .createdAt(Instant.now())
                .build());

        NotificationEvent event = NotificationEvent.builder()
                .typeNotification(TypeNotification.FRIEND_REQUEST)
                .userIdSender(senderUserId)
                .toUserIds(List.of(toUserId))
                .build();

        try {
            outboxRepository.save(Outbox.builder()
                    .aggregateId(friendRequestSaved.getId())
                    .topic("friend.request.sent")
                    .payload(objectMapper.writeValueAsString(event))
                    .build());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize notification event", e);
            throw new RuntimeException("Failed to save outbox event", e);
        }
    }

    @Transactional
    public void friendRequestStatus(String senderId, FriendRequestStatus status) {
        String hash = generateHash(Stream.of(getUserId(), senderId).sorted().toList());
        FriendRequest request = friendRequestRepository.findByHashFriendRequest(hash)
                .orElseThrow(() -> new AppException(ErrorCode.HASH_FRIEND_REQUEST));

        String currentUserId = getUserId();
        if (!request.getReceiverId().equals(currentUserId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        // Chỉ xử lý nếu request đang ở trạng thái PENDING
        if (!request.getFriendRequestStatus().equals(FriendRequestStatus.PENDING)) {
            throw new AppException(ErrorCode.FRIEND_REQUEST_ALREADY_PROCESSED);
        }

        if (status.equals(FriendRequestStatus.ACCEPTED)) {
            handleAccepted(request);
        } else if (!status.equals(FriendRequestStatus.CANCEL)) {
            throw new AppException(ErrorCode.INVALID_STATUS);
        }

        // Xoá friend request sau khi xử lý (cả ACCEPT lẫn REJECT)
        friendRequestRepository.deleteByHashFriendRequest(hash);
    }

    private void handleAccepted(FriendRequest current) {
        var userRelationshipSaved = userRelationshipRepository.save(UserRelationship.builder()
                .senderId(current.getSenderId())
                .receiverId(current.getReceiverId())
                .hashFriend(current.getHashFriendRequest())
                .relationshipStatus(RelationshipStatus.FRIEND)
                .build());
        String aggregate = userRelationshipSaved.getId();
        // Sync cả 2 phía lên Elasticsearch
        syncFriendToEs(aggregate, current.getSenderId(), current.getReceiverId());
        syncFriendToEs(aggregate, current.getReceiverId(), current.getSenderId());

        // Publish event cho conversation service
        saveToOutbox(
                aggregate,
                "friend.request.accepted.conversation",
                List.of(current.getSenderId(), current.getReceiverId())
        );

        // Publish event cho notification service
        NotificationEvent notificationEvent = NotificationEvent.builder()
                .typeNotification(TypeNotification.FRIEND_ACCEPTED)
                .userIdSender(current.getSenderId())
                .toUserIds(List.of(current.getReceiverId()))
                .build();

        saveToOutbox(
                aggregate,
                "friend.request.accepted.notification",
                notificationEvent
        );
    }

    @Transactional
    public void updateRelationshipStatus(String toUserId, RelationshipStatus status) {
        List<String> listIds = Stream.of(getUserId(), toUserId).sorted().toList();
        String hashFriend = generateHash(listIds);
        var current = userRelationshipRepository.findByHashFriend(hashFriend);

        if (Objects.isNull(current))
            throw new AppException(ErrorCode.HASH_FRIEND);

        userRelationshipRepository.updateRelationshipStatus(hashFriend, status);

        if (status.equals(RelationshipStatus.UNFRIEND))
            userRelationshipRepository.deleteByHashFriend(hashFriend);
    }

    public PageResponse<UserRelationshipResponse> getListFriend(int page, int size) {
        String currentUserId = getUserId();

        Sort sort = Sort.by("acceptAt").descending();
        Pageable pageable = PageRequest.of(page - 1, size, sort);

        Page<UserRelationship> friendsPerPage = userRelationshipRepository
                .getListFriend(currentUserId, RelationshipStatus.FRIEND, pageable);

        if (friendsPerPage.isEmpty()) {
            return emptyPage(friendsPerPage);
        }

        // Map friendId -> acceptAt (giữ thứ tự)
        Map<String, Instant> friendIdToAcceptAtMap = friendsPerPage.stream()
                .collect(Collectors.toMap(
                        friend -> friend.getSenderId().equals(currentUserId)
                                ? friend.getReceiverId()
                                : friend.getSenderId(),
                        UserRelationship::getAcceptAt,
                        (existing, duplicate) -> existing,
                        LinkedHashMap::new
                ));

        // Lấy user profiles (cache + API)
        Map<String, UserProfileResponse> userProfilesMap = fetchUserProfiles(
                friendIdToAcceptAtMap.keySet()
        );

        // Build response theo đúng thứ tự DB
        List<UserRelationshipResponse> responses = friendIdToAcceptAtMap.keySet().stream()
                .filter(userProfilesMap::containsKey)
                .map(friendId -> {
                    UserProfileResponse profile = userProfilesMap.get(friendId);
                    return UserRelationshipResponse.builder()
                            .friendId(profile.getUserId())
                            .displayName(profile.getDisplayName())
                            .friendAvatar(profile.getAvatar())
                            .status(RelationshipStatus.FRIEND)
                            .build();
                })
                .toList();

        return PageResponse.<UserRelationshipResponse>builder()
                .currentPage(page)
                .pageSize(size)
                .totalPages(friendsPerPage.getTotalPages())
                .totalElement(friendsPerPage.getTotalElements())
                .data(responses)
                .build();
    }

    private <T> PageResponse<T> emptyPage(Page<?> pageData) {
        return PageResponse.<T>builder()
                .currentPage(pageData.getNumber())
                .pageSize(pageData.getSize())
                .totalPages(pageData.getTotalPages())
                .totalElement(pageData.getTotalElements())
                .data(null)
                .build();
    }

    private Map<String, UserProfileResponse> fetchUserProfiles(Set<String> senderIds) {
        // 1. MultiGet từ Redis
        List<String> keys = senderIds.stream()
                .map(id -> "profile:user:" + id)
                .toList();

        List<Object> cachedValues = redisService.multiGet(keys);

        Map<String, UserProfileResponse> result = new HashMap<>();
        List<String> missingUserIds = new ArrayList<>();

        // 2. Phân loại hit/miss
        List<String> senderIdList = new ArrayList<>(senderIds); // giữ thứ tự với keys
        for (int i = 0; i < senderIdList.size(); i++) {
            Object cached = cachedValues.get(i);
            if (Objects.isNull(cached)) {
                missingUserIds.add(senderIdList.get(i));
            } else {
                UserProfileResponse profile = (UserProfileResponse) cached;
                result.put(profile.getUserId(), profile);
            }
        }

        // 3. Gọi API cho phần bị thiếu
        if (!missingUserIds.isEmpty()) {
            try {
                BulkUserProfileRequest bulkRequest = BulkUserProfileRequest.builder()
                        .userIds(new HashSet<>(missingUserIds))
                        .build();

                Map<String, UserProfileResponse> fetchedProfiles =
                        profileClient.getBulkUserProfiles(bulkRequest).getResult();

                // Cache lại
                fetchedProfiles.forEach((userId, profile) ->
                        redisService.setWithExpiration(
                                "profile:user:" + userId, profile, 1, TimeUnit.HOURS));

                result.putAll(fetchedProfiles);
            } catch (Exception e) {
                log.error("Failed to fetch user profiles: {}", missingUserIds, e);
                throw new AppException(ErrorCode.BULK_USER_PROFILE);
            }
        }

        return result;
    }

    public PageResponse<FriendRequestResponse> getListFriendRequest(int page, int size) {
        Sort sort = Sort.by("createdAt").descending();
        Pageable pageable = PageRequest.of(page - 1, size, sort);

        Page<FriendRequest> friendRequestsPerPage = friendRequestRepository
                .findByReceiverIdAndFriendRequestStatus(getUserId(), FriendRequestStatus.PENDING, pageable);

        if (friendRequestsPerPage.isEmpty()) {
            return emptyPage(friendRequestsPerPage);
        }

        Map<String, Instant> senderIdToCreatedAtMap = friendRequestsPerPage.stream()
                .collect(Collectors.toMap(
                        FriendRequest::getSenderId,
                        FriendRequest::getCreatedAt,
                        (existing, duplicate) -> existing,
                        LinkedHashMap::new  // giữ thứ tự
                ));

        // Lấy user profiles (cache + API)
        Map<String, UserProfileResponse> userProfilesMap = fetchUserProfiles(
                senderIdToCreatedAtMap.keySet()
        );

        // Build response — theo đúng thứ tự senderId từ DB
        List<FriendRequestResponse> responses = senderIdToCreatedAtMap.keySet().stream()
                .filter(userProfilesMap::containsKey)
                .map(senderId -> {
                    UserProfileResponse profile = userProfilesMap.get(senderId);
                    return FriendRequestResponse.builder()
                            .senderId(profile.getUserId())
                            .displayName(profile.getDisplayName())
                            .avatar(profile.getAvatar())
                            .status(FriendRequestStatus.PENDING)
                            .build();
                })
                .toList();

        List<FriendRequestResponse> list = userProfilesMap.values().stream()
                    .map(userProfile -> FriendRequestResponse.builder()
                            .senderId(userProfile.getUserId())
                            .displayName(userProfile.getDisplayName())
                            .avatar(userProfile.getAvatar())
                            .status(FriendRequestStatus.PENDING)
                            .build())
                    .toList();

        return PageResponse.<FriendRequestResponse>builder()
                .currentPage(page)
                .pageSize(size)
                .totalPages(friendRequestsPerPage.getTotalPages())
                .totalElement(friendRequestsPerPage.getTotalElements())
                .data(list)
                .build();
    }

    private void syncFriendToEs(String aggregate, String userId, String friendId) {
        try {
            UserProfileResponse data = (UserProfileResponse) redisService.get("profile:user:" + friendId);

            if (Objects.isNull(data)) {
                data = profileClient.getBulkUserProfiles(
                        BulkUserProfileRequest.builder()
                                .userIds(Set.of(friendId))
                                .build()
                ).getResult().get(friendId);
            }

            redisService.setWithExpiration("profile:user:" + friendId, data, 1, TimeUnit.HOURS);

            FriendDoc friendRequest = FriendDoc.builder()
                    .userId(userId)
                    .friendId(friendId)
                    .friendDisplayName(data.getDisplayName())
                    .friendAvatar(data.getAvatar())
                    .build();

            saveToOutbox(aggregate, "friend.sync", friendRequest);
        } catch (Exception e) {
            log.error("Failed to sync friend to ES for user {} and friend {}", userId, friendId, e);
        }
    }

    public PageResponse<UserRelationshipResponse> searchFriends(String displayName, int page, int size) {
        String userId = getUserId();
        Pageable pageable = PageRequest.of(page - 1, size);
        var searchResult = friendElasticRepository.findByUserIdAndFriendDisplayNameContaining(userId, displayName, pageable);

        return PageResponse.<UserRelationshipResponse>builder()
                .currentPage(page)
                .pageSize(size)
                .totalPages(searchResult.getTotalPages())
                .totalElement(searchResult.getTotalElements())
                .data(searchResult.getContent().stream()
                        .map(doc -> UserRelationshipResponse.builder()
                                .friendId(doc.getFriendId())
                                .displayName(doc.getFriendDisplayName())
                                .friendAvatar(doc.getFriendAvatar())
                                .status(RelationshipStatus.FRIEND)
                                .build())
                        .toList())
                .build();
    }

    public int countMyFriends() {
        return userRelationshipRepository.countMyFriends(getUserId(), RelationshipStatus.FRIEND);
    }
}
