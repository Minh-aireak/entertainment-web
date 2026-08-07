package com.MyProject.room_service.controller;

import com.MyProject.room_service.dto.request.CreateRoomRequest;
import com.MyProject.room_service.dto.request.JoinRoomRequest;
import com.MyProject.room_service.dto.request.PlaybackUpdateRequest;
import com.MyProject.room_service.dto.request.RoomMessageCreateRequest;
import com.MyProject.room_service.dto.response.RoomListItemResponse;
import com.MyProject.room_service.dto.response.RoomMessageResponse;
import com.MyProject.room_service.dto.response.RoomParticipantResponse;
import com.MyProject.room_service.dto.response.RoomResponse;
import com.MyProject.room_service.service.RoomApiRateLimitService;
import com.MyProject.room_service.service.RoomMessageService;
import com.MyProject.room_service.service.RoomService;
import com.MyProject.common.dto.response.ApiResponse;
import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.common.security.SecurityUtils;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoomController {
    RoomService roomService;
    RoomMessageService roomMessageService;
    RoomApiRateLimitService roomApiRateLimitService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @RateLimiter(name = "roomWriteApi")
    public ApiResponse<RoomResponse> createRoom(@RequestBody CreateRoomRequest request) {
        roomApiRateLimitService.checkRoomCreate(SecurityUtils.getCurrentUserId());
        return ApiResponse.<RoomResponse>builder().result(roomService.createRoom(request)).build();
    }

    @GetMapping("/public")
    @PreAuthorize("isAuthenticated()")
    @RateLimiter(name = "roomReadApi")
    public ApiResponse<PageResponse<RoomListItemResponse>> listPublicRooms(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "12") int size) {
        return ApiResponse.<PageResponse<RoomListItemResponse>>builder()
                .result(roomService.listPublicRooms(page, size))
                .build();
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    @RateLimiter(name = "roomReadApi")
    public ApiResponse<PageResponse<RoomListItemResponse>> listMyRooms(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "12") int size) {
        return ApiResponse.<PageResponse<RoomListItemResponse>>builder()
                .result(roomService.listMyRooms(page, size))
                .build();
    }

    @GetMapping("/{roomId}")
    @PreAuthorize("isAuthenticated()")
    @RateLimiter(name = "roomReadApi")
    public ApiResponse<RoomResponse> getRoom(@PathVariable String roomId) {
        return ApiResponse.<RoomResponse>builder().result(roomService.getRoom(roomId)).build();
    }

    @PostMapping("/{roomId}/join")
    @PreAuthorize("isAuthenticated()")
    @RateLimiter(name = "roomWriteApi")
    public ApiResponse<RoomResponse> joinRoom(@PathVariable String roomId,
                                               @RequestBody(required = false) JoinRoomRequest request) {
        return ApiResponse.<RoomResponse>builder().result(roomService.joinRoom(roomId, request)).build();
    }

    @PostMapping("/{roomId}/leave")
    @PreAuthorize("isAuthenticated()")
    @RateLimiter(name = "roomWriteApi")
    public ApiResponse<Void> leaveRoom(@PathVariable String roomId) {
        roomService.leaveRoom(roomId);
        return ApiResponse.<Void>builder().build();
    }

    @DeleteMapping("/{roomId}")
    @PreAuthorize("isAuthenticated()")
    @RateLimiter(name = "roomWriteApi")
    public ApiResponse<Void> closeRoom(@PathVariable String roomId) {
        roomService.closeRoom(roomId);
        return ApiResponse.<Void>builder().build();
    }

    @PatchMapping("/{roomId}/playback")
    @PreAuthorize("isAuthenticated()")
    @RateLimiter(name = "roomWriteApi")
    public ApiResponse<Void> updatePlayback(@PathVariable String roomId, @RequestBody PlaybackUpdateRequest request) {
        roomApiRateLimitService.checkRoomPlayback(SecurityUtils.getCurrentUserId(), roomId);
        roomService.updatePlayback(roomId, request);
        return ApiResponse.<Void>builder().build();
    }

    @GetMapping("/{roomId}/participants")
    @PreAuthorize("isAuthenticated()")
    @RateLimiter(name = "roomReadApi")
    public ApiResponse<List<RoomParticipantResponse>> listParticipants(@PathVariable String roomId) {
        return ApiResponse.<List<RoomParticipantResponse>>builder()
                .result(roomService.listParticipants(roomId))
                .build();
    }

    @GetMapping("/{roomId}/messages")
    @PreAuthorize("isAuthenticated()")
    @RateLimiter(name = "roomReadApi")
    public ApiResponse<PageResponse<RoomMessageResponse>> listMessages(
            @PathVariable String roomId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "30") int size) {
        return ApiResponse.<PageResponse<RoomMessageResponse>>builder()
                .result(roomMessageService.listMessages(roomId, page, size))
                .build();
    }

    @PostMapping("/{roomId}/messages")
    @PreAuthorize("isAuthenticated()")
    @RateLimiter(name = "roomWriteApi")
    public ApiResponse<RoomMessageResponse> sendMessage(@PathVariable String roomId,
                                                         @RequestBody RoomMessageCreateRequest request) {
        roomApiRateLimitService.checkRoomMessage(SecurityUtils.getCurrentUserId(), roomId);
        return ApiResponse.<RoomMessageResponse>builder()
                .result(roomMessageService.sendMessage(roomId, request))
                .build();
    }
}
