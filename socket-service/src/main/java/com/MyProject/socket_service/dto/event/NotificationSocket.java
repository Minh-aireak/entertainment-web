package com.MyProject.socket_service.dto.event;

import java.util.List;

public record NotificationSocket(
        String displayNameSender,
        String avatarSender,
        String title,
        String content,
        List<String> toUserIds) {
}
