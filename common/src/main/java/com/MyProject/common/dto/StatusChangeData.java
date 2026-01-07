package com.MyProject.common.dto;

import java.util.List;

public record StatusChangeData(String fromUserId, List<String> listUserIds, String message) {
}
