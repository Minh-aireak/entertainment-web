package com.MyProject.chat_service.controller;

import com.MyProject.chat_service.dto.response.ApiResponse;
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

    @PostMapping("/create")
    ApiResponse<ConversationResponse> createConversation(@RequestBody List<String> ids) {
        return ApiResponse.<ConversationResponse>builder()
                .result(conversationService.createConversation(ids))
                .build();
    }

    @GetMapping("/my-conversations")
    ApiResponse<List<ConversationResponse>> getMyConversations() {
        return ApiResponse.<List<ConversationResponse>>builder()
                .result(conversationService.getMyConversations())
                .build();
    }
}
