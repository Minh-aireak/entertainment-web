package com.MyProject.friend_service.repository;

import com.MyProject.friend_service.entity.FriendRequest;
import com.MyProject.friend_service.entity.FriendRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FriendRequestRepository extends JpaRepository<FriendRequest, String> {
    Optional<FriendRequest> findByHashFriendRequest(String hashFriendRequest);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE FriendRequest fr SET fr.friendRequestStatus = :friendRequestStatus " +
            "WHERE fr.hashFriendRequest = :hashFriendRequest")
    void updateFriendRequestStatus(@Param("friendRequestStatus") FriendRequestStatus friendRequestStatus,
                                  @Param("hashFriendRequest") String hashFriendRequest);
}
