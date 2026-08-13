package com.MyProject.room_service.service;

import com.MyProject.room_service.configuration.RoomProperties;
import com.MyProject.room_service.entity.Room;
import com.MyProject.room_service.enums.RoomStatus;
import com.MyProject.room_service.repository.RoomParticipantRepository;
import com.MyProject.room_service.repository.RoomRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoomPresenceCleanupJob {
    RoomRepository roomRepository;
    RoomParticipantRepository roomParticipantRepository;
    RoomProperties roomProperties;
    RoomService roomService;

    @Scheduled(fixedDelayString = "${app.room.presence-cleanup-interval-ms:30000}")
    public void cleanupDisconnectedParticipants() {
        Instant cutoff = Instant.now().minus(roomProperties.getPresenceDisconnectGrace());
        for (Room room : roomRepository.findByStatus(RoomStatus.ACTIVE)) {
            roomParticipantRepository.findByRoomId(room.getId()).forEach(participant -> {
                try {
                    roomService.expireDisconnectedParticipant(room.getId(), participant.getUserId(), cutoff);
                } catch (Exception exception) {
                    log.error("Failed to expire disconnected participant {} from room {}",
                            participant.getUserId(), room.getId(), exception);
                }
            });
        }
    }
}
