package com.MyProject.friend_service.controller;

import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.friend_service.dto.request.UpdateFriendRequestStatus;
import com.MyProject.friend_service.dto.request.UpdateRelationshipStatus;
import com.MyProject.friend_service.dto.response.ApiResponse;
import com.MyProject.friend_service.dto.response.FriendResponse;
import com.MyProject.friend_service.service.FriendService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FriendController {
    FriendService friendService;

    @PostMapping("/send-friend/{toUserId}")
    ApiResponse<Void> sendFriend(@PathVariable String toUserId) {
        friendService.sendFriend(toUserId);
        return ApiResponse.<Void>builder()
                .message("Send friend success!")
                .build();
    }

    @PutMapping("/update-request")
    ApiResponse<Void> updateFriendRequestStatus(@RequestBody UpdateFriendRequestStatus request) {
        friendService.updateFriendRequestStatus(request);
        return ApiResponse.<Void>builder()
                .message("Update success!")
                .build();
    }

    @PutMapping("/update-relationship")
    ApiResponse<Void> updateRelationshipStatus(@RequestBody UpdateRelationshipStatus request) {
        friendService.updateRelationshipStatus(request);
        return ApiResponse.<Void>builder()
                .message("Update relationship success!")
                .build();
    }

    @GetMapping("/list-friends")
    ApiResponse<PageResponse<FriendResponse>> getListFriend(@RequestParam int page,
                                                            @RequestParam int size) {
        return ApiResponse.<PageResponse<FriendResponse>>builder()
                .result(friendService.getListFriend(page, size))
                .build();
    }

    @GetMapping("/list-friend-requests")
    ApiResponse<PageResponse<FriendResponse>> getListFriendRequest(@RequestParam int page,
                                                                   @RequestParam int size) {
        return ApiResponse.<PageResponse<FriendResponse>>builder()
                .result(friendService.getListFriendRequest(page, size))
                .build();
    }

//    @DeleteMapping("/delete")
//    ApiResponse<ChatMessageResponse> deleteChatMessage(@RequestBody ChatMessageDeleteRequest request) {
//        return ApiResponse.<ChatMessageResponse>builder()
//                .result(chatMessageService.deleteChatMessage(request))
//                .build();
//    }
//
//
//    @PutMapping("/mark-as-seen/{conversationId}")
//    ApiResponse<Void> seenAt(@PathVariable String conversationId) {
//        chatMessageService.seenAt(conversationId);
//        return ApiResponse.<Void>builder()
//                .build();
//    }
}
