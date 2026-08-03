package com.MyProject.socket_service.repository.httpclient;

import com.MyProject.common.dto.response.ApiResponse;
import com.MyProject.socket_service.configuration.AuthenticationRequestInterceptor;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;

@FeignClient(name = "socket-chat-service", url = "${app.services.chat.url}",
        configuration = {AuthenticationRequestInterceptor.class})
public interface ChatClient {
    @PutMapping(value = "/chats/messages/mark-as-seen/{conversationId}")
    ApiResponse<Void> seenAt(@PathVariable("conversationId") String conversationId);
}
