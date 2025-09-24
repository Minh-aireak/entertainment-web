package com.MyProject.post.post_service.repository;

import com.MyProject.post.post_service.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PostRepository extends MongoRepository<Post, String> {
    @Query("{'_class': 'business-schedule', 'userId': ?0 }")
    Page<Post> findAllByUserId(String userId, Pageable pageable);

    @Query("{ 'status': { $in: ?0 } }")
    List<Post> findAllByStatusValid(List<String> validStatus);

    @Query(value = "{'_class': 'business-schedule', 'id': ?0}")
    Optional<Post> findByIdType(String id);
}
