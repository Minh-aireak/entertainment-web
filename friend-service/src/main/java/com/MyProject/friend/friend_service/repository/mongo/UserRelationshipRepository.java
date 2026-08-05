package com.MyProject.friend.friend_service.repository.mongo;

import com.MyProject.friend.friend_service.entity.RelationshipStatus;
import com.MyProject.friend.friend_service.entity.UserRelationship;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface UserRelationshipRepository extends MongoRepository<UserRelationship, String> {

    Optional<UserRelationship> findByHashFriend(String hashFriend);

    boolean existsByHashFriend(String hashFriend);

    void deleteByHashFriend(String hashFriend);

    @Query("{ '$and': [ " +
            "{ '$or': [ { 'senderId': ?0 }, { 'receiverId': ?0 } ] }, " +
            "{ 'relationshipStatus': ?1 } " +
            "] }")
    Page<UserRelationship> getListFriend(
            String userId,
            RelationshipStatus relationshipStatus,
            Pageable pageable
    );

    @Query(value = "{ '$and': [ " +
            "{ '$or': [ { 'senderId': ?0 }, { 'receiverId': ?0 } ] }, " +
            "{ 'relationshipStatus': ?1 } " +
            "] }", fields = "{ 'senderId': 1, 'receiverId': 1 }")
    List<UserRelationship> findRelationshipsForSuggestions(
            String userId,
            RelationshipStatus relationshipStatus
    );

    @Query("{ 'hashFriend': ?0 }")
    @Update("{ '$set': { 'relationshipStatus': ?1 } }")
    void updateRelationshipStatus(String hashFriend, RelationshipStatus status);

    @Query(value = """
        {
          $and: [
            { relationshipStatus: ?1 },
            {
              $or: [
                { senderId: ?0 },
                { receiverId: ?0 }
              ]
            }
          ]
        }
        """, count = true)
    long countMyFriends(String userId, RelationshipStatus status);
}
