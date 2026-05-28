package com.MyProject.chat_service.repository.mongo;

import com.MyProject.chat_service.entity.Conversation;

public interface ConversationRepositoryCustom {
    Conversation incrementSeqAndUpdateLastMessage(String conversationId, String content);
    void updateLastMessage(String conversationId, String content);
    void deleteLastMessage(String conversationId, String content);
}
