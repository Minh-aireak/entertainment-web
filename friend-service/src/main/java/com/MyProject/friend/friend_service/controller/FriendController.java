package com.MyProject.friend.friend_service.controller;

import com.MyProject.common.dto.response.ApiResponse;
import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.common.dto.response.UserProfileResponse;
import com.MyProject.friend.friend_service.dto.response.FriendRequestResponse;
import com.MyProject.friend.friend_service.dto.response.UserRelationshipResponse;
import com.MyProject.friend.friend_service.entity.FriendRequestStatus;
import com.MyProject.friend.friend_service.entity.RelationshipStatus;
import com.MyProject.friend.friend_service.service.FriendApiRateLimitService;
import com.MyProject.friend.friend_service.service.FriendService;
import com.MyProject.common.security.SecurityUtils;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FriendController {
    FriendService friendService;
    FriendApiRateLimitService friendApiRateLimitService;

    @PostMapping("/requests/{toUserId}")
    @RateLimiter(name = "friendApi")
    ApiResponse<Void> sendFriendRequest(@PathVariable("toUserId") String toUserId) {
        String userId = SecurityUtils.getCurrentUserId();
        friendApiRateLimitService.checkSendFriendRequest(userId);
        friendService.sendFriendRequest(toUserId);
        return ApiResponse.<Void>builder()
                .message("Send friend success!")
                .build();
    }

    @GetMapping("/my-friends")
    @RateLimiter(name = "friendApi")
    ApiResponse<PageResponse<UserRelationshipResponse>> getMyFriends(@RequestParam(value = "page", defaultValue = "1") int page,
                                                                     @RequestParam(value = "size", defaultValue = "10") int size) {
        String userId = SecurityUtils.getCurrentUserId();
        friendApiRateLimitService.checkReadFriends(userId);
        return ApiResponse.<PageResponse<UserRelationshipResponse>>builder()
                .result(friendService.getListFriend(page, size))
                .build();
    }

    @GetMapping("/suggestions")
    @RateLimiter(name = "friendApi")
    ApiResponse<PageResponse<UserProfileResponse>> getFriendSuggestions(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        String userId = SecurityUtils.getCurrentUserId();
        friendApiRateLimitService.checkReadFriends(userId);
        return ApiResponse.<PageResponse<UserProfileResponse>>builder()
                .result(friendService.getFriendSuggestions(page, size))
                .build();
    }

    @GetMapping("/search")
    @RateLimiter(name = "friendApi")
    ApiResponse<PageResponse<UserRelationshipResponse>> searchFriends(@RequestParam String displayName,
                                                                      @RequestParam(defaultValue = "1") int page,
                                                                      @RequestParam(defaultValue = "10") int size) {
        String userId = SecurityUtils.getCurrentUserId();
        friendApiRateLimitService.checkSearchFriends(userId);
        return ApiResponse.<PageResponse<UserRelationshipResponse>>builder()
                .result(friendService.searchFriends(displayName, page, size))
                .build();
    }

    @PutMapping("/requests/{senderId}")
    @RateLimiter(name = "friendApi")
    ApiResponse<Void> friendRequestStatus(@PathVariable("senderId") String senderId,
                                          @RequestParam FriendRequestStatus status) {
        String userId = SecurityUtils.getCurrentUserId();
        if (status == FriendRequestStatus.ACCEPTED) {
            friendApiRateLimitService.checkAcceptFriendRequest(userId);
        }
        friendService.friendRequestStatus(senderId, status);
        return ApiResponse.<Void>builder()
                .build();
    }

    @PutMapping("/relationship/{toUserId}")
    @RateLimiter(name = "friendApi")
    ApiResponse<Void> updateRelationshipStatus(@PathVariable("toUserId") String toUserId,
                                                 @RequestParam RelationshipStatus status) {
        String userId = SecurityUtils.getCurrentUserId();
        if (status == RelationshipStatus.UNFRIEND) {
            friendApiRateLimitService.checkUnfriend(userId);
        }
        friendService.updateRelationshipStatus(toUserId, status);
        return ApiResponse.<Void>builder()
                .build();
    }

    @GetMapping("/count")
    @RateLimiter(name = "friendApi")
    ApiResponse<Integer> countMyFriends() {
        String userId = SecurityUtils.getCurrentUserId();
        friendApiRateLimitService.checkCountFriends(userId);
        return ApiResponse.<Integer>builder()
                .result(friendService.countMyFriends())
                .build();
    }

    @GetMapping("/requests")
    @RateLimiter(name = "friendApi")
    ApiResponse<PageResponse<FriendRequestResponse>> getMyFriendRequests(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        String userId = SecurityUtils.getCurrentUserId();
        friendApiRateLimitService.checkReadFriendRequests(userId);
        return ApiResponse.<PageResponse<FriendRequestResponse>>builder()
                .result(friendService.getListFriendRequest(page, size))
                .build();
    }
}
