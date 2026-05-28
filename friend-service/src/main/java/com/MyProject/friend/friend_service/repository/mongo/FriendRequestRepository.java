package com.MyProject.friend.friend_service.repository.mongo;

import com.MyProject.friend.friend_service.entity.FriendRequest;
import com.MyProject.friend.friend_service.entity.FriendRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FriendRequestRepository extends MongoRepository<FriendRequest, String> {

    Optional<FriendRequest> findByHashFriendRequest(String hashFriendRequest);

    void deleteByHashFriendRequest(String hashFriendRequest);

    Page<FriendRequest> findByReceiverIdAndFriendRequestStatus(
            String receiverId,
            FriendRequestStatus friendRequestStatus,
            Pageable pageable
    );
}