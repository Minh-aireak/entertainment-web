package com.MyProject.common_dto.event.dto;

import java.util.List;

public record StatusChangeData(String fromUserId, List<String> listUserIds, String message) {
}
