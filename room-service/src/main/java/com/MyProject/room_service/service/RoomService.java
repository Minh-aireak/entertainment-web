package com.MyProject.room_service.service;

import com.MyProject.room_service.configuration.RoomProperties;
import com.MyProject.room_service.dto.event.RoomClosedEvent;
import com.MyProject.room_service.dto.event.RoomInviteNotificationEvent;
import com.MyProject.room_service.dto.event.RoomParticipantChangedEvent;
import com.MyProject.room_service.dto.event.RoomPlaybackChangedEvent;
import com.MyProject.room_service.dto.request.CreateRoomRequest;
import com.MyProject.room_service.dto.request.JoinRoomRequest;
import com.MyProject.room_service.dto.request.PlaybackUpdateRequest;
import com.MyProject.room_service.dto.response.RoomListItemResponse;
import com.MyProject.room_service.dto.response.RoomParticipantResponse;
import com.MyProject.room_service.dto.response.RoomResponse;
import com.MyProject.room_service.entity.Outbox;
import com.MyProject.room_service.entity.Room;
import com.MyProject.room_service.entity.RoomParticipant;
import com.MyProject.room_service.enums.ErrorCode;
import com.MyProject.room_service.enums.ParticipantRole;
import com.MyProject.room_service.enums.PlaybackAction;
import com.MyProject.room_service.enums.RoomStatus;
import com.MyProject.room_service.exception.AppException;
import com.MyProject.room_service.repository.OutboxRepository;
import com.MyProject.room_service.repository.RoomParticipantRepository;
import com.MyProject.room_service.repository.RoomRepository;
import com.MyProject.room_service.repository.httpclient.FilmClient;
import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.common.dto.response.UserProfileResponse;
import com.MyProject.common.redis.RedisService;
import com.MyProject.common.security.SecurityUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoomService {
    private static final int MAX_NAME_LENGTH = 120;
    private static final String INVITE_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Set<Double> ALLOWED_PLAYBACK_RATES = Set.of(0.5, 0.75, 1.0, 1.25, 1.5, 2.0);
    private static final int MAX_PLAYBACK_CAS_ATTEMPTS = 3;
    private static final String WATCH_LAST_SEEN_KEY_PREFIX = "presence:watch-room:last-seen:";
    private static final String WATCH_LAST_DISCONNECTED_KEY_PREFIX = "presence:watch-room:last-disconnected:";

    RoomRepository roomRepository;
    RoomParticipantRepository roomParticipantRepository;
    OutboxRepository outboxRepository;
    RoomProperties roomProperties;
    RoomProfileExternalService roomProfileExternalService;
    RoomFilmExternalService roomFilmExternalService;
    RedisService redisService;
    ObjectMapper objectMapper;
    MongoTemplate mongoTemplate;
    RoomSubscriptionTokenService roomSubscriptionTokenService;

    @Transactional(rollbackFor = Exception.class)
    public RoomResponse createRoom(CreateRoomRequest request) {
        String hostUserId = SecurityUtils.getCurrentUserId();

        if (request == null || !StringUtils.hasText(request.getFilmId())) {
            throw new AppException(ErrorCode.INVALID_FILM);
        }

        String filmId = request.getFilmId().trim();
        FilmClient.FilmInfo filmInfo = roomFilmExternalService.getFilmInfo(filmId)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_FILM));
        String episodeId = StringUtils.hasText(request.getEpisodeId()) ? request.getEpisodeId().trim() : null;
        if (episodeId != null) {
            ensureEpisodeBelongsToFilm(filmId, episodeId);
        }

        String name = StringUtils.hasText(request.getName())
                ? request.getName().trim()
                : "Xem chung: " + filmInfo.getTitle();
        if (name.codePointCount(0, name.length()) > MAX_NAME_LENGTH) {
            name = name.substring(0, MAX_NAME_LENGTH);
        }

        Set<String> invitees = request.getInviteeUserIds() == null
                ? Set.of()
                : request.getInviteeUserIds().stream()
                    .filter(id -> id != null && !id.equals(hostUserId))
                    .collect(Collectors.toSet());

        Room room = Room.builder()
                .name(name)
                .hostUserId(hostUserId)
                .filmId(filmId)
                .filmTitle(filmInfo.getTitle())
                .filmThumbnail(filmInfo.getThumbnailUrl())
                .episodeId(episodeId)
                .publicRoom(request.isPublicRoom())
                .inviteCode(generateInviteCode())
                .invitedUserIds(new HashSet<>(invitees))
                .status(RoomStatus.ACTIVE)
                .playing(false)
                .positionSeconds(0)
                .playbackRate(1.0)
                .lastActionAt(Instant.now())
                .participantCount(1)
                .maxParticipants(roomProperties.getMaxParticipants())
                .build();

        room = roomRepository.save(room);

        roomParticipantRepository.save(RoomParticipant.builder()
                .roomId(room.getId())
                .userId(hostUserId)
                .role(ParticipantRole.HOST)
                .joinedAt(Instant.now())
                .build());

        if (!invitees.isEmpty()) {
            publishInviteNotifications(room, hostUserId, invitees);
        }

        // Public rooms are broadcast to everyone currently browsing the watch-together lobby
        // (see socket-service's RoomEventKafkaService) so the room list updates live instead of
        // requiring a manual refresh click.
        if (room.isPublicRoom()) {
            saveToOutbox(room.getId(), "room.lobby.created", toListItem(room));
        }

        return buildRoomResponse(room, hostUserId);
    }

    public RoomResponse getRoom(String roomId) {
        String userId = SecurityUtils.getCurrentUserId();
        Room room = findRoomOrThrow(roomId);
        ensureCanView(room, userId);
        return buildRoomResponse(room, userId);
    }

    public PageResponse<RoomListItemResponse> listPublicRooms(int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), size, Sort.by("modifiedDate").descending());
        Page<Room> roomPage = roomRepository.findByStatusAndPublicRoomTrue(RoomStatus.ACTIVE, pageable);

        return PageResponse.<RoomListItemResponse>builder()
                .data(toListItems(roomPage.getContent(), Set.of()))
                .currentPage(page)
                .pageSize(size)
                .totalElement(roomPage.getTotalElements())
                .totalPages(roomPage.getTotalPages())
                .build();
    }

    public PageResponse<RoomListItemResponse> listMyRooms(int page, int size) {
        String userId = SecurityUtils.getCurrentUserId();
        Set<String> joinedRoomIds = roomParticipantRepository.findByUserIdOrderByJoinedAtDesc(userId).stream()
                .map(RoomParticipant::getRoomId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // Rooms the user was explicitly invited to but hasn't joined yet also belong here, so an
        // invite can be accepted straight from "Phòng của tôi" instead of only via the raw link.
        Set<String> roomIds = new LinkedHashSet<>(joinedRoomIds);
        roomRepository.findByInvitedUserIdsContainingAndStatus(userId, RoomStatus.ACTIVE)
                .forEach(room -> roomIds.add(room.getId()));

        if (roomIds.isEmpty()) {
            return PageResponse.<RoomListItemResponse>builder()
                    .data(List.of())
                    .currentPage(page)
                    .pageSize(size)
                    .totalElement(0)
                    .totalPages(0)
                    .build();
        }

        Pageable pageable = PageRequest.of(Math.max(0, page - 1), size, Sort.by("modifiedDate").descending());
        Page<Room> roomPage = roomRepository.findByIdInAndStatus(roomIds, RoomStatus.ACTIVE, pageable);

        return PageResponse.<RoomListItemResponse>builder()
                .data(toListItems(roomPage.getContent(), joinedRoomIds))
                .currentPage(page)
                .pageSize(size)
                .totalElement(roomPage.getTotalElements())
                .totalPages(roomPage.getTotalPages())
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public RoomResponse joinRoom(String roomId, JoinRoomRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        Room room = findRoomOrThrow(roomId);

        if (room.getStatus() != RoomStatus.ACTIVE) {
            throw new AppException(ErrorCode.ROOM_CLOSED);
        }

        Optional<RoomParticipant> existing = roomParticipantRepository.findByRoomIdAndUserId(roomId, userId);
        if (existing.isPresent()) {
            return buildRoomResponse(room, userId);
        }

        ensureCanJoin(room, userId, request != null ? request.getInviteCode() : null);

        if (room.getParticipantCount() >= room.getMaxParticipants()) {
            throw new AppException(ErrorCode.ROOM_FULL);
        }

        Room updated = reserveParticipantSlot(room);
        if (updated == null) {
            throw new AppException(ErrorCode.ROOM_FULL);
        }

        ParticipantRole role = userId.equals(room.getHostUserId()) ? ParticipantRole.HOST : ParticipantRole.VIEWER;
        RoomParticipant participant = roomParticipantRepository.save(RoomParticipant.builder()
                .roomId(roomId)
                .userId(userId)
                .role(role)
                .joinedAt(Instant.now())
                .build());

        publishParticipantEvent(updated, "JOINED", toParticipantResponse(participant));
        publishLobbyUpdated(updated);

        return buildRoomResponse(updated, userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void leaveRoom(String roomId) {
        String userId = SecurityUtils.getCurrentUserId();
        Room room = findRoomOrThrow(roomId);

        Optional<RoomParticipant> participantOpt = roomParticipantRepository.findByRoomIdAndUserId(roomId, userId);
        if (participantOpt.isEmpty()) {
            return;
        }

        RoomParticipant participant = participantOpt.get();
        roomParticipantRepository.deleteByRoomIdAndUserId(roomId, userId);

        // No host-transfer concept for this feature - the room only exists for as long as its
        // creator is around to control it, so the host leaving closes it for everyone.
        if (participant.getRole() == ParticipantRole.HOST) {
            closeRoomInternal(room, "HOST_LEFT");
            return;
        }

        Room updated = decrementParticipantCount(roomId);
        if (updated == null) updated = room;

        publishParticipantEvent(updated, "LEFT", toParticipantResponse(participant));
        publishLobbyUpdated(updated);
    }

    @Transactional(rollbackFor = Exception.class)
    public void closeRoom(String roomId) {
        String userId = SecurityUtils.getCurrentUserId();
        Room room = findRoomOrThrow(roomId);

        if (!userId.equals(room.getHostUserId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        closeRoomInternal(room, "HOST_CLOSED");
    }

    @Transactional(rollbackFor = Exception.class)
    public void updatePlayback(String roomId, PlaybackUpdateRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        Room room = findRoomOrThrow(roomId);

        if (room.getStatus() != RoomStatus.ACTIVE) {
            throw new AppException(ErrorCode.ROOM_CLOSED);
        }
        if (!userId.equals(room.getHostUserId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        if (request == null || request.getAction() == null) {
            throw new AppException(ErrorCode.INVALID_PLAYBACK_ACTION);
        }

        Room updated = applyPlaybackUpdateWithRetry(roomId, room, request);

        saveToOutbox(roomId, "room.playback.updated", RoomPlaybackChangedEvent.builder()
                .roomId(roomId)
                .action(request.getAction())
                .playing(updated.isPlaying())
                .positionSeconds(updated.getPositionSeconds())
                .playbackRate(updated.getPlaybackRate())
                .episodeId(updated.getEpisodeId())
                .actorUserId(userId)
                .at(updated.getLastActionAt())
                .playbackRevision(updated.getPlaybackRevision())
                .build());

        // The lobby cards also show the room's current episode. Publish the updated room
        // snapshot only for public rooms; private-room metadata must never be broadcast to the
        // shared lobby. Private participants receive the same change through room:playback and
        // GET /rooms/my returns the persisted episodeId when they leave the room screen.
        if (request.getAction() == PlaybackAction.CHANGE_EPISODE && updated.isPublicRoom()) {
            saveToOutbox(roomId, "room.lobby.updated", toListItem(updated));
        }
    }

    /** Compare-and-set on Room.playbackRevision so a HEARTBEAT racing a PAUSE (or two commands
     *  from a stale client) can never silently clobber a newer state - the read-compute-write
     *  cycle is retried against fresh state on a CAS miss instead of blindly overwriting.
     *  Bounded at MAX_PLAYBACK_CAS_ATTEMPTS: real contention here is expected to be extremely
     *  rare (the frontend also serializes its own commands - see sendPlayback), so exhausting
     *  retries indicates a genuine conflict worth surfacing rather than looping forever. */
    private Room applyPlaybackUpdateWithRetry(String roomId, Room initial, PlaybackUpdateRequest request) {
        Room current = initial;
        for (int attempt = 1; attempt <= MAX_PLAYBACK_CAS_ATTEMPTS; attempt++) {
            PlaybackComputation next = computeNewState(current, request);
            Instant now = Instant.now();

            Query query = Query.query(Criteria.where("_id").is(roomId)
                    .and("playbackRevision").is(current.getPlaybackRevision()));
            Update update = new Update()
                    .set("playing", next.playing())
                    .set("positionSeconds", next.positionSeconds())
                    .set("playbackRate", next.playbackRate())
                    .set("episodeId", next.episodeId())
                    .set("lastActionAt", now)
                    .inc("playbackRevision", 1);

            Room casResult = mongoTemplate.findAndModify(
                    query, update, FindAndModifyOptions.options().returnNew(true), Room.class);
            if (casResult != null) {
                return casResult;
            }

            current = findRoomOrThrow(roomId);
            if (current.getStatus() != RoomStatus.ACTIVE) {
                throw new AppException(ErrorCode.ROOM_CLOSED);
            }
        }
        throw new AppException(ErrorCode.PLAYBACK_UPDATE_CONFLICT);
    }

    /** Pure computation (no mutation) so it's safe to call again against freshly-read state on a
     *  CAS retry. Also where all playback validation lives. */
    private PlaybackComputation computeNewState(Room current, PlaybackUpdateRequest request) {
        validatePositionSeconds(request.getPositionSeconds());
        validatePlaybackRate(request.getPlaybackRate());

        boolean playing = current.isPlaying();
        double positionSeconds = current.getPositionSeconds();
        String episodeId = current.getEpisodeId();

        switch (request.getAction()) {
            case PLAY -> {
                positionSeconds = request.getPositionSeconds() != null
                        ? request.getPositionSeconds()
                        : computeLivePositionSeconds(current);
                playing = true;
            }
            case PAUSE -> {
                positionSeconds = request.getPositionSeconds() != null
                        ? request.getPositionSeconds()
                        : computeLivePositionSeconds(current);
                playing = false;
            }
            case SEEK -> {
                if (request.getPositionSeconds() == null) {
                    throw new AppException(ErrorCode.INVALID_PLAYBACK_ACTION);
                }
                positionSeconds = request.getPositionSeconds();
            }
            case CHANGE_EPISODE -> {
                if (!StringUtils.hasText(request.getEpisodeId())) {
                    throw new AppException(ErrorCode.INVALID_PLAYBACK_ACTION);
                }
                ensureEpisodeBelongsToFilm(current.getFilmId(), request.getEpisodeId());
                episodeId = request.getEpisodeId();
                positionSeconds = 0;
                playing = true;
            }
            case HEARTBEAT -> {
                // A playing room must keep reporting a live position - accepting a positionless
                // heartbeat here would refresh lastActionAt without moving positionSeconds,
                // making every viewer's live-position extrapolation jump backwards on next sync.
                if (current.isPlaying() && request.getPositionSeconds() == null) {
                    throw new AppException(ErrorCode.INVALID_PLAYBACK_ACTION);
                }
                if (request.getPositionSeconds() != null) {
                    positionSeconds = request.getPositionSeconds();
                }
            }
        }

        double playbackRate = request.getPlaybackRate() != null ? request.getPlaybackRate() : current.getPlaybackRate();
        positionSeconds = clampToEpisodeDuration(current.getFilmId(), episodeId, positionSeconds);

        return new PlaybackComputation(playing, positionSeconds, playbackRate, episodeId);
    }

    private void validatePositionSeconds(Double positionSeconds) {
        if (positionSeconds == null) return;
        if (!Double.isFinite(positionSeconds) || positionSeconds < 0) {
            throw new AppException(ErrorCode.INVALID_POSITION);
        }
    }

    private void validatePlaybackRate(Double playbackRate) {
        if (playbackRate == null) return;
        if (!ALLOWED_PLAYBACK_RATES.contains(playbackRate)) {
            throw new AppException(ErrorCode.INVALID_PLAYBACK_RATE);
        }
    }

    private void ensureEpisodeBelongsToFilm(String filmId, String episodeId) {
        boolean exists = roomFilmExternalService.getEpisodesByFilm(filmId).stream()
                .anyMatch(episode -> episodeId.equals(episode.getId()));
        if (!exists) {
            throw new AppException(ErrorCode.EPISODE_NOT_IN_FILM);
        }
    }

    /** Best-effort only - a cache miss (episode metadata not already resident) just skips
     *  clamping rather than forcing a remote film-service call on every HEARTBEAT. */
    private double clampToEpisodeDuration(String filmId, String episodeId, double positionSeconds) {
        if (!StringUtils.hasText(episodeId)) return positionSeconds;
        return roomFilmExternalService.getCachedEpisode(filmId, episodeId)
                .filter(episode -> episode.getDurationMinutes() > 0)
                .map(episode -> Math.min(positionSeconds, episode.getDurationMinutes() * 60.0))
                .orElse(positionSeconds);
    }

    private record PlaybackComputation(boolean playing, double positionSeconds, double playbackRate, String episodeId) {}

    public List<RoomParticipantResponse> listParticipants(String roomId) {
        String userId = SecurityUtils.getCurrentUserId();
        Room room = findRoomOrThrow(roomId);
        ensureCanView(room, userId);

        List<RoomParticipant> participants = roomParticipantRepository.findByRoomId(roomId);
        Set<String> userIds = participants.stream().map(RoomParticipant::getUserId).collect(Collectors.toSet());
        Map<String, UserProfileResponse> profiles = resolveProfiles(userIds);

        return participants.stream()
                .map(p -> {
                    UserProfileResponse profile = profiles.get(p.getUserId());
                    return RoomParticipantResponse.builder()
                            .userId(p.getUserId())
                            .displayName(profile != null ? profile.getDisplayName() : null)
                            .avatar(profile != null ? profile.getAvatar() : null)
                            .role(p.getRole())
                            .joinedAt(p.getJoinedAt())
                            .build();
                })
                .collect(Collectors.toList());
    }

    /** Used by RoomMessageService before allowing chat read/write - a room's chat is only
     *  reachable once you've actually joined it (host counts as joined at creation time). */
    public Room requireActiveRoomForParticipant(String roomId, String userId) {
        Room room = findRoomOrThrow(roomId);
        if (room.getStatus() != RoomStatus.ACTIVE) {
            throw new AppException(ErrorCode.ROOM_CLOSED);
        }
        boolean isParticipant = userId.equals(room.getHostUserId())
                || roomParticipantRepository.existsByRoomIdAndUserId(roomId, userId);
        if (!isParticipant) {
            throw new AppException(ErrorCode.NOT_A_PARTICIPANT);
        }
        return room;
    }

    Map<String, UserProfileResponse> resolveProfiles(Set<String> userIds) {
        if (userIds == null || userIds.isEmpty()) return Map.of();

        Map<String, UserProfileResponse> resolved = new HashMap<>();
        List<String> keys = userIds.stream().map(id -> "profile:user:" + id).toList();

        try {
            List<UserProfileResponse> cached = redisService.multiGet(keys, new TypeReference<UserProfileResponse>() {});
            cached.stream().filter(Objects::nonNull).forEach(profile -> resolved.put(profile.getUserId(), profile));
        } catch (Exception e) {
            log.warn("Failed to bulk-read profiles from Redis cache", e);
        }

        Set<String> missing = userIds.stream().filter(id -> !resolved.containsKey(id)).collect(Collectors.toSet());
        if (!missing.isEmpty()) {
            Map<String, UserProfileResponse> fetched = roomProfileExternalService.getBulkUserProfiles(missing);
            resolved.putAll(fetched);
            fetched.forEach((id, profile) -> {
                try {
                    redisService.setWithExpiration("profile:user:" + id, profile, 1, TimeUnit.HOURS);
                } catch (Exception e) {
                    log.warn("Failed to cache profile for user {}", id, e);
                }
            });
        }

        return resolved;
    }

    private void closeRoomInternal(Room room, String reason) {
        if (room.getStatus() == RoomStatus.CLOSED) {
            return;
        }
        room.setStatus(RoomStatus.CLOSED);
        room.setPlaying(false);
        roomRepository.save(room);
        roomParticipantRepository.deleteByRoomId(room.getId());

        saveToOutbox(room.getId(), "room.closed", RoomClosedEvent.builder()
                .roomId(room.getId())
                .reason(reason)
                .closedAt(Instant.now())
                .build());
    }

    private void publishInviteNotifications(Room room, String hostUserId, Set<String> inviteeIds) {
        UserProfileResponse hostProfile = resolveProfiles(Set.of(hostUserId)).get(hostUserId);
        String hostName = hostProfile != null && StringUtils.hasText(hostProfile.getDisplayName())
                ? hostProfile.getDisplayName()
                : "Một người bạn";

        RoomInviteNotificationEvent event = RoomInviteNotificationEvent.builder()
                .displayNameSender(hostProfile != null ? hostProfile.getDisplayName() : null)
                .avatarSender(hostProfile != null ? hostProfile.getAvatar() : null)
                .type("WATCH_ROOM_INVITE")
                .title("Lời mời xem chung")
                .content(hostName + " đã mời bạn xem \"" + room.getName() + "\"")
                .toUserIds(new ArrayList<>(inviteeIds))
                .build();

        saveToOutbox(room.getId(), "notification", event);
    }

    private void publishParticipantEvent(Room room, String eventType, RoomParticipantResponse participant) {
        saveToOutbox(room.getId(), "room.participant.changed", RoomParticipantChangedEvent.builder()
                .roomId(room.getId())
                .eventType(eventType)
                .participant(participant)
                .participantCount(room.getParticipantCount())
                .build());
    }

    private void publishLobbyUpdated(Room room) {
        if (room != null && room.isPublicRoom()) {
            saveToOutbox(room.getId(), "room.lobby.updated", toListItem(room));
        }
    }

    private RoomParticipantResponse toParticipantResponse(RoomParticipant participant) {
        UserProfileResponse profile = resolveProfiles(Set.of(participant.getUserId())).get(participant.getUserId());
        return RoomParticipantResponse.builder()
                .userId(participant.getUserId())
                .displayName(profile != null ? profile.getDisplayName() : null)
                .avatar(profile != null ? profile.getAvatar() : null)
                .role(participant.getRole())
                .joinedAt(participant.getJoinedAt())
                .build();
    }

    private RoomResponse buildRoomResponse(Room room, String viewerId) {
        boolean isHost = viewerId != null && viewerId.equals(room.getHostUserId());
        boolean isParticipant = isHost
                || (viewerId != null && roomParticipantRepository.existsByRoomIdAndUserId(room.getId(), viewerId));

        UserProfileResponse hostProfile = resolveProfiles(Set.of(room.getHostUserId())).get(room.getHostUserId());

        return RoomResponse.builder()
                .id(room.getId())
                .name(room.getName())
                .hostUserId(room.getHostUserId())
                .hostDisplayName(hostProfile != null ? hostProfile.getDisplayName() : null)
                .hostAvatar(hostProfile != null ? hostProfile.getAvatar() : null)
                .host(isHost)
                .participant(isParticipant)
                .filmId(room.getFilmId())
                .filmTitle(room.getFilmTitle())
                .filmThumbnail(room.getFilmThumbnail())
                .episodeId(room.getEpisodeId())
                .publicRoom(room.isPublicRoom())
                .inviteCode(room.getInviteCode())
                .status(room.getStatus())
                .playing(room.isPlaying())
                .positionSeconds(computeLivePositionSeconds(room))
                .playbackRate(room.getPlaybackRate())
                .lastActionAt(room.getLastActionAt())
                .playbackRevision(room.getPlaybackRevision())
                .wsToken(isParticipant ? roomSubscriptionTokenService.issueToken(viewerId, room.getId()) : null)
                .participantCount(room.getParticipantCount())
                .maxParticipants(room.getMaxParticipants())
                .createdDate(room.getCreatedDate())
                .build();
    }

    private List<RoomListItemResponse> toListItems(List<Room> rooms, Set<String> joinedRoomIds) {
        if (rooms.isEmpty()) return List.of();

        Set<String> hostIds = rooms.stream().map(Room::getHostUserId).collect(Collectors.toSet());
        Map<String, UserProfileResponse> profiles = resolveProfiles(hostIds);

        return rooms.stream()
                .map(room -> toListItem(room, profiles.get(room.getHostUserId()), joinedRoomIds.contains(room.getId())))
                .collect(Collectors.toList());
    }

    /** Used for the lobby broadcast payload (createRoom) - not scoped to any single viewer, so
     *  alreadyJoined is always false there (see the field's javadoc on RoomListItemResponse). */
    private RoomListItemResponse toListItem(Room room) {
        return toListItem(room, resolveProfiles(Set.of(room.getHostUserId())).get(room.getHostUserId()), false);
    }

    private RoomListItemResponse toListItem(Room room, UserProfileResponse hostProfile, boolean alreadyJoined) {
        return RoomListItemResponse.builder()
                .id(room.getId())
                .name(room.getName())
                .hostUserId(room.getHostUserId())
                .hostDisplayName(hostProfile != null ? hostProfile.getDisplayName() : null)
                .hostAvatar(hostProfile != null ? hostProfile.getAvatar() : null)
                .filmId(room.getFilmId())
                .filmTitle(room.getFilmTitle())
                .filmThumbnail(room.getFilmThumbnail())
                .episodeId(room.getEpisodeId())
                .publicRoom(room.isPublicRoom())
                .alreadyJoined(alreadyJoined)
                .status(room.getStatus())
                .participantCount(room.getParticipantCount())
                .maxParticipants(room.getMaxParticipants())
                .createdDate(room.getCreatedDate())
                .modifiedDate(room.getModifiedDate())
                .build();
    }

    private void ensureCanView(Room room, String userId) {
        if (room.isPublicRoom()) return;
        if (userId.equals(room.getHostUserId())) return;
        if (room.getInvitedUserIds() != null && room.getInvitedUserIds().contains(userId)) return;
        if (roomParticipantRepository.existsByRoomIdAndUserId(room.getId(), userId)) return;
        throw new AppException(ErrorCode.ROOM_ACCESS_DENIED);
    }

    private void ensureCanJoin(Room room, String userId, String inviteCode) {
        if (room.isPublicRoom()) return;
        if (userId.equals(room.getHostUserId())) return;
        if (room.getInvitedUserIds() != null && room.getInvitedUserIds().contains(userId)) return;
        if (StringUtils.hasText(inviteCode) && inviteCode.equals(room.getInviteCode())) return;
        throw new AppException(ErrorCode.ROOM_ACCESS_DENIED);
    }

    private double computeLivePositionSeconds(Room room) {
        if (!room.isPlaying() || room.getLastActionAt() == null) {
            return Math.max(0, room.getPositionSeconds());
        }
        double elapsedSeconds = Duration.between(room.getLastActionAt(), Instant.now()).toMillis() / 1000.0;
        double rate = room.getPlaybackRate() <= 0 ? 1.0 : room.getPlaybackRate();
        return Math.max(0, room.getPositionSeconds() + elapsedSeconds * rate);
    }

    private String generateInviteCode() {
        int length = roomProperties.getInviteCodeLength();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(INVITE_CODE_ALPHABET.charAt(SECURE_RANDOM.nextInt(INVITE_CODE_ALPHABET.length())));
        }
        return sb.toString();
    }

    private Room reserveParticipantSlot(Room room) {
        Query query = Query.query(Criteria.where("_id").is(room.getId())
                .and("status").is(RoomStatus.ACTIVE)
                .and("participantCount").lt(room.getMaxParticipants()));
        Update update = new Update().inc("participantCount", 1);
        return mongoTemplate.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), Room.class);
    }

    private Room decrementParticipantCount(String roomId) {
        Query query = Query.query(Criteria.where("_id").is(roomId)
                .and("participantCount").gt(1));
        Update update = new Update().inc("participantCount", -1);
        return mongoTemplate.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), Room.class);
    }

    /** Called by RoomPresenceCleanupJob after the disconnect grace period. Redis is checked again
     *  inside the transaction so a participant that reconnected while the job was scanning is
     *  retained. On Redis failure this method fails safe and removes nobody. */
    @Transactional(rollbackFor = Exception.class)
    public boolean expireDisconnectedParticipant(String roomId, String userId, Instant disconnectedBefore) {
        Optional<RoomParticipant> participantOpt = roomParticipantRepository.findByRoomIdAndUserId(roomId, userId);
        if (participantOpt.isEmpty() || !hasExpiredPresenceLease(participantOpt.get(), disconnectedBefore)) {
            return false;
        }

        Room room = findRoomOrThrow(roomId);
        if (room.getStatus() != RoomStatus.ACTIVE) return false;

        RoomParticipant participant = participantOpt.get();
        if (participant.getRole() == ParticipantRole.HOST) {
            closeRoomInternal(room, "HOST_DISCONNECTED");
            return true;
        }

        roomParticipantRepository.deleteByRoomIdAndUserId(roomId, userId);
        Room updated = decrementParticipantCount(roomId);
        if (updated == null) updated = room;
        publishParticipantEvent(updated, "LEFT", toParticipantResponse(participant));
        publishLobbyUpdated(updated);
        return true;
    }

    private boolean hasExpiredPresenceLease(RoomParticipant participant, Instant disconnectedBefore) {
        try {
            Long lastSeenAtMillis = redisService.hashGet(
                    WATCH_LAST_SEEN_KEY_PREFIX + participant.getUserId(),
                    participant.getRoomId(),
                    new TypeReference<Long>() {});
            Long disconnectedAtMillis = redisService.hashGet(
                    WATCH_LAST_DISCONNECTED_KEY_PREFIX + participant.getUserId(),
                    participant.getRoomId(),
                    new TypeReference<Long>() {});
            Instant leaseRefreshedAt = newestPresenceInstant(
                    participant.getJoinedAt(), lastSeenAtMillis, disconnectedAtMillis);
            return leaseRefreshedAt != null && !leaseRefreshedAt.isAfter(disconnectedBefore);
        } catch (Exception exception) {
            log.warn("Skipping presence expiry for user {} in room {} because Redis is unavailable",
                    participant.getUserId(), participant.getRoomId(), exception);
            return false;
        }
    }

    private Instant newestPresenceInstant(Instant joinedAt, Long lastSeenAtMillis, Long disconnectedAtMillis) {
        Instant newest = joinedAt;
        if (lastSeenAtMillis != null) {
            Instant lastSeen = Instant.ofEpochMilli(lastSeenAtMillis);
            if (newest == null || lastSeen.isAfter(newest)) newest = lastSeen;
        }
        if (disconnectedAtMillis != null) {
            Instant disconnectedAt = Instant.ofEpochMilli(disconnectedAtMillis);
            if (newest == null || disconnectedAt.isAfter(newest)) newest = disconnectedAt;
        }
        return newest;
    }

    private Room findRoomOrThrow(String roomId) {
        return roomRepository.findById(roomId).orElseThrow(() -> new AppException(ErrorCode.ROOM_NOT_FOUND));
    }

    private void saveToOutbox(String aggregateId, String topic, Object payload) {
        try {
            outboxRepository.save(Outbox.builder()
                    .aggregateId(aggregateId)
                    .topic(topic)
                    .payload(objectMapper.writeValueAsString(payload))
                    .build());
        } catch (Exception e) {
            log.error("Failed to save outbox with topic: {}", topic, e);
            throw new AppException(ErrorCode.OUTBOX_SAVE_FAILED);
        }
    }
}
