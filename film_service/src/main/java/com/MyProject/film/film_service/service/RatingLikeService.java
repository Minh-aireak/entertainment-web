package com.MyProject.film.film_service.service;

import com.MyProject.common.security.SecurityUtils;
import com.MyProject.film.film_service.dto.response.RatingLikeResponse;
import com.MyProject.film.film_service.entity.Rating;
import com.MyProject.film.film_service.entity.RatingLike;
import com.MyProject.film.film_service.enums.ErrorCode;
import com.MyProject.film.film_service.exception.AppException;
import com.MyProject.film.film_service.repository.mysql.RatingLikeRepository;
import com.MyProject.film.film_service.repository.mysql.RatingRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RatingLikeService {
    RatingRepository ratingRepository;
    RatingLikeRepository ratingLikeRepository;
    RatingLikeNotificationService ratingLikeNotificationService;

    @Transactional
    public RatingLikeResponse toggleLike(String ratingId) {
        String userId = SecurityUtils.getCurrentUserId();
        Rating rating = ratingRepository.findById(ratingId)
                .orElseThrow(() -> new AppException(ErrorCode.RATING_NOT_FOUND));

        boolean alreadyLiked = ratingLikeRepository.existsByRatingIdAndUserId(ratingId, userId);
        if (alreadyLiked) {
            ratingLikeRepository.deleteByRatingIdAndUserId(ratingId, userId);
            rating.setLikeCount(Math.max(0, rating.getLikeCount() - 1));
        } else {
            ratingLikeRepository.save(RatingLike.builder()
                    .rating(rating)
                    .userId(userId)
                    .createdAt(LocalDateTime.now())
                    .build());
            rating.setLikeCount(rating.getLikeCount() + 1);
        }
        ratingRepository.save(rating);

        boolean isNowLiked = !alreadyLiked;
        if (!userId.equals(rating.getUserId())) {
            ratingLikeNotificationService.scheduleNotification(ratingId, userId);
        }

        return RatingLikeResponse.builder()
                .liked(isNowLiked)
                .likeCount(rating.getLikeCount())
                .build();
    }
}
