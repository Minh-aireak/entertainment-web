package com.MyProject.chat_service.mapper;

import com.MyProject.chat_service.dto.request.ChatMessageCreateRequest;
import com.MyProject.chat_service.entity.ChatMessage;
import com.MyProject.chat_service.dto.response.ChatMessageResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface ChatMessageMapper {

    @Mapping(target = "content", source = "content")
    ChatMessage toChatMessage(ChatMessageCreateRequest request);

    ChatMessageResponse toChatMessageResponse(ChatMessage chatMessage);
}
