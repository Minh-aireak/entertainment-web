package com.MyProject.friend_service.repository;

import com.MyProject.friend_service.entity.RelationshipStatus;
import com.MyProject.friend_service.entity.UserRelationship;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRelationshipRepository extends JpaRepository<UserRelationship, String> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE UserRelationship ur SET ur.relationshipStatus = :relationshipStatus " +
            "WHERE ur.hashFriend = :hashFriend")
    void updateRelationshipStatus(@Param("relationshipStatus") RelationshipStatus relationshipStatus,
                                  @Param("hashFriend") String hashFriend);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM UserRelationship ur WHERE ur.hashFriend = :hashFriend")
    void deleteByHashFriend(@Param("hashFriend") String hashFriend);

    @Query("SELECT ur FROM UserRelationship ur " +
            "WHERE (ur.userId = :userId OR ur.relatedUserId = :userId) " +
            "AND ur.relationshipStatus = :relationshipStatus")
    Page<UserRelationship> getListFriend(@Param("userId") String userId,
                                         @Param("relationshipStatus") RelationshipStatus relationshipStatus,
                                         Pageable pageable);

    Optional<UserRelationship> findByHashFriend(String hashFriend);
}

