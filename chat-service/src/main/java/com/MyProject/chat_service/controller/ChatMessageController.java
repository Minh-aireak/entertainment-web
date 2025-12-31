package com.MyProject.chat_service.controller;

import com.MyProject.chat_service.dto.request.ChatMessageCreateRequest;
import com.MyProject.chat_service.dto.request.ChatMessageDeleteRequest;
import com.MyProject.chat_service.dto.request.ChatMessageUpdateRequest;
import com.MyProject.chat_service.dto.response.ApiResponse;
import com.MyProject.common_dto.event.dto.response.PageResponse;
import com.MyProject.chat_service.service.ChatMessageService;
import com.MyProject.common_dto.event.dto.response.ChatMessageResponse;
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

    @PostMapping("/create")
    ApiResponse<ChatMessageResponse> createChatMessage(@RequestBody @Valid ChatMessageCreateRequest request) {
        return ApiResponse.<ChatMessageResponse>builder()
                .result(chatMessageService.createChatMessage(request))
                .build();
    }

    @GetMapping
    ApiResponse<PageResponse<ChatMessageResponse>> getMyChatMessages(@RequestParam String conversationId,
                                                                     @RequestParam int page,
                                                                     @RequestParam int size) {
        return ApiResponse.<PageResponse<ChatMessageResponse>>builder()
                .result(chatMessageService.getMyChatMessages(conversationId, page, size))
                .build();
    }

    @DeleteMapping("/delete")
    ApiResponse<ChatMessageResponse> deleteChatMessage(@RequestBody ChatMessageDeleteRequest request) {
        return ApiResponse.<ChatMessageResponse>builder()
                .result(chatMessageService.deleteChatMessage(request))
                .build();
    }

    @PutMapping("/update")
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
}
