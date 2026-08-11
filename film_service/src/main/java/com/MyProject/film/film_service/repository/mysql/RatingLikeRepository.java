package com.MyProject.film.film_service.repository.mysql;

import com.MyProject.film.film_service.entity.RatingLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RatingLikeRepository extends JpaRepository<RatingLike, String> {
    boolean existsByRatingIdAndUserId(String ratingId, String userId);

    void deleteByRatingIdAndUserId(String ratingId, String userId);
}
