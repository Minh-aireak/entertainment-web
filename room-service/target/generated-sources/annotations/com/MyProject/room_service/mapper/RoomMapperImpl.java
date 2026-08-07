package com.MyProject.room_service.mapper;

import com.MyProject.room_service.dto.response.RoomMessageResponse;
import com.MyProject.room_service.entity.RoomMessage;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    comments = "version: 1.6.3, compiler: javac, environment: Java 25.0.1 (Oracle Corporation)"
)
@Component
public class RoomMapperImpl implements RoomMapper {

    @Override
    public RoomMessageResponse toRoomMessageResponse(RoomMessage message) {
        if ( message == null ) {
            return null;
        }

        RoomMessageResponse.RoomMessageResponseBuilder roomMessageResponse = RoomMessageResponse.builder();

        roomMessageResponse.id( message.getId() );
        roomMessageResponse.roomId( message.getRoomId() );
        roomMessageResponse.senderId( message.getSenderId() );
        roomMessageResponse.senderName( message.getSenderName() );
        roomMessageResponse.senderAvatar( message.getSenderAvatar() );
        roomMessageResponse.content( message.getContent() );
        roomMessageResponse.createdAt( message.getCreatedAt() );

        return roomMessageResponse.build();
    }
}
