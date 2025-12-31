package com.MyProject.common_dto.event.dto;

import com.MyProject.common_dto.event.dto.response.NotificationResponse;

import java.util.List;

public record NotificationSocketData(NotificationResponse notificationResponse, List<String> userIds) {
}
