package com.MyProject.room_service.mapper;

import com.MyProject.room_service.dto.response.RoomMessageResponse;
import com.MyProject.room_service.entity.RoomMessage;
import org.mapstruct.Mapper;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface RoomMapper {
    RoomMessageResponse toRoomMessageResponse(RoomMessage message);
}
