package com.MyProject.friend_service.repository;

import com.MyProject.friend_service.entity.RelationshipStatus;
import com.MyProject.friend_service.entity.UserRelationship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRelationshipRepository extends JpaRepository<UserRelationship, String> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE UserRelationship ur SET ur.friendRequestStatus = :friendRequestStatus " +
            "WHERE ur.hashFriend = :hashFriend")
    void updateRelationshipStatus(@Param("relationshipStatus") RelationshipStatus relationshipStatus,
                                  @Param("hashFriend") String hashFriend);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM UserRelationship ur WHERE ur.hashFriend = :hashFriend")
    void deleteByHashFriend(@Param("hashFriend") String hashFriend);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("SELECT ur FROM UserRelationship ur " +
            "WHERE (ur.userId = :userId OR ur.relatedUserId = :userId) " +
            "AND ur.relationshipStatus = :relationshipStatus")
    List<UserRelationship> findAllFriendsOf(@Param("userId") String userId,
                                            @Param("relationshipStatus") RelationshipStatus relationshipStatus);
}

