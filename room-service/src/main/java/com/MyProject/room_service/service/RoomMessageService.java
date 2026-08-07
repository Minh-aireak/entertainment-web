package com.MyProject.room_service.service;

import com.MyProject.room_service.dto.request.RoomMessageCreateRequest;
import com.MyProject.room_service.dto.response.RoomMessageResponse;
import com.MyProject.room_service.entity.Outbox;
import com.MyProject.room_service.entity.Room;
import com.MyProject.room_service.entity.RoomMessage;
import com.MyProject.room_service.enums.ErrorCode;
import com.MyProject.room_service.exception.AppException;
import com.MyProject.room_service.mapper.RoomMapper;
import com.MyProject.room_service.repository.OutboxRepository;
import com.MyProject.room_service.repository.RoomMessageRepository;
import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.common.dto.response.UserProfileResponse;
import com.MyProject.common.security.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoomMessageService {
    private static final int MAX_CONTENT_LENGTH = 1000;

    RoomService roomService;
    RoomMessageRepository roomMessageRepository;
    RoomMapper roomMapper;
    OutboxRepository outboxRepository;
    ObjectMapper objectMapper;

    public PageResponse<RoomMessageResponse> listMessages(String roomId, int page, int size) {
        String userId = SecurityUtils.getCurrentUserId();
        roomService.requireActiveRoomForParticipant(roomId, userId);

        Pageable pageable = PageRequest.of(Math.max(0, page - 1), size);
        Page<RoomMessage> messagePage = roomMessageRepository.findByRoomIdOrderByCreatedAtDesc(roomId, pageable);

        return PageResponse.<RoomMessageResponse>builder()
                .data(messagePage.getContent().stream().map(roomMapper::toRoomMessageResponse).collect(Collectors.toList()))
                .currentPage(page)
                .pageSize(size)
                .totalElement(messagePage.getTotalElements())
                .totalPages(messagePage.getTotalPages())
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public RoomMessageResponse sendMessage(String roomId, RoomMessageCreateRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        Room room = roomService.requireActiveRoomForParticipant(roomId, userId);

        String content = validateAndNormalizeContent(request != null ? request.getContent() : null);

        UserProfileResponse senderProfile = roomService.resolveProfiles(Set.of(userId)).get(userId);

        RoomMessage message = RoomMessage.builder()
                .roomId(room.getId())
                .senderId(userId)
                .senderName(senderProfile != null ? senderProfile.getDisplayName() : null)
                .senderAvatar(senderProfile != null ? senderProfile.getAvatar() : null)
                .content(content)
                .build();

        message = roomMessageRepository.save(message);

        RoomMessageResponse response = roomMapper.toRoomMessageResponse(message);

        // Debezium CDC reads the "outbox" collection and relays this onto Kafka topic
        // "room.message.created" -> socket-service broadcasts "room:message" to the room.
        try {
            outboxRepository.save(Outbox.builder()
                    .aggregateId(room.getId())
                    .topic("room.message.created")
                    .payload(objectMapper.writeValueAsString(response))
                    .build());
        } catch (Exception e) {
            log.error("Failed to save outbox for room message", e);
            throw new AppException(ErrorCode.OUTBOX_SAVE_FAILED);
        }

        return response;
    }

    private String validateAndNormalizeContent(String content) {
        if (content == null) {
            throw new AppException(ErrorCode.MESSAGE_CONTENT_EMPTY);
        }
        String trimmed = content.trim();
        if (trimmed.isEmpty()) {
            throw new AppException(ErrorCode.MESSAGE_CONTENT_EMPTY);
        }
        if (trimmed.codePointCount(0, trimmed.length()) > MAX_CONTENT_LENGTH) {
            throw new AppException(ErrorCode.MESSAGE_CONTENT_TOO_LONG);
        }
        return trimmed;
    }
}
