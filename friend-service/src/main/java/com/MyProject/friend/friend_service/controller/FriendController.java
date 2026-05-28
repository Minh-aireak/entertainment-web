package com.MyProject.friend.friend_service.controller;

import com.MyProject.common.dto.response.ApiResponse;
import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.friend.friend_service.dto.response.FriendRequestResponse;
import com.MyProject.friend.friend_service.dto.response.UserRelationshipResponse;
import com.MyProject.friend.friend_service.entity.FriendRequestStatus;
import com.MyProject.friend.friend_service.entity.RelationshipStatus;
import com.MyProject.friend.friend_service.service.FriendService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FriendController {
    FriendService friendService;

    @PostMapping("/requests/{toUserId}")
    ApiResponse<Void> sendFriendRequest(@PathVariable("toUserId") String toUserId) {
        friendService.sendFriendRequest(toUserId);
        return ApiResponse.<Void>builder()
                .message("Send friend success!")
                .build();
    }

    @GetMapping("/my-friends")
    ApiResponse<PageResponse<UserRelationshipResponse>> getMyFriends(@RequestParam(value = "page", defaultValue = "1") int page,
                                                                     @RequestParam(value = "size", defaultValue = "10") int size) {
        return ApiResponse.<PageResponse<UserRelationshipResponse>>builder()
                .result(friendService.getListFriend(page, size))
                .build();
    }

    @GetMapping("/search")
    ApiResponse<PageResponse<UserRelationshipResponse>> searchFriends(@RequestParam String displayName,
                                                                      @RequestParam(defaultValue = "1") int page,
                                                                      @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.<PageResponse<UserRelationshipResponse>>builder()
                .result(friendService.searchFriends(displayName, page, size))
                .build();
    }

    @PutMapping("/requests/{senderId}")
    ApiResponse<Void> friendRequestStatus(@PathVariable("senderId") String senderId,
                                          @RequestParam FriendRequestStatus status) {
        friendService.friendRequestStatus(senderId, status);
        return ApiResponse.<Void>builder()
                .build();
    }

    @PutMapping("/relationship/{toUserId}")
    ApiResponse<Void> updateRelationshipStatus(@PathVariable("toUserId") String toUserId,
                                               @RequestParam RelationshipStatus status) {
        friendService.updateRelationshipStatus(toUserId, status);
        return ApiResponse.<Void>builder()
                .build();
    }

    @GetMapping("/count")
    ApiResponse<Integer> countMyFriends() {
        return ApiResponse.<Integer>builder()
                .result(friendService.countMyFriends())
                .build();
    }

    @GetMapping("/requests")
    ApiResponse<PageResponse<FriendRequestResponse>> getMyFriendRequests(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return ApiResponse.<PageResponse<FriendRequestResponse>>builder()
                .result(friendService.getListFriendRequest(page, size))
                .build();
    }
}
