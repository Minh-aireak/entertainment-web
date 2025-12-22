package com.MyProject.friend_service.repository;

import com.MyProject.friend_service.entity.FriendRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FriendRequestRepository extends JpaRepository<FriendRequest, String> {
    Optional<FriendRequest> findByHashFriendRequest(String hashFriendRequest);

    void deleteByHashFriendRequest(String hashFriendRequest);

    @Query("SELECT fr FROM FriendRequest fr " +
            "WHERE fr.toUserId = :userId")
    Page<FriendRequest> getListFriendRequests(@Param("userId") String userId,
                                              Pageable pageable);
}
