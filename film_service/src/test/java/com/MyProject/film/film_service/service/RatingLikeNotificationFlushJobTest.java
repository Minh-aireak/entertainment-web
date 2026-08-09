package com.MyProject.film.film_service.service;

import com.MyProject.common.redis.ToggleDebounceService;
import com.MyProject.film.film_service.entity.Outbox;
import com.MyProject.film.film_service.entity.Rating;
import com.MyProject.film.film_service.repository.mysql.OutboxRepository;
import com.MyProject.film.film_service.repository.mysql.RatingLikeRepository;
import com.MyProject.film.film_service.repository.mysql.RatingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RatingLikeNotificationFlushJobTest {

    @Mock ToggleDebounceService toggleDebounceService;
    @Mock RatingLikeRepository ratingLikeRepository;
    @Mock RatingRepository ratingRepository;
    @Mock OutboxRepository outboxRepository;

    RatingLikeNotificationFlushJob flushJob;

    @BeforeEach
    void setUp() {
        flushJob = new RatingLikeNotificationFlushJob(toggleDebounceService, ratingLikeRepository,
                ratingRepository, outboxRepository, new ObjectMapper());
    }

    @Test
    void flushPendingLikeNotifications_stillLikedByOtherUser_savesOutboxNotification() {
        when(toggleDebounceService.pollDue(eq(RatingLikeNotificationService.QUEUE_KEY), eq(200)))
                .thenReturn(List.of("rating-1|actor-1"));
        when(ratingLikeRepository.existsByRatingIdAndUserId("rating-1", "actor-1")).thenReturn(true);
        Rating rating = Rating.builder().id("rating-1").userId("owner-1").build();
        when(ratingRepository.findById("rating-1")).thenReturn(Optional.of(rating));

        flushJob.flushPendingLikeNotifications();

        verify(outboxRepository).save(argThat((Outbox o) ->
                o.getAggregateId().equals("rating-1") && o.getTopic().equals("notification.events")));
    }

    @Test
    void flushPendingLikeNotifications_noLongerLiked_skipsNotification() {
        when(toggleDebounceService.pollDue(any(), anyInt())).thenReturn(List.of("rating-1|actor-1"));
        when(ratingLikeRepository.existsByRatingIdAndUserId("rating-1", "actor-1")).thenReturn(false);

        flushJob.flushPendingLikeNotifications();

        verifyNoInteractions(ratingRepository, outboxRepository);
    }

    @Test
    void flushPendingLikeNotifications_ratingDeleted_skipsNotification() {
        when(toggleDebounceService.pollDue(any(), anyInt())).thenReturn(List.of("rating-1|actor-1"));
        when(ratingLikeRepository.existsByRatingIdAndUserId("rating-1", "actor-1")).thenReturn(true);
        when(ratingRepository.findById("rating-1")).thenReturn(Optional.empty());

        flushJob.flushPendingLikeNotifications();

        verifyNoInteractions(outboxRepository);
    }

    @Test
    void flushPendingLikeNotifications_selfLike_skipsNotification() {
        when(toggleDebounceService.pollDue(any(), anyInt())).thenReturn(List.of("rating-1|owner-1"));
        when(ratingLikeRepository.existsByRatingIdAndUserId("rating-1", "owner-1")).thenReturn(true);
        Rating rating = Rating.builder().id("rating-1").userId("owner-1").build();
        when(ratingRepository.findById("rating-1")).thenReturn(Optional.of(rating));

        flushJob.flushPendingLikeNotifications();

        verifyNoInteractions(outboxRepository);
    }

    @Test
    void flushPendingLikeNotifications_malformedMember_skipsWithoutThrowing() {
        when(toggleDebounceService.pollDue(any(), anyInt())).thenReturn(List.of("malformed-no-delimiter"));

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> flushJob.flushPendingLikeNotifications());

        verifyNoInteractions(ratingLikeRepository, ratingRepository, outboxRepository);
    }

    @Test
    void flushPendingLikeNotifications_noDueMembers_isNoOp() {
        when(toggleDebounceService.pollDue(any(), anyInt())).thenReturn(List.of());

        flushJob.flushPendingLikeNotifications();

        verifyNoInteractions(ratingLikeRepository, ratingRepository, outboxRepository);
    }

    @Test
    void flushPendingLikeNotifications_oneMemberThrows_stillProcessesRemainingMembers() {
        when(toggleDebounceService.pollDue(any(), anyInt()))
                .thenReturn(List.of("rating-1|actor-1", "rating-2|actor-2"));
        when(ratingLikeRepository.existsByRatingIdAndUserId("rating-1", "actor-1"))
                .thenThrow(new RuntimeException("db down"));
        when(ratingLikeRepository.existsByRatingIdAndUserId("rating-2", "actor-2")).thenReturn(true);
        Rating rating2 = Rating.builder().id("rating-2").userId("owner-2").build();
        when(ratingRepository.findById("rating-2")).thenReturn(Optional.of(rating2));

        flushJob.flushPendingLikeNotifications();

        verify(outboxRepository).save(argThat((Outbox o) -> o.getAggregateId().equals("rating-2")));
    }
}
