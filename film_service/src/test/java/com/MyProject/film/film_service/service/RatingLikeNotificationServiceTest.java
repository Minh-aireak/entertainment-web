package com.MyProject.film.film_service.service;

import com.MyProject.common.redis.ToggleDebounceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RatingLikeNotificationServiceTest {

    @Mock ToggleDebounceService toggleDebounceService;

    RatingLikeNotificationService ratingLikeNotificationService;

    @BeforeEach
    void setUp() throws Exception {
        ratingLikeNotificationService = new RatingLikeNotificationService(toggleDebounceService);
        Field debounceSecondsField = RatingLikeNotificationService.class.getDeclaredField("debounceSeconds");
        debounceSecondsField.setAccessible(true);
        debounceSecondsField.set(ratingLikeNotificationService, 3L);
    }

    @Test
    void scheduleNotification_happyPath_schedulesWithComposedMemberKey() {
        ratingLikeNotificationService.scheduleNotification("rating-1", "user-1");

        verify(toggleDebounceService).schedule("rating:like:notify:queue", "rating-1|user-1", 3L);
    }

    @Test
    void splitMember_wellFormedMember_splitsIntoRatingIdAndUserId() {
        String[] parts = RatingLikeNotificationService.splitMember("rating-1|user-1");

        assertThat(parts).containsExactly("rating-1", "user-1");
    }

    @Test
    void splitMember_userIdContainsDelimiter_limitsSplitToTwoParts() {
        String[] parts = RatingLikeNotificationService.splitMember("rating-1|user-1|extra");

        assertThat(parts).containsExactly("rating-1", "user-1|extra");
    }

    @Test
    void splitMember_missingDelimiter_returnsSingleElementArray() {
        String[] parts = RatingLikeNotificationService.splitMember("malformed");

        assertThat(parts).containsExactly("malformed");
    }
}
