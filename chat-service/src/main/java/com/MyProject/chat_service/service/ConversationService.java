package com.MyProject.chat_service.service;

import com.MyProject.chat_service.dto.request.BulkUserProfileRequest;
import com.MyProject.chat_service.dto.response.ConversationResponse;
import com.MyProject.chat_service.entity.*;
import com.MyProject.chat_service.exception.AppException;
import com.MyProject.chat_service.exception.ErrorCode;
import com.MyProject.chat_service.mapper.ConversationMapper;
import com.MyProject.chat_service.repository.ConversationDirectRepository;
import com.MyProject.chat_service.repository.ConversationGroupRepository;
import com.MyProject.chat_service.repository.ConversationRepository;
import com.MyProject.chat_service.repository.httpclient.ProfileClient;
import com.MyProject.common_dto.event.dto.UserProfileResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ConversationService {
    ProfileClient profileClient;
    ConversationMapper conversationMapper;
    ConversationRepository conversationRepository;
    ConversationDirectRepository conversationDirectRepository;
    ConversationGroupRepository conversationGroupRepository;

    private String getUserId(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        Jwt jwt = ((JwtAuthenticationToken) authentication).getToken();
        return jwt.getClaim("userId");
    }

    private Conversation baseConversation(String type) {
        return Conversation.builder()
                .id(UUID.randomUUID().toString())
                .type(ConversationType.valueOf(type))
                .createdDate(Instant.now())
                .modifiedDate(Instant.now())
                .build();
    }

    private ConversationResponse toConversationResponse(Conversation conversation) {
        ConversationResponse conversationResponse;
        switch (conversation) {
            case ConversationDirect conversationDirect -> {
                conversationResponse = conversationMapper.toConversationDirectResponse(conversationDirect);

                conversationResponse.getParticipantInfos().stream()
                        .filter(participantInfo -> !participantInfo.getUserId().equals(getUserId()))
                        .findFirst().ifPresent(participantInfo -> {
                            conversationResponse.setDirectName(participantInfo.getDisplayName());
                            conversationResponse.setDirectAvatar(participantInfo.getAvatar());
                        });
            }
            case ConversationGroup conversationGroup -> conversationResponse = conversationMapper.toConversationGroupResponse(conversationGroup);
            default -> throw new AppException(ErrorCode.TYPE_CONVERSATION_ERROR);
        }

        return conversationResponse;
    }

    private String createGroupName(List<ParticipantInfo> participantsInfo) {
        StringBuilder baseName = new StringBuilder();
        int limit = Math.min(3, participantsInfo.size());

        for (int i = 0; i < limit; i++) {
            String name = participantsInfo.get(i).getDisplayName();
            baseName.append(", ").append(name);
        }

        if (participantsInfo.size() > 3) {
            int more = participantsInfo.size() - 3;
            baseName.append(" + ").append(more).append(" more");
        }

        return baseName.toString();
    }

    @Transactional
    public ConversationResponse createConversation(List<String> ids) {
        String currentId = getUserId();
        ids.addFirst(currentId);

        List<UserProfileResponse> listUserResponse = profileClient.getBulkUserProfiles(BulkUserProfileRequest.builder()
                        .userIds(ids)
                .build()).getResult();

        if (ids.isEmpty()) {
            return null;
        } else if (ids.size() == 2) {
            List<String> sortedIds = listUserResponse.stream().map(UserProfileResponse::getUserId).sorted().toList();

            String userIdsHash = generateParticipantsHash(sortedIds);
            var conversation = conversationDirectRepository.findByParticipantsHash(userIdsHash)
                    .orElseGet(() -> {
                        List<ParticipantInfo> participantInfos = List.of(
                                ParticipantInfo.builder()
                                        .userId(listUserResponse.getFirst().getUserId())
                                        .displayName(listUserResponse.getFirst().getDisplayName())
                                        .avatar(listUserResponse.getFirst().getAvatar())
                                        .build(),
                                ParticipantInfo.builder()
                                        .userId(listUserResponse.getLast().getUserId())
                                        .displayName(listUserResponse.getLast().getDisplayName())
                                        .avatar(listUserResponse.getLast().getAvatar())
                                        .build()
                        );

                        ConversationDirect conversationDirect = ConversationDirect
                                .fromConversation(baseConversation("DIRECT"))
                                .participantInfos(participantInfos)
                                .participantsHash(userIdsHash)
                                .build();

                        conversationDirect = conversationDirectRepository.save(conversationDirect);
                        return conversationDirect;
                    });

            return toConversationResponse(conversation);
        } else {
            List<ParticipantInfo> participantsInfo = listUserResponse.stream().map(s ->
                ParticipantInfo.builder()
                        .userId(s.getUserId())
                        .displayName(s.getDisplayName())
                        .avatar(s.getAvatar())
                        .build()
            ).collect(Collectors.toList());

            ConversationGroup conversationGroup = ConversationGroup
                    .fromConversation(baseConversation("GROUP"))
                    .participantInfos(participantsInfo)
                    .groupName(createGroupName(participantsInfo))
                    .groupOwner(currentId)
                    .groupAvatar("")
                    .build();

            conversationGroup = conversationGroupRepository.save(conversationGroup);

            return toConversationResponse(conversationGroup);
        }
    }

    @Transactional
    public List<ConversationResponse> getMyConversations() {
        String userId = getUserId();

        return conversationRepository.findAllByUserId(userId)
                .stream()
                .map(a -> {
                    ConversationResponse response = null;
                    if (a instanceof ConversationDirect conversationDirect) {
                        response = toConversationResponse(conversationDirect);
                    } else if (a instanceof ConversationGroup conversationGroup){
                        response = toConversationResponse(conversationGroup);
                    }
                    return response;
                })
                .toList();
    }

    private String generateParticipantsHash(List<String> ids) {
        StringJoiner stringJoiner  = new StringJoiner("_");
        ids.forEach(stringJoiner::add);
        return stringJoiner.toString();
    }
}
