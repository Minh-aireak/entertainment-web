package com.MyProject.post.post_service.repository;

import com.MyProject.post.post_service.entity.PostLike;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostLikeRepository extends MongoRepository<PostLike, String> {
    boolean existsByPostIdAndUserId(String postId, String userId);

    void deleteByPostIdAndUserId(String postId, String userId);

    List<PostLike> findByPostIdInAndUserId(List<String> postIds, String userId);
}
