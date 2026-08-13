package com.MyProject.room_service.repository;

import com.MyProject.room_service.entity.Room;
import com.MyProject.room_service.enums.RoomStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface RoomRepository extends MongoRepository<Room, String> {
    List<Room> findByStatus(RoomStatus status);

    Page<Room> findByStatusAndPublicRoomTrue(RoomStatus status, Pageable pageable);

    Page<Room> findByIdInAndStatus(Collection<String> ids, RoomStatus status, Pageable pageable);

    /** Rooms this user was explicitly invited to but may not have joined yet - lets an invite
     *  surface under "Phòng của tôi" so it can be accepted from there instead of only via the
     *  raw invite link. */
    List<Room> findByInvitedUserIdsContainingAndStatus(String userId, RoomStatus status);
}
