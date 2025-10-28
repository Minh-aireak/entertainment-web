package com.MyProject.chat_service.service;


import com.MyProject.chat_service.dto.request.ConversationRequest;
import com.MyProject.chat_service.dto.response.ConversationResponse;
import com.MyProject.chat_service.dto.response.UserProfileResponse;
import com.MyProject.chat_service.entity.*;
import com.MyProject.chat_service.exception.AppException;
import com.MyProject.chat_service.exception.ErrorCode;
import com.MyProject.chat_service.mapper.ConversationMapper;
import com.MyProject.chat_service.repository.ConversationDirectRepository;
import com.MyProject.chat_service.repository.ConversationGroupRepository;
import com.MyProject.chat_service.repository.ConversationRepository;
import com.MyProject.chat_service.repository.httpclient.ProfileClient;
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

    private Conversation baseConversation(ConversationRequest request) {
        return Conversation.builder()
                .id(UUID.randomUUID().toString())
                .type(ConversationType.valueOf(request.getType()))
                .createdDate(Instant.now())
                .modifiedDate(Instant.now())
                .build();
    }

    private ConversationResponse toConversationResponse(Conversation conversation) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Jwt jwt = ((JwtAuthenticationToken) authentication).getToken();
        String currentUserId = jwt.getClaim("userId");

        ConversationResponse conversationResponse;
        if (conversation instanceof ConversationDirect conversationDirect) {
            conversationResponse = conversationMapper.toConversationDirectResponse(conversationDirect);

            conversationResponse.getParticipantInfos().stream()
                    .filter(participantInfo -> !participantInfo.getUserId().equals(currentUserId))
                    .findFirst().ifPresent(participantInfo -> {
                        conversationResponse.setDirectName(participantInfo.getDisplayName());
                        conversationResponse.setDirectAvatar(participantInfo.getAvatar());
                    });
        } else if (conversation instanceof ConversationGroup conversationGroup) {
            conversationResponse = conversationMapper.toConversationGroupResponse(conversationGroup);
        } else {
            throw new AppException(ErrorCode.TYPE_CONVERSATION_ERROR);
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
    public ConversationResponse createConversation(ConversationRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Jwt jwt = ((JwtAuthenticationToken) authentication).getToken();
        String userId = jwt.getClaim("userId");

        var userProfileResponse = profileClient.getUserProfile(userId).getResult();

        if (request.getParticipantInfos().isEmpty()) {
            return null;
        } else if (request.getType().equals("DIRECT")) {
            UserProfileResponse participantInfoResponse = profileClient.getUserProfile(request.getParticipantInfos().getFirst().getUserId()).getResult();
            List<String> ids = new ArrayList<>();
            ids.add(userProfileResponse.getUserId());
            ids.add(participantInfoResponse.getUserId());

            var sortedIds = ids.stream().sorted().toList();
            String userIdsHash = generateParticipantsHash(sortedIds);
            var conversation = conversationDirectRepository.findByParticipantsHash(userIdsHash)
                    .orElseGet(() -> {
                        List<ParticipantInfo> participantInfos = List.of(
                                ParticipantInfo.builder()
                                        .userId(userId)
                                        .displayName(userProfileResponse.getDisplayName())
                                        .avatar(userProfileResponse.getAvatar())
                                        .build(),
                                ParticipantInfo.builder()
                                        .userId(participantInfoResponse.getUserId())
                                        .displayName(participantInfoResponse.getDisplayName())
                                        .avatar(participantInfoResponse.getAvatar())
                                        .build()
                        );

                        ConversationDirect conversationDirect = ConversationDirect
                                .fromConversation(baseConversation(request))
                                .participantInfos(participantInfos)
                                .participantsHash(userIdsHash)
                                .build();

                        conversationDirect = conversationDirectRepository.save(conversationDirect);
                        return conversationDirect;
                    });

            return toConversationResponse(conversation);
        } else {
            List<ParticipantInfo> participantsInfo = request.getParticipantInfos().stream().map(s -> {
                var response = profileClient.getUserProfile(s.getUserId()).getResult();
                return ParticipantInfo.builder()
                        .userId(response.getUserId())
                        .displayName(response.getDisplayName())
                        .avatar(response.getAvatar())
                        .build();
            }).collect(Collectors.toList());

            List<ParticipantInfo> listParticipantInfo = new ArrayList<>();
            listParticipantInfo.add(ParticipantInfo.builder()
                    .userId(userId)
                    .displayName(userProfileResponse.getDisplayName())
                    .avatar(userProfileResponse.getAvatar())
                    .build());
            listParticipantInfo.addAll(participantsInfo);

            ConversationGroup conversationGroup = ConversationGroup
                    .fromConversation(baseConversation(request))
                    .participantInfos(listParticipantInfo)
                    .groupName(createGroupName(participantsInfo))
                    .groupOwner(userId)
                    .groupAvatar("")
                    .build();

            conversationGroup = conversationGroupRepository.save(conversationGroup);

            return toConversationResponse(conversationGroup);
        }
    }

    @Transactional
    public List<ConversationResponse> getMyConversations() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Jwt jwt = ((JwtAuthenticationToken) authentication).getToken();
        String userId = jwt.getClaim("userId");

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
