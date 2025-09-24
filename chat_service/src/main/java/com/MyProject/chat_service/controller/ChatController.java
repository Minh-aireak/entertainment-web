package com.MyProject.chat_service.controller;

import com.MyProject.chat_service.dto.request.ConversationRequest;
import com.MyProject.chat_service.dto.response.ApiResponse;
import com.MyProject.chat_service.dto.response.ConversationResponse;
import com.MyProject.chat_service.service.ConversationService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/conversations")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ChatController {
    ConversationService conversationService;

    @PostMapping("/create")
    ApiResponse<ConversationResponse> createConversation(@RequestBody @Valid ConversationRequest request) {
        return ApiResponse.<ConversationResponse>builder()
                .result(conversationService.createConversation(request))
                .build();
    }

    @GetMapping("/my-conversation")
    ApiResponse<List<ConversationResponse>> getMyConversations(@RequestParam String type) {
        return ApiResponse.<List<ConversationResponse>>builder()
                .result(conversationService.getMyConversations(type))
                .build();
    }
}
