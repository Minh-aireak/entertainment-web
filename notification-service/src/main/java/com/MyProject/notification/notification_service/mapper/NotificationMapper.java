package com.MyProject.notification.notification_service.mapper;

import com.MyProject.common_dto.event.dto.NotificationResponse;
import com.MyProject.notification.notification_service.entity.Notification;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface NotificationMapper {
    @Mapping(ignore = true, target = "type")
    NotificationResponse toNotificationResponse(Notification notification);
}
