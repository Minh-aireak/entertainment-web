package com.MyProject.chat_service.controller;

import com.MyProject.chat_service.service.ChatApiRateLimitService;
import com.MyProject.common.security.SecurityUtils;
import com.MyProject.common.dto.response.ApiResponse;
import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.chat_service.dto.request.ConversationUpdateRequest;
import com.MyProject.chat_service.dto.response.ConversationResponse;
import com.MyProject.chat_service.service.ConversationService;
import com.mongodb.MongoException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/conversations")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ConversationController {
    static final int CREATE_CONVERSATION_ATTEMPTS = 3;
    static final long CREATE_CONVERSATION_RETRY_DELAY_MS = 50;
    static final int MONGO_DUPLICATE_KEY = 11000;
    static final int MONGO_WRITE_CONFLICT = 112;

    ConversationService conversationService;
    ChatApiRateLimitService chatApiRateLimitService;

    @PostMapping
    ApiResponse<ConversationResponse> createConversation(@RequestBody List<String> ids) {
        String userId = SecurityUtils.getCurrentUserId();
        chatApiRateLimitService.checkConversationWrite(userId);
        return ApiResponse.<ConversationResponse>builder()
                .result(createConversationRetryingOnRace(ids))
                .build();
    }

    // Two "find or create" calls for the same new pair can race; the unique participantsHash index (see
    // ConversationIndexInitializer) makes the loser's transaction fail instead of inserting a second
    // conversation. Retrying in a fresh transaction - each call goes through the @Transactional proxy -
    // then finds the conversation the winner created, so both callers get the same one.
    private ConversationResponse createConversationRetryingOnRace(List<String> ids) {
        for (int attempt = 1; ; attempt++) {
            try {
                return conversationService.createConversationForApi(ids);
            } catch (RuntimeException e) {
                if (attempt >= CREATE_CONVERSATION_ATTEMPTS || !isConcurrentCreateConflict(e)) {
                    throw e;
                }
                try {
                    Thread.sleep(CREATE_CONVERSATION_RETRY_DELAY_MS * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    // Duplicate key once the winner has committed; WriteConflict / TransientTransactionError while it's
    // still in flight (surfaced wrapped, e.g. in a TransactionSystemException from the commit).
    static boolean isConcurrentCreateConflict(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof DuplicateKeyException) {
                return true;
            }
            if (cause instanceof MongoException mongoException
                    && (mongoException.getCode() == MONGO_DUPLICATE_KEY
                        || mongoException.getCode() == MONGO_WRITE_CONFLICT
                        || mongoException.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL))) {
                return true;
            }
        }
        return false;
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
