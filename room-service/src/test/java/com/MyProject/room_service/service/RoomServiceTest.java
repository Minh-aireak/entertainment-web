package com.MyProject.room_service.service;

import com.MyProject.common.security.SecurityUtils;
import com.MyProject.room_service.configuration.RoomProperties;
import com.MyProject.room_service.dto.request.CreateRoomRequest;
import com.MyProject.room_service.dto.request.JoinRoomRequest;
import com.MyProject.room_service.dto.request.PlaybackUpdateRequest;
import com.MyProject.room_service.dto.response.RoomResponse;
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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    private static final String HOST_ID = "host-1";
    private static final String VIEWER_ID = "viewer-1";

    @Mock RoomRepository roomRepository;
    @Mock RoomParticipantRepository roomParticipantRepository;
    @Mock OutboxRepository outboxRepository;
    @Mock RoomProfileExternalService roomProfileExternalService;
    @Mock RoomFilmExternalService roomFilmExternalService;
    @Mock com.MyProject.common.redis.RedisService redisService;
    @Mock MongoTemplate mongoTemplate;
    @Mock RoomSubscriptionTokenService roomSubscriptionTokenService;

    RoomService roomService;
    RoomProperties roomProperties;
    MockedStatic<SecurityUtils> securityUtils;

    @BeforeEach
    void setUp() {
        roomProperties = new RoomProperties();
        roomProperties.setMaxParticipants(10);
        roomProperties.setInviteCodeLength(8);

        roomService = new RoomService(roomRepository, roomParticipantRepository, outboxRepository, roomProperties,
                roomProfileExternalService, roomFilmExternalService, redisService,
                new ObjectMapper().registerModule(new JavaTimeModule()), mongoTemplate, roomSubscriptionTokenService);

        securityUtils = mockStatic(SecurityUtils.class);
        securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(HOST_ID);

        lenient().when(roomFilmExternalService.getFilmInfo(any())).thenReturn(
                Optional.of(new FilmClient.FilmInfo("film-1", "Film title", "thumb.jpg")));
        lenient().when(roomFilmExternalService.getEpisodesByFilm(any())).thenReturn(List.of());
        lenient().when(roomFilmExternalService.getCachedEpisode(any(), any())).thenReturn(Optional.empty());
        lenient().when(roomRepository.save(any(Room.class))).thenAnswer(inv -> {
            Room r = inv.getArgument(0);
            if (r.getId() == null) r.setId("room-1");
            return r;
        });
        lenient().when(roomProfileExternalService.getBulkUserProfiles(any())).thenReturn(java.util.Map.of());
        lenient().when(roomSubscriptionTokenService.issueToken(any(), any())).thenReturn("ws-token");
    }

    /** updatePlayback persists via a compare-and-set MongoTemplate.findAndModify instead of
     *  roomRepository.save, so tests simulate a successful CAS by applying the $set/$inc
     *  operators from the real Update object onto `base` - this exercises the actual
     *  computeNewState -> Update -> event pipeline instead of just echoing a canned result. */
    private Room applyCas(Room base, Update update) {
        Room result = new Room();
        result.setId(base.getId());
        result.setName(base.getName());
        result.setHostUserId(base.getHostUserId());
        result.setFilmId(base.getFilmId());
        result.setFilmTitle(base.getFilmTitle());
        result.setFilmThumbnail(base.getFilmThumbnail());
        result.setEpisodeId(base.getEpisodeId());
        result.setPublicRoom(base.isPublicRoom());
        result.setInviteCode(base.getInviteCode());
        result.setInvitedUserIds(base.getInvitedUserIds());
        result.setStatus(base.getStatus());
        result.setPlaying(base.isPlaying());
        result.setPositionSeconds(base.getPositionSeconds());
        result.setPlaybackRate(base.getPlaybackRate());
        result.setLastActionAt(base.getLastActionAt());
        result.setPlaybackRevision(base.getPlaybackRevision());
        result.setParticipantCount(base.getParticipantCount());
        result.setMaxParticipants(base.getMaxParticipants());
        result.setCreatedDate(base.getCreatedDate());
        result.setModifiedDate(base.getModifiedDate());

        org.bson.Document raw = update.getUpdateObject();
        org.bson.Document set = (org.bson.Document) raw.get("$set");
        if (set != null) {
            if (set.containsKey("playing")) result.setPlaying((Boolean) set.get("playing"));
            if (set.containsKey("positionSeconds")) result.setPositionSeconds(((Number) set.get("positionSeconds")).doubleValue());
            if (set.containsKey("playbackRate")) result.setPlaybackRate(((Number) set.get("playbackRate")).doubleValue());
            if (set.containsKey("episodeId")) result.setEpisodeId((String) set.get("episodeId"));
            if (set.containsKey("lastActionAt")) result.setLastActionAt((Instant) set.get("lastActionAt"));
        }
        org.bson.Document inc = (org.bson.Document) raw.get("$inc");
        if (inc != null && inc.containsKey("playbackRevision")) {
            result.setPlaybackRevision(result.getPlaybackRevision() + ((Number) inc.get("playbackRevision")).intValue());
        }
        return result;
    }

    /** CAS query matches only when it targets the given revision - lets tests simulate a miss
     *  (stale revision) by stubbing this to return null for a specific revision value. */
    private void stubCasSuccess(String roomId, Room base) {
        lenient().when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Room.class)))
                .thenAnswer(inv -> applyCas(base, inv.getArgument(1)));
    }

    @AfterEach
    void tearDown() {
        securityUtils.close();
    }

    private Room.RoomBuilder activeRoomBuilder() {
        return Room.builder().id("room-1").hostUserId(HOST_ID).status(RoomStatus.ACTIVE)
                .maxParticipants(10).participantCount(1).lastActionAt(Instant.now());
    }

    // ---------- createRoom ----------

    @Test
    void createRoom_blankFilmId_throwsInvalidFilm() {
        CreateRoomRequest request = CreateRoomRequest.builder().filmId("  ").build();

        assertThatThrownBy(() -> roomService.createRoom(request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_FILM);
    }

    @Test
    void createRoom_nullRequest_throwsInvalidFilm() {
        assertThatThrownBy(() -> roomService.createRoom(null))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_FILM);
    }

    @Test
    void createRoom_happyPath_createsHostParticipant() {
        CreateRoomRequest request = CreateRoomRequest.builder().filmId("film-1").name("My Room").build();

        RoomResponse response = roomService.createRoom(request);

        assertThat(response.getHostUserId()).isEqualTo(HOST_ID);
        assertThat(response.isHost()).isTrue();
        verify(roomParticipantRepository).save(argThat(p ->
                p.getUserId().equals(HOST_ID) && p.getRole() == ParticipantRole.HOST));
    }

    @Test
    void createRoom_excludesHostFromInviteeListAndNotifiesRest() {
        CreateRoomRequest request = CreateRoomRequest.builder().filmId("film-1")
                .inviteeUserIds(List.of(HOST_ID, "friend-1")).build();

        roomService.createRoom(request);

        verify(outboxRepository).save(argThat(o -> o.getTopic().equals("notification")
                && o.getPayload().contains("friend-1") && !o.getPayload().contains("\"toUserIds\":[\"" + HOST_ID)));
    }

    @Test
    void createRoom_publicRoom_broadcastsToLobby() {
        CreateRoomRequest request = CreateRoomRequest.builder().filmId("film-1").publicRoom(true).build();

        roomService.createRoom(request);

        verify(outboxRepository).save(argThat(o -> o.getTopic().equals("room.lobby.created")));
    }

    @Test
    void createRoom_privateRoom_doesNotBroadcastToLobby() {
        CreateRoomRequest request = CreateRoomRequest.builder().filmId("film-1").publicRoom(false).build();

        roomService.createRoom(request);

        verify(outboxRepository, never()).save(argThat(o -> o.getTopic().equals("room.lobby.created")));
    }

    @Test
    void createRoom_noNameProvided_fallsBackToFilmTitle() {
        when(roomFilmExternalService.getFilmInfo("film-1")).thenReturn(
                Optional.of(new FilmClient.FilmInfo("film-1", "Inception", "thumb.jpg")));
        CreateRoomRequest request = CreateRoomRequest.builder().filmId("film-1").build();

        roomService.createRoom(request);

        verify(roomRepository).save(argThat(r -> r.getName().equals("Xem chung: Inception")));
    }

    @Test
    void createRoom_episodeNotInFilm_rejectsBeforeSavingRoom() {
        when(roomFilmExternalService.getEpisodesByFilm("film-1")).thenReturn(List.of());
        CreateRoomRequest request = CreateRoomRequest.builder()
                .filmId("film-1")
                .episodeId("episode-from-another-film")
                .build();

        assertThatThrownBy(() -> roomService.createRoom(request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.EPISODE_NOT_IN_FILM);

        verify(roomRepository, never()).save(any());
    }

    // ---------- getRoom ----------

    @Test
    void getRoom_notFound_throws() {
        when(roomRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.getRoom("missing"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.ROOM_NOT_FOUND);
    }

    @Test
    void getRoom_privateRoomNoAccess_throwsAccessDenied() {
        securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(VIEWER_ID);
        Room room = activeRoomBuilder().publicRoom(false).build();
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));
        when(roomParticipantRepository.existsByRoomIdAndUserId("room-1", VIEWER_ID)).thenReturn(false);

        assertThatThrownBy(() -> roomService.getRoom("room-1"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.ROOM_ACCESS_DENIED);
    }

    @Test
    void getRoom_publicRoom_anyoneCanView() {
        securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(VIEWER_ID);
        Room room = activeRoomBuilder().publicRoom(true).build();
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));

        RoomResponse response = roomService.getRoom("room-1");

        assertThat(response.isHost()).isFalse();
    }

    @Test
    void getRoom_nonParticipant_wsTokenIsNull() {
        securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(VIEWER_ID);
        Room room = activeRoomBuilder().publicRoom(true).build();
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));
        when(roomParticipantRepository.existsByRoomIdAndUserId("room-1", VIEWER_ID)).thenReturn(false);

        RoomResponse response = roomService.getRoom("room-1");

        assertThat(response.isParticipant()).isFalse();
        assertThat(response.getWsToken()).isNull();
        verifyNoInteractions(roomSubscriptionTokenService);
    }

    @Test
    void getRoom_host_includesPlaybackRevisionAndWsToken() {
        Room room = activeRoomBuilder().playbackRevision(7).publicRoom(true).build();
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));
        when(roomSubscriptionTokenService.issueToken(HOST_ID, "room-1")).thenReturn("signed-token");

        RoomResponse response = roomService.getRoom("room-1");

        assertThat(response.isHost()).isTrue();
        assertThat(response.getPlaybackRevision()).isEqualTo(7);
        assertThat(response.getWsToken()).isEqualTo("signed-token");
    }

    // ---------- joinRoom ----------

    @Test
    void joinRoom_alreadyJoined_returnsWithoutCreatingDuplicateParticipant() {
        securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(VIEWER_ID);
        Room room = activeRoomBuilder().publicRoom(true).build();
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));
        when(roomParticipantRepository.findByRoomIdAndUserId("room-1", VIEWER_ID))
                .thenReturn(Optional.of(RoomParticipant.builder().userId(VIEWER_ID).build()));

        roomService.joinRoom("room-1", null);

        verify(roomParticipantRepository, never()).save(any());
    }

    @Test
    void joinRoom_roomClosed_throws() {
        securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(VIEWER_ID);
        Room room = activeRoomBuilder().status(RoomStatus.CLOSED).build();
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> roomService.joinRoom("room-1", null))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.ROOM_CLOSED);
    }

    @Test
    void joinRoom_privateRoomWithoutInviteOrAccess_throwsAccessDenied() {
        securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(VIEWER_ID);
        Room room = activeRoomBuilder().publicRoom(false).inviteCode("ABC123").build();
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));
        when(roomParticipantRepository.findByRoomIdAndUserId("room-1", VIEWER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.joinRoom("room-1", JoinRoomRequest.builder().inviteCode("WRONG").build()))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.ROOM_ACCESS_DENIED);
    }

    @Test
    void joinRoom_full_throws() {
        securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(VIEWER_ID);
        Room room = activeRoomBuilder().publicRoom(true).participantCount(10).maxParticipants(10).build();
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));
        when(roomParticipantRepository.findByRoomIdAndUserId("room-1", VIEWER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.joinRoom("room-1", null))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.ROOM_FULL);
    }

    @Test
    void joinRoom_validInviteCode_succeedsAndPublishesJoinedEvent() {
        securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(VIEWER_ID);
        Room room = activeRoomBuilder().publicRoom(false).inviteCode("ABC123").build();
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));
        when(roomParticipantRepository.findByRoomIdAndUserId("room-1", VIEWER_ID)).thenReturn(Optional.empty());
        when(roomParticipantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Room.class)))
                .thenReturn(room);

        roomService.joinRoom("room-1", JoinRoomRequest.builder().inviteCode("ABC123").build());

        verify(roomParticipantRepository).save(argThat(p -> p.getRole() == ParticipantRole.VIEWER));
        verify(outboxRepository).save(argThat(o -> o.getPayload().contains("JOINED")));
    }

    @Test
    void joinRoom_atomicReservationMiss_throwsRoomFullWithoutSavingParticipant() {
        securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(VIEWER_ID);
        Room room = activeRoomBuilder().publicRoom(true).participantCount(9).maxParticipants(10).build();
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));
        when(roomParticipantRepository.findByRoomIdAndUserId("room-1", VIEWER_ID)).thenReturn(Optional.empty());
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Room.class)))
                .thenReturn(null);

        assertThatThrownBy(() -> roomService.joinRoom("room-1", null))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.ROOM_FULL);

        verify(roomParticipantRepository, never()).save(any());
    }

    @Test
    void joinRoom_publicRoom_publishesUpdatedParticipantCountToLobby() {
        securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(VIEWER_ID);
        Room room = activeRoomBuilder().publicRoom(true).participantCount(1).build();
        Room updated = activeRoomBuilder().publicRoom(true).participantCount(2).build();
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));
        when(roomParticipantRepository.findByRoomIdAndUserId("room-1", VIEWER_ID)).thenReturn(Optional.empty());
        when(roomParticipantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Room.class)))
                .thenReturn(updated);

        roomService.joinRoom("room-1", null);

        verify(outboxRepository).save(argThat(o -> o.getTopic().equals("room.lobby.updated")
                && o.getPayload().contains("\"participantCount\":2")));
    }

    // ---------- leaveRoom ----------

    @Test
    void leaveRoom_notParticipant_isNoOp() {
        securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(VIEWER_ID);
        Room room = activeRoomBuilder().build();
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));
        when(roomParticipantRepository.findByRoomIdAndUserId("room-1", VIEWER_ID)).thenReturn(Optional.empty());

        roomService.leaveRoom("room-1");

        verify(roomParticipantRepository, never()).deleteByRoomIdAndUserId(any(), any());
    }

    @Test
    void leaveRoom_hostLeaves_closesRoomForEveryone() {
        Room room = activeRoomBuilder().build();
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));
        when(roomParticipantRepository.findByRoomIdAndUserId("room-1", HOST_ID))
                .thenReturn(Optional.of(RoomParticipant.builder().userId(HOST_ID).role(ParticipantRole.HOST).build()));

        roomService.leaveRoom("room-1");

        assertThat(room.getStatus()).isEqualTo(RoomStatus.CLOSED);
        verify(roomParticipantRepository).deleteByRoomId("room-1");
        verify(outboxRepository).save(argThat(o -> o.getTopic().equals("room.closed")));
    }

    @Test
    void leaveRoom_viewerLeaves_decrementsParticipantCount() {
        securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(VIEWER_ID);
        Room room = activeRoomBuilder().build();
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));
        when(roomParticipantRepository.findByRoomIdAndUserId("room-1", VIEWER_ID))
                .thenReturn(Optional.of(RoomParticipant.builder().userId(VIEWER_ID).role(ParticipantRole.VIEWER).build()));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Room.class)))
                .thenReturn(room);

        roomService.leaveRoom("room-1");

        verify(roomParticipantRepository).deleteByRoomIdAndUserId("room-1", VIEWER_ID);
        verify(outboxRepository).save(argThat(o -> o.getPayload().contains("LEFT")));
    }

    @Test
    void leaveRoom_publicRoom_publishesUpdatedParticipantCountToLobby() {
        securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(VIEWER_ID);
        Room room = activeRoomBuilder().publicRoom(true).participantCount(2).build();
        Room updated = activeRoomBuilder().publicRoom(true).participantCount(1).build();
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));
        when(roomParticipantRepository.findByRoomIdAndUserId("room-1", VIEWER_ID))
                .thenReturn(Optional.of(RoomParticipant.builder().userId(VIEWER_ID).role(ParticipantRole.VIEWER).build()));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Room.class)))
                .thenReturn(updated);

        roomService.leaveRoom("room-1");

        verify(outboxRepository).save(argThat(o -> o.getTopic().equals("room.lobby.updated")
                && o.getPayload().contains("\"participantCount\":1")));
    }

    // ---------- closeRoom ----------

    @Test
    void closeRoom_notHost_throwsUnauthorized() {
        securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(VIEWER_ID);
        Room room = activeRoomBuilder().build();
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> roomService.closeRoom("room-1"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void closeRoom_alreadyClosed_isNoOpAndDoesNotRepublish() {
        Room room = activeRoomBuilder().status(RoomStatus.CLOSED).build();
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));

        roomService.closeRoom("room-1");

        verifyNoInteractions(outboxRepository);
        verify(roomRepository, never()).save(any());
    }

    @Test
    void closeRoom_happyPath_closesAndRemovesParticipants() {
        Room room = activeRoomBuilder().build();
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));

        roomService.closeRoom("room-1");

        assertThat(room.getStatus()).isEqualTo(RoomStatus.CLOSED);
        assertThat(room.isPlaying()).isFalse();
        verify(roomParticipantRepository).deleteByRoomId("room-1");
    }

    // ---------- updatePlayback ----------

    @Test
    void updatePlayback_roomClosed_throws() {
        Room room = activeRoomBuilder().status(RoomStatus.CLOSED).build();
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> roomService.updatePlayback("room-1",
                PlaybackUpdateRequest.builder().action(PlaybackAction.PLAY).build()))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.ROOM_CLOSED);
    }

    @Test
    void updatePlayback_notHost_throwsUnauthorized() {
        securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(VIEWER_ID);
        Room room = activeRoomBuilder().build();
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> roomService.updatePlayback("room-1",
                PlaybackUpdateRequest.builder().action(PlaybackAction.PLAY).build()))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void updatePlayback_nullAction_throwsInvalidPlaybackAction() {
        Room room = activeRoomBuilder().build();
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> roomService.updatePlayback("room-1", PlaybackUpdateRequest.builder().build()))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_PLAYBACK_ACTION);
    }

    @Test
    void updatePlayback_seekWithoutPosition_throws() {
        Room room = activeRoomBuilder().build();
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> roomService.updatePlayback("room-1",
                PlaybackUpdateRequest.builder().action(PlaybackAction.SEEK).build()))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_PLAYBACK_ACTION);
    }

    @Test
    void updatePlayback_changeEpisodeBlankId_throws() {
        Room room = activeRoomBuilder().build();
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> roomService.updatePlayback("room-1",
                PlaybackUpdateRequest.builder().action(PlaybackAction.CHANGE_EPISODE).build()))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_PLAYBACK_ACTION);
    }

    @Test
    void updatePlayback_play_setsPlayingTrueAndPublishesEvent() {
        Room room = activeRoomBuilder().playing(false).positionSeconds(10).playbackRevision(4).build();
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));
        stubCasSuccess("room-1", room);

        roomService.updatePlayback("room-1",
                PlaybackUpdateRequest.builder().action(PlaybackAction.PLAY).positionSeconds(42.0).build());

        verify(outboxRepository).save(argThat(o -> o.getTopic().equals("room.playback.updated")
                && o.getPayload().contains("\"playing\":true")
                && o.getPayload().contains("\"positionSeconds\":42.0")
                && o.getPayload().contains("\"playbackRevision\":5")));
    }

    @Test
    void updatePlayback_seekWithPosition_updatesPositionOnly() {
        Room room = activeRoomBuilder().playing(true).build();
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));
        stubCasSuccess("room-1", room);

        roomService.updatePlayback("room-1",
                PlaybackUpdateRequest.builder().action(PlaybackAction.SEEK).positionSeconds(99.0).build());

        verify(outboxRepository).save(argThat(o -> o.getTopic().equals("room.playback.updated")
                && o.getPayload().contains("\"positionSeconds\":99.0")
                && o.getPayload().contains("\"playing\":true")));
    }

    @Test
    void updatePlayback_heartbeat_neverFlipsPlaying() {
        Room room = activeRoomBuilder().playing(true).positionSeconds(5).build();
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));
        stubCasSuccess("room-1", room);

        roomService.updatePlayback("room-1",
                PlaybackUpdateRequest.builder().action(PlaybackAction.HEARTBEAT).positionSeconds(12.0).build());

        verify(outboxRepository).save(argThat(o -> o.getTopic().equals("room.playback.updated")
                && o.getPayload().contains("\"playing\":true")
                && o.getPayload().contains("\"positionSeconds\":12.0")));
    }

    @Test
    void updatePlayback_heartbeatWhilePlayingWithoutPosition_throwsAndDoesNotWrite() {
        Room room = activeRoomBuilder().playing(true).positionSeconds(5).build();
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> roomService.updatePlayback("room-1",
                PlaybackUpdateRequest.builder().action(PlaybackAction.HEARTBEAT).build()))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_PLAYBACK_ACTION);

        verifyNoInteractions(outboxRepository);
        verify(mongoTemplate, never()).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Room.class));
    }

    @Test
    void updatePlayback_heartbeatWhilePaused_positionOptional() {
        Room room = activeRoomBuilder().playing(false).positionSeconds(5).build();
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));
        stubCasSuccess("room-1", room);

        roomService.updatePlayback("room-1", PlaybackUpdateRequest.builder().action(PlaybackAction.HEARTBEAT).build());

        verify(outboxRepository).save(argThat(o -> o.getTopic().equals("room.playback.updated")
                && o.getPayload().contains("\"positionSeconds\":5.0")));
    }

    @Test
    void updatePlayback_negativePosition_throwsInvalidPosition() {
        Room room = activeRoomBuilder().build();
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> roomService.updatePlayback("room-1",
                PlaybackUpdateRequest.builder().action(PlaybackAction.SEEK).positionSeconds(-1.0).build()))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_POSITION);
    }

    @Test
    void updatePlayback_nonFinitePosition_throwsInvalidPosition() {
        Room room = activeRoomBuilder().build();
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> roomService.updatePlayback("room-1",
                PlaybackUpdateRequest.builder().action(PlaybackAction.SEEK).positionSeconds(Double.NaN).build()))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_POSITION);
    }

    @Test
    void updatePlayback_unsupportedPlaybackRate_throwsInvalidPlaybackRate() {
        Room room = activeRoomBuilder().build();
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> roomService.updatePlayback("room-1",
                PlaybackUpdateRequest.builder().action(PlaybackAction.HEARTBEAT).playbackRate(1.75).build()))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_PLAYBACK_RATE);
    }

    @Test
    void updatePlayback_changeEpisode_updatesRoomAndPublicLobby() {
        Room room = activeRoomBuilder().publicRoom(true).filmId("film-1").episodeId("episode-1").build();
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));
        when(roomFilmExternalService.getEpisodesByFilm("film-1"))
                .thenReturn(List.of(new FilmClient.EpisodeInfo("episode-12", "film-1", 20)));
        stubCasSuccess("room-1", room);

        roomService.updatePlayback("room-1", PlaybackUpdateRequest.builder()
                .action(PlaybackAction.CHANGE_EPISODE)
                .episodeId("episode-12")
                .build());

        verify(outboxRepository).save(argThat(o -> o.getTopic().equals("room.playback.updated")
                && o.getPayload().contains("episode-12")));
        verify(outboxRepository).save(argThat(o -> o.getTopic().equals("room.lobby.updated")
                && o.getPayload().contains("episode-12")));
    }

    @Test
    void updatePlayback_changeEpisode_privateRoomDoesNotBroadcastToLobby() {
        Room room = activeRoomBuilder().publicRoom(false).filmId("film-1").build();
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));
        when(roomFilmExternalService.getEpisodesByFilm("film-1"))
                .thenReturn(List.of(new FilmClient.EpisodeInfo("episode-2", "film-1", 20)));
        stubCasSuccess("room-1", room);

        roomService.updatePlayback("room-1", PlaybackUpdateRequest.builder()
                .action(PlaybackAction.CHANGE_EPISODE)
                .episodeId("episode-2")
                .build());

        verify(outboxRepository, never()).save(argThat(o -> o.getTopic().equals("room.lobby.updated")));
    }

    @Test
    void updatePlayback_changeEpisodeNotInFilm_throwsEpisodeNotInFilm() {
        Room room = activeRoomBuilder().filmId("film-1").build();
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));
        when(roomFilmExternalService.getEpisodesByFilm("film-1")).thenReturn(List.of());

        assertThatThrownBy(() -> roomService.updatePlayback("room-1", PlaybackUpdateRequest.builder()
                .action(PlaybackAction.CHANGE_EPISODE)
                .episodeId("episode-from-another-film")
                .build()))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.EPISODE_NOT_IN_FILM);

        verifyNoInteractions(outboxRepository);
    }

    @Test
    void updatePlayback_zeroDurationMetadata_doesNotClampPositionToZero() {
        Room room = activeRoomBuilder().filmId("film-1").episodeId("episode-1").playing(true).build();
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));
        when(roomFilmExternalService.getCachedEpisode("film-1", "episode-1"))
                .thenReturn(Optional.of(new FilmClient.EpisodeInfo("episode-1", "film-1", 0)));
        stubCasSuccess("room-1", room);

        roomService.updatePlayback("room-1",
                PlaybackUpdateRequest.builder().action(PlaybackAction.SEEK).positionSeconds(99.0).build());

        verify(outboxRepository).save(argThat(o -> o.getTopic().equals("room.playback.updated")
                && o.getPayload().contains("\"positionSeconds\":99.0")));
    }

    @Test
    void updatePlayback_positiveDuration_clampsPositionToEpisodeEnd() {
        Room room = activeRoomBuilder().filmId("film-1").episodeId("episode-1").playing(true).build();
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));
        when(roomFilmExternalService.getCachedEpisode("film-1", "episode-1"))
                .thenReturn(Optional.of(new FilmClient.EpisodeInfo("episode-1", "film-1", 20)));
        stubCasSuccess("room-1", room);

        roomService.updatePlayback("room-1",
                PlaybackUpdateRequest.builder().action(PlaybackAction.SEEK).positionSeconds(1300.0).build());

        verify(outboxRepository).save(argThat(o -> o.getTopic().equals("room.playback.updated")
                && o.getPayload().contains("\"positionSeconds\":1200.0")));
    }

    @Test
    void updatePlayback_casMiss_retriesAgainstFreshStateInsteadOfLosingUpdate() {
        // Simulates a HEARTBEAT racing a PAUSE: the read at the top of updatePlayback sees
        // playbackRevision=1/playing=true, but by the time the CAS write runs, a concurrent PAUSE
        // has already landed at revision 2/playing=false. The first CAS attempt (querying for
        // revision=1) must miss, forcing a re-read and a retry computed against the fresher state
        // instead of blindly reintroducing playing=true.
        Room staleRead = activeRoomBuilder().playing(true).positionSeconds(10).playbackRevision(1).build();
        Room concurrentlyPaused = activeRoomBuilder().playing(false).positionSeconds(15).playbackRevision(2).build();
        when(roomRepository.findById("room-1"))
                .thenReturn(Optional.of(staleRead))
                .thenReturn(Optional.of(concurrentlyPaused));

        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Room.class)))
                .thenAnswer(inv -> {
                    Query query = inv.getArgument(0);
                    boolean targetsStaleRevision = query.getQueryObject().get("playbackRevision").equals(1);
                    if (targetsStaleRevision) return null; // CAS miss - another writer already moved it to revision 2
                    return applyCas(concurrentlyPaused, inv.getArgument(1));
                });

        roomService.updatePlayback("room-1",
                PlaybackUpdateRequest.builder().action(PlaybackAction.HEARTBEAT).positionSeconds(15.5).build());

        verify(mongoTemplate, times(2)).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Room.class));
        verify(outboxRepository).save(argThat(o -> o.getTopic().equals("room.playback.updated")
                // Retried against the freshly-read (paused) state, so playing stays false -
                // the stale HEARTBEAT never resurrects playing=true.
                && o.getPayload().contains("\"playing\":false")
                && o.getPayload().contains("\"positionSeconds\":15.5")
                && o.getPayload().contains("\"playbackRevision\":3")));
    }

    @Test
    void updatePlayback_casMissExhaustsRetries_throwsPlaybackUpdateConflict() {
        Room room = activeRoomBuilder().build();
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Room.class)))
                .thenReturn(null);

        assertThatThrownBy(() -> roomService.updatePlayback("room-1",
                PlaybackUpdateRequest.builder().action(PlaybackAction.PAUSE).build()))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.PLAYBACK_UPDATE_CONFLICT);

        verify(mongoTemplate, times(3)).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Room.class));
    }

    // ---------- disconnected presence cleanup ----------

    @Test
    void expireDisconnectedParticipant_recentHeartbeat_keepsParticipant() {
        RoomParticipant participant = RoomParticipant.builder()
                .roomId("room-1").userId(VIEWER_ID).role(ParticipantRole.VIEWER)
                .joinedAt(Instant.now().minusSeconds(600)).build();
        when(roomParticipantRepository.findByRoomIdAndUserId("room-1", VIEWER_ID))
                .thenReturn(Optional.of(participant));
        when(redisService.hashGet(eq("presence:watch-room:last-seen:" + VIEWER_ID), eq("room-1"), any(TypeReference.class)))
                .thenReturn(Instant.now().toEpochMilli());

        boolean expired = roomService.expireDisconnectedParticipant(
                "room-1", VIEWER_ID, Instant.now().minusSeconds(120));

        assertThat(expired).isFalse();
        verify(roomParticipantRepository, never()).deleteByRoomIdAndUserId(any(), any());
    }

    @Test
    void expireDisconnectedParticipant_staleViewer_removesAndBroadcastsLobbyCount() {
        Instant disconnectedAt = Instant.now().minusSeconds(300);
        RoomParticipant participant = RoomParticipant.builder()
                .roomId("room-1").userId(VIEWER_ID).role(ParticipantRole.VIEWER)
                .joinedAt(disconnectedAt).build();
        Room room = activeRoomBuilder().publicRoom(true).participantCount(2).build();
        Room updated = activeRoomBuilder().publicRoom(true).participantCount(1).build();
        when(roomParticipantRepository.findByRoomIdAndUserId("room-1", VIEWER_ID))
                .thenReturn(Optional.of(participant));
        when(redisService.hashGet(eq("presence:watch-room:last-seen:" + VIEWER_ID), eq("room-1"), any(TypeReference.class)))
                .thenReturn(null);
        when(redisService.hashGet(eq("presence:watch-room:last-disconnected:" + VIEWER_ID), eq("room-1"), any(TypeReference.class)))
                .thenReturn(disconnectedAt.toEpochMilli());
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Room.class)))
                .thenReturn(updated);

        boolean expired = roomService.expireDisconnectedParticipant(
                "room-1", VIEWER_ID, Instant.now().minusSeconds(120));

        assertThat(expired).isTrue();
        verify(roomParticipantRepository).deleteByRoomIdAndUserId("room-1", VIEWER_ID);
        verify(outboxRepository).save(argThat(o -> o.getTopic().equals("room.lobby.updated")
                && o.getPayload().contains("\"participantCount\":1")));
    }

    @Test
    void expireDisconnectedParticipant_staleHost_closesRoom() {
        Instant disconnectedAt = Instant.now().minusSeconds(300);
        RoomParticipant participant = RoomParticipant.builder()
                .roomId("room-1").userId(HOST_ID).role(ParticipantRole.HOST)
                .joinedAt(disconnectedAt).build();
        Room room = activeRoomBuilder().build();
        when(roomParticipantRepository.findByRoomIdAndUserId("room-1", HOST_ID))
                .thenReturn(Optional.of(participant));
        when(redisService.hashGet(eq("presence:watch-room:last-seen:" + HOST_ID), eq("room-1"), any(TypeReference.class)))
                .thenReturn(null);
        when(redisService.hashGet(eq("presence:watch-room:last-disconnected:" + HOST_ID), eq("room-1"), any(TypeReference.class)))
                .thenReturn(disconnectedAt.toEpochMilli());
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));

        boolean expired = roomService.expireDisconnectedParticipant(
                "room-1", HOST_ID, Instant.now().minusSeconds(120));

        assertThat(expired).isTrue();
        assertThat(room.getStatus()).isEqualTo(RoomStatus.CLOSED);
        verify(outboxRepository).save(argThat(o -> o.getTopic().equals("room.closed")
                && o.getPayload().contains("HOST_DISCONNECTED")));
    }

    // ---------- requireActiveRoomForParticipant ----------

    @Test
    void requireActiveRoomForParticipant_closedRoom_throws() {
        Room room = activeRoomBuilder().status(RoomStatus.CLOSED).build();
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> roomService.requireActiveRoomForParticipant("room-1", VIEWER_ID))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.ROOM_CLOSED);
    }

    @Test
    void requireActiveRoomForParticipant_notParticipant_throws() {
        Room room = activeRoomBuilder().build();
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));
        when(roomParticipantRepository.existsByRoomIdAndUserId("room-1", VIEWER_ID)).thenReturn(false);

        assertThatThrownBy(() -> roomService.requireActiveRoomForParticipant("room-1", VIEWER_ID))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_A_PARTICIPANT);
    }

    @Test
    void requireActiveRoomForParticipant_host_alwaysAllowedWithoutParticipantLookup() {
        Room room = activeRoomBuilder().build();
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));

        Room result = roomService.requireActiveRoomForParticipant("room-1", HOST_ID);

        assertThat(result).isEqualTo(room);
        verifyNoInteractions(roomParticipantRepository);
    }
}
