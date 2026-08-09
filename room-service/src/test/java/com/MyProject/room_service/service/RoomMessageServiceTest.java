package com.MyProject.room_service.service;

import com.MyProject.common.dto.response.UserProfileResponse;
import com.MyProject.common.security.SecurityUtils;
import com.MyProject.room_service.dto.request.RoomMessageCreateRequest;
import com.MyProject.room_service.dto.response.RoomMessageResponse;
import com.MyProject.room_service.entity.Outbox;
import com.MyProject.room_service.entity.Room;
import com.MyProject.room_service.entity.RoomMessage;
import com.MyProject.room_service.enums.ErrorCode;
import com.MyProject.room_service.enums.RoomStatus;
import com.MyProject.room_service.exception.AppException;
import com.MyProject.room_service.mapper.RoomMapper;
import com.MyProject.room_service.repository.OutboxRepository;
import com.MyProject.room_service.repository.RoomMessageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomMessageServiceTest {

    @Mock RoomService roomService;
    @Mock RoomMessageRepository roomMessageRepository;
    @Mock RoomMapper roomMapper;
    @Mock OutboxRepository outboxRepository;

    RoomMessageService roomMessageService;
    MockedStatic<SecurityUtils> securityUtils;

    @BeforeEach
    void setUp() {
        roomMessageService = new RoomMessageService(roomService, roomMessageRepository, roomMapper,
                outboxRepository, new ObjectMapper().registerModule(new JavaTimeModule()));
        securityUtils = mockStatic(SecurityUtils.class);
        securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn("user-1");
    }

    @AfterEach
    void tearDown() {
        securityUtils.close();
    }

    private Room activeRoom() {
        return Room.builder().id("room-1").status(RoomStatus.ACTIVE).build();
    }

    @Test
    void listMessages_notAParticipant_propagatesExceptionFromRoomService() {
        when(roomService.requireActiveRoomForParticipant("room-1", "user-1"))
                .thenThrow(new AppException(ErrorCode.NOT_A_PARTICIPANT));

        assertThatThrownBy(() -> roomMessageService.listMessages("room-1", 1, 30))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_A_PARTICIPANT);

        verifyNoInteractions(roomMessageRepository);
    }

    @Test
    void listMessages_happyPath_returnsMappedPage() {
        when(roomService.requireActiveRoomForParticipant("room-1", "user-1")).thenReturn(activeRoom());
        RoomMessage message = RoomMessage.builder().id("m-1").roomId("room-1").content("hi").build();
        Page<RoomMessage> page = new PageImpl<>(java.util.List.of(message));
        when(roomMessageRepository.findByRoomIdOrderByCreatedAtDesc(eq("room-1"), any(Pageable.class))).thenReturn(page);
        when(roomMapper.toRoomMessageResponse(message)).thenReturn(
                RoomMessageResponse.builder().id("m-1").content("hi").build());

        var response = roomMessageService.listMessages("room-1", 1, 30);

        assertThat(response.getData()).hasSize(1);
        assertThat(response.getData().get(0).getContent()).isEqualTo("hi");
    }

    @Test
    void listMessages_emptyRoom_returnsEmptyPage() {
        when(roomService.requireActiveRoomForParticipant("room-1", "user-1")).thenReturn(activeRoom());
        when(roomMessageRepository.findByRoomIdOrderByCreatedAtDesc(eq("room-1"), any(Pageable.class)))
                .thenReturn(Page.empty());

        var response = roomMessageService.listMessages("room-1", 1, 30);

        assertThat(response.getData()).isEmpty();
    }

    @Test
    void sendMessage_happyPath_savesMessageAndOutboxEvent() {
        when(roomService.requireActiveRoomForParticipant("room-1", "user-1")).thenReturn(activeRoom());
        when(roomService.resolveProfiles(Set.of("user-1"))).thenReturn(
                Map.of("user-1", UserProfileResponse.builder().userId("user-1").displayName("Alice").avatar("a.png").build()));
        RoomMessage saved = RoomMessage.builder().id("m-1").roomId("room-1").senderId("user-1").content("hello").build();
        when(roomMessageRepository.save(any(RoomMessage.class))).thenReturn(saved);
        when(roomMapper.toRoomMessageResponse(saved)).thenReturn(
                RoomMessageResponse.builder().id("m-1").roomId("room-1").content("hello").build());

        RoomMessageResponse response = roomMessageService.sendMessage("room-1", RoomMessageCreateRequest.builder().content("  hello  ").build());

        assertThat(response.getContent()).isEqualTo("hello");
        verify(roomMessageRepository).save(argThat(m -> m.getSenderName().equals("Alice") && m.getContent().equals("hello")));
        verify(outboxRepository).save(argThat((Outbox o) -> o.getTopic().equals("room.message.created")));
    }

    @Test
    void sendMessage_nullRequest_throwsMessageContentEmpty() {
        when(roomService.requireActiveRoomForParticipant("room-1", "user-1")).thenReturn(activeRoom());

        assertThatThrownBy(() -> roomMessageService.sendMessage("room-1", null))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.MESSAGE_CONTENT_EMPTY);

        verifyNoInteractions(roomMessageRepository);
    }

    @Test
    void sendMessage_blankContent_throwsMessageContentEmpty() {
        when(roomService.requireActiveRoomForParticipant("room-1", "user-1")).thenReturn(activeRoom());

        assertThatThrownBy(() -> roomMessageService.sendMessage("room-1", RoomMessageCreateRequest.builder().content("   ").build()))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.MESSAGE_CONTENT_EMPTY);
    }

    @Test
    void sendMessage_contentTooLong_throwsMessageContentTooLong() {
        when(roomService.requireActiveRoomForParticipant("room-1", "user-1")).thenReturn(activeRoom());
        String tooLong = "a".repeat(1001);

        assertThatThrownBy(() -> roomMessageService.sendMessage("room-1", RoomMessageCreateRequest.builder().content(tooLong).build()))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.MESSAGE_CONTENT_TOO_LONG);

        verifyNoInteractions(roomMessageRepository);
    }

    @Test
    void sendMessage_contentExactlyAtLimit_succeeds() {
        when(roomService.requireActiveRoomForParticipant("room-1", "user-1")).thenReturn(activeRoom());
        when(roomService.resolveProfiles(any())).thenReturn(Map.of());
        String exactly1000 = "a".repeat(1000);
        RoomMessage saved = RoomMessage.builder().id("m-1").content(exactly1000).build();
        when(roomMessageRepository.save(any())).thenReturn(saved);
        when(roomMapper.toRoomMessageResponse(saved)).thenReturn(RoomMessageResponse.builder().id("m-1").content(exactly1000).build());

        RoomMessageResponse response = roomMessageService.sendMessage("room-1", RoomMessageCreateRequest.builder().content(exactly1000).build());

        assertThat(response.getContent()).hasSize(1000);
    }

    @Test
    void sendMessage_senderProfileMissing_savesMessageWithNullSenderNameAndAvatar() {
        when(roomService.requireActiveRoomForParticipant("room-1", "user-1")).thenReturn(activeRoom());
        when(roomService.resolveProfiles(Set.of("user-1"))).thenReturn(Map.of());
        RoomMessage saved = RoomMessage.builder().id("m-1").content("hi").build();
        when(roomMessageRepository.save(any())).thenReturn(saved);
        when(roomMapper.toRoomMessageResponse(saved)).thenReturn(RoomMessageResponse.builder().id("m-1").content("hi").build());

        roomMessageService.sendMessage("room-1", RoomMessageCreateRequest.builder().content("hi").build());

        verify(roomMessageRepository).save(argThat(m -> m.getSenderName() == null && m.getSenderAvatar() == null));
    }

    @Test
    void sendMessage_outboxSerializationFails_throwsOutboxSaveFailed() {
        when(roomService.requireActiveRoomForParticipant("room-1", "user-1")).thenReturn(activeRoom());
        when(roomService.resolveProfiles(any())).thenReturn(Map.of());
        RoomMessage saved = RoomMessage.builder().id("m-1").content("hi").build();
        when(roomMessageRepository.save(any())).thenReturn(saved);
        when(roomMapper.toRoomMessageResponse(saved)).thenReturn(RoomMessageResponse.builder().id("m-1").content("hi").build());
        when(outboxRepository.save(any())).thenThrow(new RuntimeException("mongo down"));

        assertThatThrownBy(() -> roomMessageService.sendMessage("room-1", RoomMessageCreateRequest.builder().content("hi").build()))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.OUTBOX_SAVE_FAILED);
    }

    @Test
    void sendMessage_roomClosed_propagatesExceptionFromRoomService() {
        when(roomService.requireActiveRoomForParticipant("room-1", "user-1"))
                .thenThrow(new AppException(ErrorCode.ROOM_CLOSED));

        assertThatThrownBy(() -> roomMessageService.sendMessage("room-1", RoomMessageCreateRequest.builder().content("hi").build()))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.ROOM_CLOSED);

        verifyNoInteractions(roomMessageRepository, outboxRepository);
    }
}
