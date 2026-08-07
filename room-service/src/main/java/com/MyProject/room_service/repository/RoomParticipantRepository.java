package com.MyProject.room_service.repository;

import com.MyProject.room_service.entity.RoomParticipant;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomParticipantRepository extends MongoRepository<RoomParticipant, String> {
    Optional<RoomParticipant> findByRoomIdAndUserId(String roomId, String userId);

    boolean existsByRoomIdAndUserId(String roomId, String userId);

    List<RoomParticipant> findByRoomId(String roomId);

    List<RoomParticipant> findByUserIdOrderByJoinedAtDesc(String userId);

    void deleteByRoomId(String roomId);

    void deleteByRoomIdAndUserId(String roomId, String userId);
}
