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
                new ObjectMapper().registerModule(new JavaTimeModule()), mongoTemplate);

        securityUtils = mockStatic(SecurityUtils.class);
        securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(HOST_ID);

        lenient().when(roomFilmExternalService.getFilmInfo(any())).thenReturn(Optional.empty());
        lenient().when(roomRepository.save(any(Room.class))).thenAnswer(inv -> {
            Room r = inv.getArgument(0);
            if (r.getId() == null) r.setId("room-1");
            return r;
        });
        lenient().when(roomProfileExternalService.getBulkUserProfiles(any())).thenReturn(java.util.Map.of());
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
        Room room = activeRoomBuilder().playing(false).positionSeconds(10).build();
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));

        roomService.updatePlayback("room-1",
                PlaybackUpdateRequest.builder().action(PlaybackAction.PLAY).positionSeconds(42.0).build());

        assertThat(room.isPlaying()).isTrue();
        assertThat(room.getPositionSeconds()).isEqualTo(42.0);
        verify(outboxRepository).save(argThat(o -> o.getTopic().equals("room.playback.updated")));
    }

    @Test
    void updatePlayback_seekWithPosition_updatesPositionOnly() {
        Room room = activeRoomBuilder().playing(true).build();
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));

        roomService.updatePlayback("room-1",
                PlaybackUpdateRequest.builder().action(PlaybackAction.SEEK).positionSeconds(99.0).build());

        assertThat(room.getPositionSeconds()).isEqualTo(99.0);
        assertThat(room.isPlaying()).isTrue();
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
