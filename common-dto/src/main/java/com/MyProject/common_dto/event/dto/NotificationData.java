package com.MyProject.common_dto.event.dto;

import java.util.List;

public record NotificationData(NotificationResponse notificationResponse, List<String> userIds) {
}
