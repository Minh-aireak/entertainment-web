package com.MyProject.chat_service.mapper;

import com.MyProject.chat_service.document.ConversationDoc;
import com.MyProject.chat_service.dto.response.ConversationResponse;
import com.MyProject.chat_service.entity.ConversationDirect;
import com.MyProject.chat_service.entity.ConversationGroup;
import org.mapstruct.Mapper;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface ConversationMapper {
    ConversationResponse toConversationDirectResponse(ConversationDirect conversationDirect);
    ConversationResponse toConversationGroupResponse(ConversationGroup conversationGroup);

    ConversationDoc toConversationDoc(ConversationGroup group);

    ConversationDoc toConversationDoc(ConversationDirect direct);

}
