package com.MyProject.chat_service.service;

import com.MyProject.chat_service.dto.response.ConversationResponse;
import com.MyProject.chat_service.dto.response.ParticipantResponse;
import com.MyProject.chat_service.entity.*;
import com.MyProject.chat_service.exception.AppException;
import com.MyProject.chat_service.exception.ErrorCode;
import com.MyProject.chat_service.mapper.ConversationMapper;
import com.MyProject.chat_service.repository.elasticsearch.ConversationElasticRepository;
import com.MyProject.chat_service.repository.httpclient.ProfileClient;
import com.MyProject.chat_service.repository.mongo.*;
import com.MyProject.common.dto.request.BulkUserProfileRequest;
import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.common.dto.response.UserProfileResponse;
import com.MyProject.common.redis.RedisService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;
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
    ConversationMemberRepository conversationMemberRepository;
    ConversationElasticRepository conversationElasticRepository;
    RedisService redisService;
    OutboxRepository outboxRepository;
    com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private void saveToOutbox(String aggregateId, String topic, Object payload) {
        try {
            outboxRepository.save(Outbox.builder()
                            .aggregateId(aggregateId)
                    .topic(topic)
                    .payload(objectMapper.writeValueAsString(payload))
                    .build());
        } catch (Exception e) {
            log.error("Failed to save to outbox", e);
        }
    }

    private String getUserId(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        Jwt jwt = ((JwtAuthenticationToken) authentication).getToken();
        return jwt.getClaim("userId");
    }

    private String getProfileCacheKey(String userId) {
        return "profile:user:" + userId;
    }

    private String getLastMessageCacheKey(String conversationId) {
        return "chat:last-message:" + conversationId;
    }

    private Conversation baseConversation(String type, List<String> userIds) {
        return Conversation.builder()
                .type(ConversationType.valueOf(type))
                .totalSeq(0L)
                .userIds(userIds)
                .build();
    }

    private ConversationResponse toConversationResponse(Conversation conversation) {
        return switch (conversation) {
            case ConversationDirect direct -> conversationMapper.toConversationDirectResponse(direct);
            case ConversationGroup group -> conversationMapper.toConversationGroupResponse(group);
            default -> throw new AppException(ErrorCode.TYPE_CONVERSATION_ERROR);
        };
    }

    private Map<String, UserProfileResponse> fetchProfiles(Set<String> userIds) {
        if (userIds.isEmpty()) return Collections.emptyMap();

        List<String> userIdList = new ArrayList<>(userIds);
        List<String> keys = userIdList.stream()
                .map(this::getProfileCacheKey)
                .toList();

        // 1. MultiGet Redis 1 lần
        List<Object> cachedValues = redisService.multiGet(keys);

        Map<String, UserProfileResponse> result = new HashMap<>();
        List<String> missingIds = new ArrayList<>();

        for (int i = 0; i < userIdList.size(); i++) {
            Object cached = cachedValues.get(i);
            if (Objects.nonNull(cached)) {
                result.put(userIdList.get(i), (UserProfileResponse) cached);
            } else {
                missingIds.add(userIdList.get(i));
            }
        }

        // 2. Fetch missing từ Profile Service
        if (!missingIds.isEmpty()) {
            try {
                Map<String, UserProfileResponse> fetchedProfiles = profileClient.getBulkUserProfiles(
                        BulkUserProfileRequest.builder()
                                .userIds(new HashSet<>(missingIds))
                                .build()
                ).getResult();

                if (fetchedProfiles != null) {
                    fetchedProfiles.forEach((userId, profile) -> {
                        result.put(userId, profile);
                        redisService.setWithExpiration(
                                getProfileCacheKey(userId), profile, 1, TimeUnit.HOURS);
                    });
                }
            } catch (Exception e) {
                log.error("Failed to fetch bulk profiles for ids: {}", missingIds, e);
            }
        }

        return result;
    }

    private String createGroupName(Collection<UserProfileResponse> profiles) {
        if (profiles == null || profiles.isEmpty()) {
            return "Group Chat";
        }

        List<String> names = profiles.stream()
                .limit(3)
                .map(p -> p.getDisplayName() != null ? p.getDisplayName() : "Unknown")
                .toList();

        String joinedNames = String.join(", ", names);
        StringBuilder baseName = new StringBuilder(joinedNames);

        if (profiles.size() > 3) {
            int more = profiles.size() - 3;
            baseName.append(" + ").append(more).append(" more");
        }

        return baseName.toString();
    }

    private String getConversationCacheKey(String conversationId) {
        return "chat:conversation:" + conversationId;
    }

    @Transactional
    public ConversationResponse createConversation(List<String> ids) {
        String currentId = getUserId();

        if (ids == null || ids.isEmpty()) {
            return null;
        }

        // 1. SORT IDS (important for hash consistency)
        List<String> sortedIds = new ArrayList<>(ids);
        Collections.sort(sortedIds);

        // 2. FETCH PROFILES (Enriched from Redis/Service)
        Map<String, UserProfileResponse> profileMap = fetchProfiles(new HashSet<>(sortedIds));

        Conversation conversation;
        // 3. DIRECT CHAT (2 participants)
        if (sortedIds.size() == 2) {
            String userIdsHash = generateParticipantsHash(sortedIds);

            conversation = conversationDirectRepository
                    .findByParticipantsHash(userIdsHash)
                    .orElseGet(() -> {
                        ConversationDirect newConversation = ConversationDirect
                                .fromConversation(baseConversation("DIRECT", sortedIds))
                                .participantsHash(userIdsHash)
                                .build();

                        ConversationDirect saved = conversationDirectRepository.save(newConversation);
                        saveToOutbox(saved.getId(), "chat.conversation.created", conversationMapper.toConversationDoc(saved));
                        return saved;
                    });
        } else {
            // 4. GROUP CHAT (> 2 participants)
            conversation = ConversationGroup
                    .fromConversation(baseConversation("GROUP", sortedIds))
                    .groupName(createGroupName(profileMap.values()))
                    .groupOwner(currentId)
                    .groupAvatar("") // Can be updated later or set default
                    .build();

            conversation = conversationGroupRepository.save((ConversationGroup) conversation);
            saveToOutbox(conversation.getId(), "chat.conversation.created", conversationMapper.toConversationDoc((ConversationGroup) conversation));
        }

        // Tạo thành viên nếu chưa có
        createMembersIfNotExist(conversation);

        // CACHE result
        redisService.setWithExpiration(
                getConversationCacheKey(conversation.getId()),
                conversation.getId(),
                12,
                TimeUnit.HOURS
        );

        ConversationResponse response = toConversationResponse(conversation);
        
        // Map thông tin thành viên kèm lastSeen
        enrichParticipants(response, conversation.getUserIds(), profileMap);

        if (conversation instanceof ConversationDirect) {
            // Enrich with "other" user info
            sortedIds.stream()
                    .filter(id -> !id.equals(currentId))
                    .findFirst()
                    .ifPresent(otherId -> {
                        UserProfileResponse p = profileMap.get(otherId);
                        if (p != null) {
                            response.setDirectName(p.getDisplayName());
                            response.setDirectAvatar(p.getAvatar());
                        }
                    });
        } else {
            ConversationGroup group = (ConversationGroup) conversation;
            response.setGroupName(group.getGroupName());
            response.setGroupAvatar(group.getGroupAvatar());
            response.setGroupOwner(group.getGroupOwner());
        }

        return response;
    }

    private void createMembersIfNotExist(Conversation conversation) {
        for (String uid : conversation.getUserIds()) {
            if (!conversationMemberRepository.existsByConversationIdAndUserId(conversation.getId(), uid)) {
                conversationMemberRepository.save(ConversationMember.builder()
                        .conversationId(conversation.getId())
                        .userId(uid)
                        .lastSeenSeq(0L)
                        .build());
            }
        }
    }

    private void enrichParticipants(ConversationResponse response, List<String> userIds, Map<String, UserProfileResponse> profileMap) {
        List<ConversationMember> members = conversationMemberRepository.findByConversationId(response.getId());
        List<ParticipantResponse> participants = members.stream()
                .map(m -> {
                    UserProfileResponse p = profileMap.get(m.getUserId());
                    return ParticipantResponse.builder()
                            .userId(m.getUserId())
                            .lastSeenMessageId(m.getLastSeenMessageId())
                            .displayName(p != null ? p.getDisplayName() : "")
                            .avatar(p != null ? p.getAvatar() : "")
                            .build();
                })
                .toList();
        response.setParticipants(participants);
    }

    public PageResponse<ConversationResponse> getMyConversations(int page, int size) {
        String userId = getUserId();

        Sort sort = Sort.by("modifiedDate").descending();
        Pageable pageable = PageRequest.of(page - 1, size, sort);
        
        Page<Conversation> conversationPage = conversationRepository.findAllByUserId(userId, pageable);
        List<Conversation> conversations = conversationPage.getContent();
        
        // 1. Lấy tất cả User Profile của các thành viên trong các hội thoại
        Set<String> allUserIds = conversations.stream()
                .flatMap(c -> c.getUserIds().stream())
                .collect(Collectors.toSet());

        Map<String, UserProfileResponse> profileMap = fetchProfiles(allUserIds);

        // 2. Lấy thông tin thành viên (để lấy lastSeenMessageId)
        List<String> conversationIds = conversations.stream().map(Conversation::getId).toList();
        List<ConversationMember> allMembers = conversationMemberRepository.findByConversationIdIn(conversationIds);
        Map<String, List<ConversationMember>> membersByConvId = allMembers.stream()
                .collect(Collectors.groupingBy(ConversationMember::getConversationId));

        List<ConversationResponse> data = conversations.stream()
                .map(a -> {
                    ConversationResponse response = toConversationResponse(a);

                    // Map thông tin thành viên kèm lastSeen
                    List<ConversationMember> members = membersByConvId.getOrDefault(a.getId(), Collections.emptyList());
                    List<ParticipantResponse> participants = members.stream()
                            .map(m -> {
                                UserProfileResponse p = profileMap.get(m.getUserId());
                                return ParticipantResponse.builder()
                                        .userId(m.getUserId())
                                        .lastSeenMessageId(m.getLastSeenMessageId())
                                        .displayName(p != null ? p.getDisplayName() : "")
                                        .avatar(p != null ? p.getAvatar() : "")
                                        .build();
                            })
                            .toList();
                    response.setParticipants(participants);

                    if (a instanceof ConversationDirect) {
                        // Logic cho DIRECT: Lấy từ Profile Service/Cache
                        a.getUserIds().stream()
                                .filter(id -> !id.equals(userId))
                                .findFirst()
                                .ifPresent(otherId -> {
                                    UserProfileResponse p = profileMap.get(otherId);
                                    if (p != null) {
                                        response.setDirectName(p.getDisplayName());
                                        response.setDirectAvatar(p.getAvatar());
                                    }
                                });
                    } else if (a instanceof ConversationGroup group) {
                        // Logic cho GROUP: Lấy thông tin hard-coded trong Entity
                        response.setGroupName(group.getGroupName());
                        response.setGroupAvatar(group.getGroupAvatar());
                        response.setGroupOwner(group.getGroupOwner());
                    }

                    return response;
                })
                .toList();

        return PageResponse.<ConversationResponse>builder()
                .currentPage(page)
                .pageSize(size)
                .totalPages(conversationPage.getTotalPages())
                .totalElement(conversationPage.getTotalElements())
                .data(data)
                .build();
    }

    private String generateParticipantsHash(List<String> ids) {
        StringJoiner stringJoiner  = new StringJoiner("_");
        ids.forEach(stringJoiner::add);
        return stringJoiner.toString();
    }

    public PageResponse<ConversationResponse> searchConversations(String query, int page, int size) {
        String userId = getUserId();
        Pageable pageable = PageRequest.of(page - 1, size);
        var searchResult = conversationElasticRepository.findByUserIdsContainingAndGroupNameContaining(userId, query, pageable);

        return PageResponse.<ConversationResponse>builder()
                .currentPage(page)
                .pageSize(size)
                .totalPages(searchResult.getTotalPages())
                .totalElement(searchResult.getTotalElements())
                .data(searchResult.getContent().stream()
                        .map(doc -> ConversationResponse.builder()
                                .id(doc.getId())
                                .type(doc.getType())
                                .groupName(doc.getGroupName())
                                .groupAvatar(doc.getGroupAvatar())
                                .lastMessage(doc.getLastMessage())
                                .deleted(doc.isDeleted())
                                .build())
                        .toList())
                .build();
    }
}
