package com.MyProject.common.dto;

import com.MyProject.common.dto.response.NotificationResponse;

import java.util.List;

public record NotificationSocketData(NotificationResponse notificationResponse, List<String> userIds) {
}
