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

@Repository
public interface UserRelationshipRepository extends MongoRepository<UserRelationship, String> {

    Optional<UserRelationship> findByHashFriend(String hashFriend);

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

    @Update(value = "{ 'hashFriend': ?0 }",
            update = "{ '$set': { 'relationshipStatus': ?1 } }")
    void updateRelationshipStatus(String hashFriend, RelationshipStatus status);

    @Query("""
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
        """)
    int countMyFriends(String userId, RelationshipStatus status);
}