package com.MyProject.chat_service.service;

import com.MyProject.chat_service.document.ConversationDoc;
import com.MyProject.chat_service.dto.response.ConversationResponse;
import com.MyProject.chat_service.dto.response.ParticipantResponse;
import com.MyProject.chat_service.entity.*;
import com.MyProject.chat_service.enums.ConversationType;
import com.MyProject.chat_service.exception.AppException;
import com.MyProject.chat_service.enums.ErrorCode;
import com.MyProject.chat_service.mapper.ConversationMapper;
import com.MyProject.chat_service.repository.elasticsearch.ConversationElasticRepository;
import com.MyProject.chat_service.repository.mongo.*;
import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.common.dto.response.UserProfileResponse;
import com.MyProject.common.redis.RedisService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
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
    ChatProfileExternalService chatProfileExternalService;
    ConversationMapper conversationMapper;
    ConversationRepository conversationRepository;
    ConversationDirectRepository conversationDirectRepository;
    ConversationGroupRepository conversationGroupRepository;
    ConversationMemberRepository conversationMemberRepository;
    ConversationElasticRepository conversationElasticRepository;
    RedisService redisService;
    OutboxRepository outboxRepository;
    ObjectMapper objectMapper;

    private void saveToOutbox(String aggregateId, String topic, Object payload) {
        try {
            outboxRepository.save(Outbox.builder()
                    .aggregateId(aggregateId)
                    .topic(topic)
                    .payload(objectMapper.writeValueAsString(payload))
                    .build());
        } catch (Exception e) {
            log.error("Failed to save to outbox for topic {}", topic, e);
            throw new AppException(ErrorCode.OUTBOX_SAVE_FAILED);
        }
    }

    private String getUserId(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken)) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
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
        return fetchProfiles(userIds, false);
    }

    private Map<String, UserProfileResponse> fetchProfiles(Set<String> userIds, boolean internalFlow) {
        if (userIds.isEmpty()) return Collections.emptyMap();

        List<String> userIdList = new ArrayList<>(userIds);
        List<String> keys = userIdList.stream()
                .map(this::getProfileCacheKey)
                .toList();

        // 1. MultiGet Redis 1 lần
        List<UserProfileResponse> cachedValues = Collections.nCopies(keys.size(), null);
        try {
            cachedValues = redisService.multiGet(keys, new TypeReference<UserProfileResponse>() {});
        } catch (Exception e) {
            log.error("Failed to multiGet from cache for keys: {}", keys, e);
        }

        Map<String, UserProfileResponse> result = new HashMap<>();
        List<String> missingIds = new ArrayList<>();

        for (int i = 0; i < userIdList.size(); i++) {
            UserProfileResponse cached = (i < cachedValues.size()) ? cachedValues.get(i) : null;
            if (Objects.nonNull(cached)) {
                result.put(userIdList.get(i), cached);
            } else {
                missingIds.add(userIdList.get(i));
            }
        }

        // 2. Fetch missing từ Profile Service
        if (!missingIds.isEmpty()) {
            try {
                Map<String, UserProfileResponse> fetchedProfiles =
                        internalFlow
                                ? chatProfileExternalService.getBulkUserProfilesForInternal(new HashSet<>(missingIds))
                                : chatProfileExternalService.getBulkUserProfilesForApi(new HashSet<>(missingIds));

                if (fetchedProfiles != null) {
                    fetchedProfiles.forEach((userId, profile) -> {
                        result.put(userId, profile);
                        try {
                            redisService.setWithExpiration(
                                    getProfileCacheKey(userId), profile, 1, TimeUnit.HOURS);
                        } catch (Exception e) {
                            log.error("Failed to set cache for userId: {}", userId, e);
                        }
                    });
                }
            } catch (Exception e) {
                if (internalFlow) {
                    throw e;
                }
                log.error("Failed to fetch bulk profiles for ids: {}", missingIds, e);
            }
        }

        return result;
    }

    private void validateResolvedProfiles(List<String> userIds, Map<String, UserProfileResponse> profileMap) {
        if (profileMap.size() != userIds.size()) {
            throw new AppException(ErrorCode.INVALID_CONVERSATION_PARTICIPANTS);
        }
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

    private String buildConversationNameForDocument(Conversation conversation, Map<String, UserProfileResponse> profileMap) {
        if (conversation instanceof ConversationGroup group) {
            return group.getGroupName();
        }

        LinkedHashSet<String> names = conversation.getUserIds().stream()
                .map(profileMap::get)
                .filter(Objects::nonNull)
                .map(profile -> profile.getDisplayName() != null && !profile.getDisplayName().isBlank()
                        ? profile.getDisplayName()
                        : profile.getUserId())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (names.isEmpty()) {
            names.addAll(conversation.getUserIds());
        }

        return String.join(", ", names);
    }

    private String buildConversationAvatarForDocument(Conversation conversation, Map<String, UserProfileResponse> profileMap) {
        if (conversation instanceof ConversationGroup group) {
            return group.getGroupAvatar();
        }

        return conversation.getUserIds().stream()
                .map(profileMap::get)
                .filter(Objects::nonNull)
                .map(UserProfileResponse::getAvatar)
                .filter(Objects::nonNull)
                .filter(avatar -> !avatar.isBlank())
                .findFirst()
                .orElse(null);
    }

    private String resolveConversationNameForUser(
            Conversation conversation,
            String currentUserId,
            Map<String, UserProfileResponse> profileMap
    ) {
        if (conversation instanceof ConversationGroup group) {
            return group.getGroupName();
        }

        return conversation.getUserIds().stream()
                .filter(id -> !id.equals(currentUserId))
                .findFirst()
                .map(profileMap::get)
                .map(profile -> profile.getDisplayName() != null && !profile.getDisplayName().isBlank()
                        ? profile.getDisplayName()
                        : profile.getUserId())
                .orElseGet(() -> buildConversationNameForDocument(conversation, profileMap));
    }

    private String resolveConversationAvatarForUser(
            Conversation conversation,
            String currentUserId,
            Map<String, UserProfileResponse> profileMap
    ) {
        if (conversation instanceof ConversationGroup group) {
            return group.getGroupAvatar();
        }

        return conversation.getUserIds().stream()
                .filter(id -> !id.equals(currentUserId))
                .findFirst()
                .map(profileMap::get)
                .map(UserProfileResponse::getAvatar)
                .orElse(null);
    }

    private ConversationDoc buildConversationDoc(Conversation conversation, Map<String, UserProfileResponse> profileMap) {
        ConversationDoc.ConversationDocBuilder builder = ConversationDoc.builder()
                .id(conversation.getId())
                .type(conversation.getType().name())
                .userIds(conversation.getUserIds())
                .lastMessage(conversation.getLastMessage())
                .deleted(conversation.isDeleted())
                .conversationName(buildConversationNameForDocument(conversation, profileMap))
                .conversationAvatar(buildConversationAvatarForDocument(conversation, profileMap));

        return builder.build();
    }

    private ConversationResponse toSearchConversationResponse(
            Conversation conversation,
            String currentUserId,
            Map<String, UserProfileResponse> profileMap
    ) {
        ConversationResponse.ConversationResponseBuilder builder = ConversationResponse.builder()
                .id(conversation.getId())
                .type(conversation.getType().name())
                .userIds(conversation.getUserIds())
                .totalSeq(conversation.getTotalSeq())
                .createdDate(conversation.getCreatedDate())
                .modifiedDate(conversation.getModifiedDate())
                .lastMessage(conversation.getLastMessage())
                .deleted(conversation.isDeleted())
                .conversationName(resolveConversationNameForUser(conversation, currentUserId, profileMap))
                .conversationAvatar(resolveConversationAvatarForUser(conversation, currentUserId, profileMap));

        if (conversation instanceof ConversationDirect direct) {
            builder.participantsHash(direct.getParticipantsHash());
        } else if (conversation instanceof ConversationGroup group) {
            builder.groupOwner(group.getGroupOwner());
        }

        return builder.build();
    }

    private ConversationResponse toSearchConversationResponse(
            ConversationDoc doc,
            String currentUserId,
            Map<String, UserProfileResponse> profileMap
    ) {
        ConversationResponse.ConversationResponseBuilder builder = ConversationResponse.builder()
                .id(doc.getId())
                .type(doc.getType())
                .userIds(doc.getUserIds())
                .conversationName(doc.getConversationName())
                .conversationAvatar(doc.getConversationAvatar())
                .lastMessage(doc.getLastMessage())
                .deleted(doc.isDeleted());

        if (ConversationType.DIRECT.name().equals(doc.getType()) && doc.getUserIds() != null) {
            doc.getUserIds().stream()
                    .filter(id -> !id.equals(currentUserId))
                    .findFirst()
                    .map(profileMap::get)
                    .ifPresent(profile -> {
                        builder.conversationName(profile.getDisplayName());
                        builder.conversationAvatar(profile.getAvatar());
                    });
        }

        return builder.build();
    }

    private PageResponse<ConversationResponse> searchConversationsFromMongo(
            String userId,
            String query,
            int page,
            int size
    ) {
        List<Conversation> matched = findMatchedConversationsFromMongo(userId, query);

        return pageSearchConversations(matched, userId, page, size);
    }

    private List<Conversation> findMatchedConversationsFromMongo(String userId, String query) {
        List<Conversation> conversations = conversationRepository.findByUserIdsContaining(userId);
        Set<String> allUserIds = conversations.stream()
                .flatMap(conversation -> conversation.getUserIds().stream())
                .collect(Collectors.toSet());
        Map<String, UserProfileResponse> profileMap = fetchProfiles(allUserIds);
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);

        return conversations.stream()
                .filter(conversation -> buildConversationNameForDocument(conversation, profileMap)
                        .toLowerCase(Locale.ROOT)
                        .contains(normalizedQuery))
                .sorted(Comparator.comparing(
                        Conversation::getModifiedDate,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ).reversed())
                .toList();
    }

    private PageResponse<ConversationResponse> pageSearchConversations(
            List<Conversation> matched,
            String userId,
            int page,
            int size
    ) {
        Set<String> allUserIds = matched.stream()
                .flatMap(conversation -> conversation.getUserIds().stream())
                .collect(Collectors.toSet());
        Map<String, UserProfileResponse> profileMap = fetchProfiles(allUserIds);

        int fromIndex = Math.max(0, (page - 1) * size);
        int toIndex = Math.min(matched.size(), fromIndex + size);
        List<ConversationResponse> pagedData = fromIndex >= matched.size()
                ? List.of()
                : matched.subList(fromIndex, toIndex).stream()
                .map(conversation -> toSearchConversationResponse(conversation, userId, profileMap))
                .toList();

        int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) matched.size() / size);

        return PageResponse.<ConversationResponse>builder()
                .currentPage(page)
                .pageSize(size)
                .totalPages(totalPages)
                .totalElement(matched.size())
                .data(pagedData)
                .build();
    }

    @Transactional
    public ConversationResponse createConversationForApi(List<String> ids) {
        String currentId = getUserId();

        if (ids == null) {
            throw new AppException(ErrorCode.INVALID_CONVERSATION_PARTICIPANTS);
        }

        List<String> uniqueIds = ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (uniqueIds.size() < 2) {
            throw new AppException(ErrorCode.INVALID_CONVERSATION_PARTICIPANTS);
        }

        if (!uniqueIds.contains(currentId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        return createConversationInternal(uniqueIds, currentId, false);
    }

    @Transactional
    public ConversationResponse createConversationFromEvent(List<String> ids) {
        return createConversationInternal(ids, null, true);
    }

    private ConversationResponse createConversationInternal(List<String> ids, String currentId, boolean internalFlow) {

        if (ids == null || ids.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_CONVERSATION_PARTICIPANTS);
        }

        List<String> uniqueIds = ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (uniqueIds.size() < 2) {
            throw new AppException(ErrorCode.INVALID_CONVERSATION_PARTICIPANTS);
        }

        // 1. SORT IDS (important for hash consistency)
        List<String> sortedIds = new ArrayList<>(uniqueIds);
        Collections.sort(sortedIds);

        // 2. FETCH PROFILES (Enriched from Redis/Service)
        Map<String, UserProfileResponse> profileMap = fetchProfiles(new HashSet<>(sortedIds), internalFlow);
        validateResolvedProfiles(sortedIds, profileMap);

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
                        saveToOutbox(saved.getId(), "conversation.sync", buildConversationDoc(saved, profileMap));
                        return saved;
                    });
        } else {
            // 4. GROUP CHAT (> 2 participants)
            conversation = ConversationGroup
                    .fromConversation(baseConversation("GROUP", sortedIds))
                    .groupName(createGroupName(profileMap.values()))
                    .groupOwner(currentId != null ? currentId : sortedIds.get(0))
                    .groupAvatar("") // Can be updated later or set default
                    .build();

            conversation = conversationGroupRepository.save((ConversationGroup) conversation);
            saveToOutbox(conversation.getId(), "conversation.sync", buildConversationDoc(conversation, profileMap));
        }

        // Tạo thành viên nếu chưa có
        createMembersIfNotExist(conversation);

        // CACHE result
        try {
            redisService.setWithExpiration(
                    getConversationCacheKey(conversation.getId()),
                    conversation.getId(),
                    12,
                    TimeUnit.HOURS
            );
        } catch (Exception e) {
            log.error("Failed to cache conversation ID: {}", conversation.getId(), e);
        }

        ConversationResponse response = toConversationResponse(conversation);
        
        // Map thông tin thành viên kèm lastSeen
        enrichParticipants(response, conversation.getUserIds(), profileMap);

        if (conversation instanceof ConversationDirect) {
            // Enrich with "other" user info
            if (currentId != null) {
                response.setConversationName(resolveConversationNameForUser(conversation, currentId, profileMap));
                response.setConversationAvatar(resolveConversationAvatarForUser(conversation, currentId, profileMap));
            }
        } else {
            ConversationGroup group = (ConversationGroup) conversation;
            response.setConversationName(group.getGroupName());
            response.setConversationAvatar(group.getGroupAvatar());
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

    public void syncConversationDocumentsForUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }

        List<Conversation> conversations = conversationRepository.findByUserIdsContaining(userId);
        if (conversations.isEmpty()) {
            return;
        }

        Set<String> allUserIds = conversations.stream()
                .flatMap(conversation -> conversation.getUserIds().stream())
                .collect(Collectors.toSet());

        Map<String, UserProfileResponse> profileMap = fetchProfiles(allUserIds, true);
        validateResolvedProfiles(new ArrayList<>(allUserIds), profileMap);
        List<ConversationDoc> docs = conversations.stream()
                .map(conversation -> buildConversationDoc(conversation, profileMap))
                .toList();

        conversationElasticRepository.saveAll(docs);
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
                        response.setConversationName(resolveConversationNameForUser(a, userId, profileMap));
                        response.setConversationAvatar(resolveConversationAvatarForUser(a, userId, profileMap));
                    } else if (a instanceof ConversationGroup group) {
                        // Logic cho GROUP: Lấy thông tin hard-coded trong Entity
                        response.setConversationName(group.getGroupName());
                        response.setConversationAvatar(group.getGroupAvatar());
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

    @CircuitBreaker(name = "conversationSearchApi", fallbackMethod = "searchConversationsFallback")
    public PageResponse<ConversationResponse> searchConversations(String query, int page, int size) {
        String userId = getUserId();
        var searchResult = conversationElasticRepository.searchConversations(userId, query, Pageable.unpaged());

        List<Conversation> mongoMatched = findMatchedConversationsFromMongo(userId, query);
        LinkedHashMap<String, Conversation> combined = mongoMatched.stream()
                .collect(Collectors.toMap(
                        Conversation::getId,
                        conversation -> conversation,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        Set<String> esConversationIds = searchResult.getContent().stream()
                .map(ConversationDoc::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (!esConversationIds.isEmpty()) {
            conversationRepository.findAllById(esConversationIds).forEach(conversation ->
                    combined.putIfAbsent(conversation.getId(), conversation)
            );
        }

        List<Conversation> merged = combined.values().stream()
                .sorted(Comparator.comparing(
                        Conversation::getModifiedDate,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ).reversed())
                .toList();

        return pageSearchConversations(merged, userId, page, size);
    }

    public PageResponse<ConversationResponse> searchConversationsFallback(
            String query,
            int page,
            int size,
            Throwable throwable
    ) {
        if (throwable instanceof AppException appException) {
            throw appException;
        }
        log.warn("Fallback to Mongo conversation search because Elasticsearch search is unavailable.", throwable);
        return searchConversationsFromMongo(getUserId(), query, page, size);
    }

    private AppException buildSearchFallbackException(String action, Throwable throwable) {
        if (throwable instanceof AppException appException) {
            return appException;
        }
        if (throwable instanceof CallNotPermittedException) {
            log.warn("Circuit breaker is open while trying to {}.", action);
            return new AppException(ErrorCode.SERVICE_UNAVAILABLE);
        }
        log.error("Fallback triggered while trying to {}.", action, throwable);
        return new AppException(ErrorCode.SERVICE_UNAVAILABLE);
    }
}
