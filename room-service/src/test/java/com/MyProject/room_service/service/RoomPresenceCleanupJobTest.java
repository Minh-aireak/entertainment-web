package com.MyProject.room_service.service;

import com.MyProject.room_service.configuration.RoomProperties;
import com.MyProject.room_service.entity.Room;
import com.MyProject.room_service.entity.RoomParticipant;
import com.MyProject.room_service.enums.ParticipantRole;
import com.MyProject.room_service.enums.RoomStatus;
import com.MyProject.room_service.repository.RoomParticipantRepository;
import com.MyProject.room_service.repository.RoomRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomPresenceCleanupJobTest {
    @Mock RoomRepository roomRepository;
    @Mock RoomParticipantRepository roomParticipantRepository;
    @Mock RoomService roomService;

    @Test
    void cleanupDisconnectedParticipants_checksEveryParticipantInActiveRooms() {
        RoomProperties properties = new RoomProperties();
        properties.setPresenceDisconnectGrace(Duration.ofMinutes(2));
        RoomPresenceCleanupJob job = new RoomPresenceCleanupJob(
                roomRepository, roomParticipantRepository, properties, roomService);
        Room room = Room.builder().id("room-1").status(RoomStatus.ACTIVE).build();
        RoomParticipant host = RoomParticipant.builder()
                .roomId("room-1").userId("host-1").role(ParticipantRole.HOST)
                .joinedAt(Instant.now()).build();
        RoomParticipant viewer = RoomParticipant.builder()
                .roomId("room-1").userId("viewer-1").role(ParticipantRole.VIEWER)
                .joinedAt(Instant.now()).build();
        when(roomRepository.findByStatus(RoomStatus.ACTIVE)).thenReturn(List.of(room));
        when(roomParticipantRepository.findByRoomId("room-1")).thenReturn(List.of(host, viewer));

        job.cleanupDisconnectedParticipants();

        verify(roomService).expireDisconnectedParticipant(eq("room-1"), eq("host-1"), org.mockito.ArgumentMatchers.any());
        verify(roomService).expireDisconnectedParticipant(eq("room-1"), eq("viewer-1"), org.mockito.ArgumentMatchers.any());
    }
}
