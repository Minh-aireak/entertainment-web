package com.MyProject.film.film_service.service;

import com.MyProject.common.security.SecurityUtils;
import com.MyProject.film.film_service.dto.response.RatingLikeResponse;
import com.MyProject.film.film_service.entity.Rating;
import com.MyProject.film.film_service.enums.ErrorCode;
import com.MyProject.film.film_service.exception.AppException;
import com.MyProject.film.film_service.repository.mysql.RatingLikeRepository;
import com.MyProject.film.film_service.repository.mysql.RatingRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RatingLikeServiceTest {

    private static final String USER_ID = "user-1";

    @Mock RatingRepository ratingRepository;
    @Mock RatingLikeRepository ratingLikeRepository;
    @Mock RatingLikeNotificationService ratingLikeNotificationService;

    RatingLikeService ratingLikeService;
    MockedStatic<SecurityUtils> securityUtils;

    @BeforeEach
    void setUp() {
        ratingLikeService = new RatingLikeService(ratingRepository, ratingLikeRepository, ratingLikeNotificationService);
        securityUtils = mockStatic(SecurityUtils.class);
        securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);
    }

    @AfterEach
    void tearDown() {
        securityUtils.close();
    }

    @Test
    void toggleLike_ratingNotFound_throws() {
        when(ratingRepository.findById("r-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ratingLikeService.toggleLike("r-1"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.RATING_NOT_FOUND);
    }

    @Test
    void toggleLike_notYetLiked_addsLikeAndIncrementsCount() {
        Rating rating = Rating.builder().id("r-1").userId("owner-2").likeCount(3).build();
        when(ratingRepository.findById("r-1")).thenReturn(Optional.of(rating));
        when(ratingLikeRepository.existsByRatingIdAndUserId("r-1", USER_ID)).thenReturn(false);

        RatingLikeResponse response = ratingLikeService.toggleLike("r-1");

        assertThat(response.isLiked()).isTrue();
        assertThat(response.getLikeCount()).isEqualTo(4);
        verify(ratingLikeRepository).save(any());
        verify(ratingLikeRepository, never()).deleteByRatingIdAndUserId(any(), any());
        verify(ratingRepository).save(rating);
    }

    @Test
    void toggleLike_alreadyLiked_removesLikeAndDecrementsCount() {
        Rating rating = Rating.builder().id("r-1").userId("owner-2").likeCount(3).build();
        when(ratingRepository.findById("r-1")).thenReturn(Optional.of(rating));
        when(ratingLikeRepository.existsByRatingIdAndUserId("r-1", USER_ID)).thenReturn(true);

        RatingLikeResponse response = ratingLikeService.toggleLike("r-1");

        assertThat(response.isLiked()).isFalse();
        assertThat(response.getLikeCount()).isEqualTo(2);
        verify(ratingLikeRepository).deleteByRatingIdAndUserId("r-1", USER_ID);
        verify(ratingLikeRepository, never()).save(any());
    }

    @Test
    void toggleLike_likeCountNeverGoesNegative() {
        Rating rating = Rating.builder().id("r-1").userId("owner-2").likeCount(0).build();
        when(ratingRepository.findById("r-1")).thenReturn(Optional.of(rating));
        when(ratingLikeRepository.existsByRatingIdAndUserId("r-1", USER_ID)).thenReturn(true);

        RatingLikeResponse response = ratingLikeService.toggleLike("r-1");

        assertThat(response.getLikeCount()).isZero();
    }

    @Test
    void toggleLike_ownRating_doesNotScheduleNotification() {
        Rating rating = Rating.builder().id("r-1").userId(USER_ID).likeCount(0).build();
        when(ratingRepository.findById("r-1")).thenReturn(Optional.of(rating));
        when(ratingLikeRepository.existsByRatingIdAndUserId("r-1", USER_ID)).thenReturn(false);

        ratingLikeService.toggleLike("r-1");

        verifyNoInteractions(ratingLikeNotificationService);
    }

    @Test
    void toggleLike_othersRating_schedulesNotification() {
        Rating rating = Rating.builder().id("r-1").userId("owner-2").likeCount(0).build();
        when(ratingRepository.findById("r-1")).thenReturn(Optional.of(rating));
        when(ratingLikeRepository.existsByRatingIdAndUserId("r-1", USER_ID)).thenReturn(false);

        ratingLikeService.toggleLike("r-1");

        verify(ratingLikeNotificationService).scheduleNotification("r-1", USER_ID);
    }
}
