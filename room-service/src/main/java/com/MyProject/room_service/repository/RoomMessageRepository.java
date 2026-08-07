package com.MyProject.room_service.repository;

import com.MyProject.room_service.entity.RoomMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoomMessageRepository extends MongoRepository<RoomMessage, String> {
    Page<RoomMessage> findByRoomIdOrderByCreatedAtDesc(String roomId, Pageable pageable);
}
