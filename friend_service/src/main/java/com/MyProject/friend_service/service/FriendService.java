package com.MyProject.friend_service.service;

import com.MyProject.friend_service.dto.request.UpdateFriendRequestStatus;
import com.MyProject.friend_service.dto.request.UpdateRelationshipStatus;
import com.MyProject.friend_service.dto.response.FriendResponse;
import com.MyProject.friend_service.entity.FriendRequest;
import com.MyProject.friend_service.entity.RelationshipStatus;
import com.MyProject.friend_service.entity.UserRelationship;
import com.MyProject.friend_service.mapper.FriendMapper;
import com.MyProject.friend_service.repository.FriendRequestRepository;
import com.MyProject.friend_service.repository.UserRelationshipRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.StringJoiner;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FriendService {
    FriendRequestRepository friendRequestRepository;
    UserRelationshipRepository userRelationshipRepository;
    FriendMapper friendMapper;

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
        String hashFriendRequest = generateHash(List.of(getUserId(), toUserId));
        friendRequestRepository.findByHashFriendRequest(hashFriendRequest)
                        .orElseGet(() ->
                            friendRequestRepository.save(FriendRequest.builder()
                                    .userId(getUserId())
                                    .toUserId(toUserId)
                                    .hashFriendRequest(generateHash(List.of(getUserId(), toUserId)))
                                    .friendRequestStatus(com.MyProject.friend_service.entity.FriendRequestStatus.PENDING)
                                    .createdAt(Instant.now())
                                    .build())
                        );
    }

    @Transactional
    public void updateFriendRequestStatus(UpdateFriendRequestStatus request) {
        friendRequestRepository.updateFriendRequestStatus(
                request.getFriendRequestStatus(),
                request.getFriendRequest().getHashFriendRequest());

        if (request.getFriendRequestStatus().equals("ACCEPTED"))
            userRelationshipRepository.save(UserRelationship.builder()
                            .userId(request.getFriendRequest().getUserId())
                            .relatedUserId(request.getFriendRequest().getToUserId())
                            .hashFriend(request.getFriendRequest().getHashFriendRequest())
                            .relationshipStatus(RelationshipStatus.FRIEND)
                    .build());
    }

    @Transactional
    public void updateRelationshipStatus(UpdateRelationshipStatus request) {
        userRelationshipRepository.updateRelationshipStatus(
                request.getFriendStatus(),
                request.getFriendRequest().getHashFriendRequest());

        if (request.getFriendStatus().equals("UNFRIEND"))
            userRelationshipRepository.deleteByHashFriend(request.getFriendRequest().getHashFriendRequest());
    }

    public List<FriendResponse> findAllFriendsOf() {
        var infos = userRelationshipRepository.findAllFriendsOf(getUserId(), RelationshipStatus.FRIEND);
        return infos.stream().map(friendMapper::toFriendResponse
        ).toList();
    }
}
