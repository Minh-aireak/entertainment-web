package com.MyProject.chat_service.controller;

import com.MyProject.chat_service.service.ChatApiRateLimitService;
import com.MyProject.common.security.SecurityUtils;
import com.MyProject.common.dto.response.ApiResponse;
import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.chat_service.dto.request.ConversationUpdateRequest;
import com.MyProject.chat_service.dto.response.ConversationResponse;
import com.MyProject.chat_service.service.ConversationService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/conversations")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ConversationController {
    ConversationService conversationService;
    ChatApiRateLimitService chatApiRateLimitService;

    @PostMapping
    ApiResponse<ConversationResponse> createConversation(@RequestBody List<String> ids) {
        String userId = SecurityUtils.getCurrentUserId();
        chatApiRateLimitService.checkConversationWrite(userId);
        return ApiResponse.<ConversationResponse>builder()
                .result(conversationService.createConversationForApi(ids))
                .build();
    }

    @GetMapping("/search")
    ApiResponse<PageResponse<ConversationResponse>> searchConversations(@RequestParam String query,
                                                                         @RequestParam(defaultValue = "1") int page,
                                                                         @RequestParam(defaultValue = "10") int size) {
        String userId = SecurityUtils.getCurrentUserId();
        chatApiRateLimitService.checkConversationSearch(userId);
        return ApiResponse.<PageResponse<ConversationResponse>>builder()
                .result(conversationService.searchConversations(query, page, size))
                .build();
    }

    @GetMapping("/my-conversations")
    ApiResponse<PageResponse<ConversationResponse>> getMyConversations(@RequestParam(value = "page", defaultValue = "1") int page,
                                                                        @RequestParam(value = "size", defaultValue = "10") int size) {
        String userId = SecurityUtils.getCurrentUserId();
        chatApiRateLimitService.checkConversationRead(userId);
        return ApiResponse.<PageResponse<ConversationResponse>>builder()
                .result(conversationService.getMyConversations(page, size))
                .build();
    }

    @PutMapping("/{conversationId}")
    ApiResponse<ConversationResponse> updateConversation(@PathVariable String conversationId,
                                                           @RequestBody ConversationUpdateRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        chatApiRateLimitService.checkConversationWrite(userId);
        return ApiResponse.<ConversationResponse>builder()
                .result(conversationService.updateGroupConversation(conversationId, request))
                .build();
    }
}
