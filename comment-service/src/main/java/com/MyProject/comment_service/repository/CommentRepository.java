package com.MyProject.comment_service.repository;

import com.MyProject.comment_service.entity.Comment;
import com.MyProject.comment_service.enums.CommentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CommentRepository extends MongoRepository<Comment, String> {
    Page<Comment> findBySourceIdAndParentIdIsNullAndStatusNot(String sourceId, CommentStatus status, Pageable pageable);
}
