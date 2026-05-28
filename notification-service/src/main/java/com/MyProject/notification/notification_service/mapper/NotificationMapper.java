package com.MyProject.notification.notification_service.mapper;

import com.MyProject.notification.notification_service.dto.response.NotificationResponse;
import com.MyProject.notification.notification_service.entity.Notification;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface NotificationMapper {

    @Mapping(ignore = true, target = "type")
    @Mapping(ignore = true, target = "createdAt")
    NotificationResponse toNotificationResponse(Notification notification);
}
