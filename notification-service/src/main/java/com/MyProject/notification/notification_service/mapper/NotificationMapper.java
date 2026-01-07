package com.MyProject.notification.notification_service.mapper;

import com.MyProject.common.dto.response.NotificationResponse;
import com.MyProject.notification.notification_service.entity.Notification;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface NotificationMapper {

    @Mapping(ignore = true, target = "type")
    @Mapping(ignore = true, target = "displayNameSender")
    @Mapping(ignore = true, target = "avatarSender")
    @Mapping(ignore = true, target = "isRead")
    NotificationResponse toNotificationResponse(Notification notification);
}
