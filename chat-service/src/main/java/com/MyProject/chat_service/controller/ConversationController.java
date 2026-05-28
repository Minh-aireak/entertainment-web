package com.MyProject.chat_service.controller;

import com.MyProject.common.dto.response.ApiResponse;
import com.MyProject.common.dto.response.PageResponse;
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

    @PostMapping
    ApiResponse<ConversationResponse> createConversation(@RequestBody List<String> ids) {
        return ApiResponse.<ConversationResponse>builder()
                .result(conversationService.createConversation(ids))
                .build();
    }

    @GetMapping("/search")
    ApiResponse<PageResponse<ConversationResponse>> searchConversations(@RequestParam String query,
                                                                         @RequestParam(defaultValue = "1") int page,
                                                                         @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.<PageResponse<ConversationResponse>>builder()
                .result(conversationService.searchConversations(query, page, size))
                .build();
    }

    @GetMapping("/my-conversations")
    ApiResponse<PageResponse<ConversationResponse>> getMyConversations(@RequestParam(value = "page", defaultValue = "1") int page,
                                                                        @RequestParam(value = "size", defaultValue = "10") int size) {
        return ApiResponse.<PageResponse<ConversationResponse>>builder()
                .result(conversationService.getMyConversations(page, size))
                .build();
    }
}
