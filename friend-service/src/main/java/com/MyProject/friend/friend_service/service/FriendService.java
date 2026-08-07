package com.MyProject.friend.friend_service.service;

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
import com.MyProject.friend.friend_service.exception.AppException;
import com.MyProject.friend.friend_service.exception.ErrorCode;
import com.MyProject.friend.friend_service.repository.mongo.FriendRequestRepository;
import com.MyProject.common.security.SecurityUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
    FriendProfileExternalService friendProfileExternalService;
    UserRelationshipRepository userRelationshipRepository;
    FriendElasticRepository friendElasticRepository;
    RedisService redisService;
    OutboxEventPublisher outboxEventPublisher;

    private String generateHash(List<String> ids) {
        StringJoiner stringJoiner  = new StringJoiner("_");
        ids.forEach(stringJoiner::add);
        return stringJoiner.toString();
    }

    @Transactional
    public void sendFriendRequest(String toUserId) {
        String senderUserId = SecurityUtils.getCurrentUserId();
        if (senderUserId.equals(toUserId)) {
            throw new AppException(ErrorCode.CANNOT_SEND_REQUEST_TO_SELF);
        }

        List<String> listSorted = Stream.of(senderUserId, toUserId).sorted().toList();
        String hashFriendRequest = generateHash(listSorted);

        if (userRelationshipRepository.existsByHashFriend(hashFriendRequest)) {
            throw new AppException(ErrorCode.ALREADY_FRIEND);
        }

        friendRequestRepository.findByHashFriendRequest(hashFriendRequest)
                .ifPresent(request -> {
                    throw new AppException(ErrorCode.ALREADY_SEND_REQUEST);
                });

        var friendRequestSaved = friendRequestRepository.save(FriendRequest.builder()
                .senderId(senderUserId)
                .receiverId(toUserId)
                .hashFriendRequest(hashFriendRequest)
                .friendRequestStatus(FriendRequestStatus.PENDING)
                .createdAt(Instant.now())
                .build());

        NotificationEvent event = NotificationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .typeNotification("FRIEND_REQUEST")
                .userIdSender(senderUserId)
                .toUserIds(List.of(toUserId))
                .build();

        outboxEventPublisher.publish(friendRequestSaved.getId(), "notification.events", event);
    }

    @Transactional
    public void friendRequestStatus(String senderId, FriendRequestStatus status) {
        String hash = generateHash(Stream.of(SecurityUtils.getCurrentUserId(), senderId).sorted().toList());
        FriendRequest request = friendRequestRepository.findByHashFriendRequest(hash)
                .orElseThrow(() -> new AppException(ErrorCode.HASH_FRIEND_REQUEST));

        String currentUserId = SecurityUtils.getCurrentUserId();
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
        outboxEventPublisher.publish(
                aggregate,
                "friend.request.accepted.conversation",
                List.of(current.getSenderId(), current.getReceiverId())
        );

        // Publish event cho notification service
        NotificationEvent notificationEvent = NotificationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .typeNotification("FRIEND_ACCEPTED")
                .userIdSender(current.getReceiverId())
                .toUserIds(List.of(current.getSenderId()))
                .build();

        outboxEventPublisher.publish(
                aggregate,
                "notification.events",
                notificationEvent
        );
    }

    @Transactional
    public void updateRelationshipStatus(String toUserId, RelationshipStatus status) {
        List<String> listIds = Stream.of(SecurityUtils.getCurrentUserId(), toUserId).sorted().toList();
        String hashFriend = generateHash(listIds);
        var current = userRelationshipRepository.findByHashFriend(hashFriend);

        if (Objects.isNull(current))
            throw new AppException(ErrorCode.HASH_FRIEND);

        if (status.equals(RelationshipStatus.UNFRIEND)) {
            userRelationshipRepository.deleteByHashFriend(hashFriend);
        } else {
            userRelationshipRepository.updateRelationshipStatus(hashFriend, status);
        }
    }

    public PageResponse<UserRelationshipResponse> getListFriend(int page, int size) {
        String currentUserId = SecurityUtils.getCurrentUserId();

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

    public PageResponse<UserProfileResponse> getFriendSuggestions(int page, int size) {
        String currentUserId = SecurityUtils.getCurrentUserId();
        Set<String> excludedUserIds = new HashSet<>();
        excludedUserIds.add(currentUserId);

        userRelationshipRepository.findRelationshipsForSuggestions(
                        currentUserId,
                        RelationshipStatus.FRIEND
                ).stream()
                .map(relationship -> relationship.getSenderId().equals(currentUserId)
                        ? relationship.getReceiverId()
                        : relationship.getSenderId())
                .forEach(excludedUserIds::add);

        friendRequestRepository.findRequestsForSuggestions(
                        currentUserId,
                        FriendRequestStatus.PENDING
                ).stream()
                .map(request -> request.getSenderId().equals(currentUserId)
                        ? request.getReceiverId()
                        : request.getSenderId())
                .forEach(excludedUserIds::add);

        PageResponse<UserProfileResponse> suggestions = friendProfileExternalService
                .getSuggestionProfiles(excludedUserIds, page - 1, size);

        return PageResponse.<UserProfileResponse>builder()
                .currentPage(page)
                .pageSize(size)
                .totalPages(suggestions.getTotalPages())
                .totalElement(suggestions.getTotalElement())
                .data(suggestions.getData())
                .build();
    }

    private <T> PageResponse<T> emptyPage(Page<?> pageData) {
        return PageResponse.<T>builder()
                .currentPage(pageData.getNumber())
                .pageSize(pageData.getSize())
                .totalPages(pageData.getTotalPages())
                .totalElement(pageData.getTotalElements())
                .data(List.of())
                .build();
    }

    private Map<String, UserProfileResponse> fetchUserProfiles(Set<String> senderIds) {
        // 1. MultiGet từ Redis
        List<String> keys = senderIds.stream()
                .map(id -> "profile:user:" + id)
                .toList();

        List<UserProfileResponse> cachedValues = Collections.nCopies(keys.size(), null);
        try {
            cachedValues = redisService.multiGet(keys, new TypeReference<UserProfileResponse>() {});
        } catch (Exception e) {
            log.error("Failed to multiGet profiles from cache for keys: {}", keys, e);
        }

        Map<String, UserProfileResponse> result = new HashMap<>();
        List<String> missingUserIds = new ArrayList<>();

        // 2. Phân loại hit/miss
        List<String> senderIdList = new ArrayList<>(senderIds); // giữ thứ tự với keys
        for (int i = 0; i < senderIdList.size(); i++) {
            UserProfileResponse profile = (i < cachedValues.size()) ? cachedValues.get(i) : null;
            if (Objects.isNull(profile)) {
                missingUserIds.add(senderIdList.get(i));
            } else {
                result.put(profile.getUserId(), profile);
            }
        }

        // 3. Gọi API cho phần bị thiếu
        if (!missingUserIds.isEmpty()) {
            try {
                Map<String, UserProfileResponse> fetchedProfiles =
                        friendProfileExternalService.getBulkUserProfiles(new HashSet<>(missingUserIds));

                if (!fetchedProfiles.isEmpty()) {
                    // Cache lại
                    fetchedProfiles.forEach((userId, profile) -> {
                        try {
                            redisService.setWithExpiration(
                                    "profile:user:" + userId, profile, 1, TimeUnit.HOURS);
                        } catch (Exception e) {
                            log.error("Failed to cache profile for userId: {}", userId, e);
                        }
                    });

                    result.putAll(fetchedProfiles);
                }
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
                .findByReceiverIdAndFriendRequestStatus(SecurityUtils.getCurrentUserId(), FriendRequestStatus.PENDING, pageable);

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

        return PageResponse.<FriendRequestResponse>builder()
                .currentPage(page)
                .pageSize(size)
                .totalPages(friendRequestsPerPage.getTotalPages())
                .totalElement(friendRequestsPerPage.getTotalElements())
                .data(responses)
                .build();
    }

    private void syncFriendToEs(String aggregate, String userId, String friendId) {
        try {
            UserProfileResponse data = redisService.get("profile:user:" + friendId, new TypeReference<UserProfileResponse>() {});

            if (Objects.isNull(data)) {
                data = friendProfileExternalService.getBulkUserProfiles(Set.of(friendId)).get(friendId);
                if (Objects.isNull(data)) {
                    log.warn("Profile fallback returned empty data for friendId: {}", friendId);
                    return;
                }
            }

            redisService.setWithExpiration("profile:user:" + friendId, data, 1, TimeUnit.HOURS);

            FriendDoc friendDoc = FriendDoc.builder()
                    .id(aggregate)
                    .userId(userId)
                    .friendId(friendId)
                    .friendDisplayName(data.getDisplayName())
                    .build();

            outboxEventPublisher.publish(aggregate, "friend.sync", friendDoc);
        } catch (Exception e) {
            log.error("Failed to sync friend to ES for user {} and friend {}", userId, friendId, e);
        }
    }

    public PageResponse<UserRelationshipResponse> searchFriends(String displayName, int page, int size) {
        String userId = SecurityUtils.getCurrentUserId();
        Pageable pageable = PageRequest.of(page - 1, size);
        var searchResult = friendElasticRepository.findByUserIdAndFriendDisplayNameContaining(userId, displayName, pageable);

        // ES chỉ dùng để MATCH theo tên - avatar/tên hiển thị luôn lấy live từ profile-service (qua
        // cache) tại đây, không tin vào giá trị đã lưu trong FriendDoc (có thể cũ, xem FriendDoc).
        Set<String> friendIds = searchResult.getContent().stream()
                .map(FriendDoc::getFriendId)
                .collect(Collectors.toSet());
        Map<String, UserProfileResponse> userProfilesMap = fetchUserProfiles(friendIds);

        return PageResponse.<UserRelationshipResponse>builder()
                .currentPage(page)
                .pageSize(size)
                .totalPages(searchResult.getTotalPages())
                .totalElement(searchResult.getTotalElements())
                .data(searchResult.getContent().stream()
                        .map(doc -> {
                            UserProfileResponse profile = userProfilesMap.get(doc.getFriendId());
                            return UserRelationshipResponse.builder()
                                    .friendId(doc.getFriendId())
                                    .displayName(profile != null ? profile.getDisplayName() : doc.getFriendDisplayName())
                                    .friendAvatar(profile != null ? profile.getAvatar() : null)
                                    .status(RelationshipStatus.FRIEND)
                                    .build();
                        })
                        .toList())
                .build();
    }

    public int countMyFriends() {
        long count = userRelationshipRepository.countMyFriends(
                SecurityUtils.getCurrentUserId(),
                RelationshipStatus.FRIEND
        );
        return Math.toIntExact(count);
    }
}
