package com.MyProject.chat_service.controller;

import com.MyProject.chat_service.dto.request.ChatMessageCreateRequest;
import com.MyProject.chat_service.dto.request.ChatMessageDeleteRequest;
import com.MyProject.chat_service.dto.request.ChatMessageUpdateRequest;
import com.MyProject.common.dto.response.ApiResponse;
import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.chat_service.service.ChatMessageService;
import com.MyProject.chat_service.dto.response.UnreadCountResponse;
import com.MyProject.chat_service.dto.response.ChatMessageResponse;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/messages")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ChatMessageController {
    ChatMessageService chatMessageService;

    @PostMapping
    ApiResponse<ChatMessageResponse> createChatMessage(@RequestBody @Valid ChatMessageCreateRequest request) {
        return ApiResponse.<ChatMessageResponse>builder()
                .result(chatMessageService.createChatMessage(request))
                .build();
    }

    @GetMapping("/{conversationId}/search")
    ApiResponse<PageResponse<ChatMessageResponse>> searchMessages(@PathVariable String conversationId,
                                                                   @RequestParam String query,
                                                                   @RequestParam(defaultValue = "1") int page,
                                                                   @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.<PageResponse<ChatMessageResponse>>builder()
                .result(chatMessageService.searchMessages(conversationId, query, page, size))
                .build();
    }

    @GetMapping("my-messages")
    ApiResponse<PageResponse<ChatMessageResponse>> getMyChatMessages(@RequestParam String conversationId,
                                                                     @RequestParam int page,
                                                                     @RequestParam int size) {
        return ApiResponse.<PageResponse<ChatMessageResponse>>builder()
                .result(chatMessageService.getMyChatMessages(conversationId, page, size))
                .build();
    }

    @DeleteMapping
    ApiResponse<Void> deleteChatMessage(@RequestBody ChatMessageDeleteRequest request) {
        chatMessageService.deleteChatMessage(request);
        return ApiResponse.<Void>builder()
                .build();
    }

    @PutMapping
    ApiResponse<ChatMessageResponse> updateChatMessage(@RequestBody @Valid ChatMessageUpdateRequest request) {
        return ApiResponse.<ChatMessageResponse>builder()
                .result(chatMessageService.updateChatMessage(request))
                .build();
    }

    @PutMapping("/mark-as-seen/{conversationId}")
    ApiResponse<Void> seenAt(@PathVariable String conversationId) {
        chatMessageService.seenAt(conversationId);
        return ApiResponse.<Void>builder()
                .build();
    }

    @GetMapping("/unread-count")
    ApiResponse<UnreadCountResponse> getUnreadCount() {
        return ApiResponse.<UnreadCountResponse>builder()
                .result(chatMessageService.getUnreadCount())
                .build();
    }
}
