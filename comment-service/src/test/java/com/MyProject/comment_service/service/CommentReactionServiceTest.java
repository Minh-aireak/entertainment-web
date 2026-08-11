package com.MyProject.comment_service.service;

import com.MyProject.comment_service.dto.response.CommentReactionResponse;
import com.MyProject.comment_service.entity.Comment;
import com.MyProject.comment_service.enums.CommentReactionType;
import com.MyProject.comment_service.enums.ErrorCode;
import com.MyProject.comment_service.exception.AppException;
import com.MyProject.comment_service.repository.CommentRepository;
import com.MyProject.comment_service.repository.OutboxRepository;
import com.MyProject.common.redis.RedisService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentReactionServiceTest {

    @Mock CommentRepository commentRepository;
    @Mock RedisService redisService;
    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock HashOperations<String, Object, Object> hashOperations;
    @Mock CommentReactionNotificationService commentReactionNotificationService;
    @Mock OutboxRepository outboxRepository;

    CommentReactionService commentReactionService;

    @BeforeEach
    void setUp() {
        commentReactionService = new CommentReactionService(commentRepository, redisService, redisTemplate,
                commentReactionNotificationService, outboxRepository, new ObjectMapper());
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        lenient().when(redisService.hashGetAll(anyString())).thenReturn(Map.of());
    }

    @Test
    void react_nullType_throwsInvalidReactionType() {
        assertThatThrownBy(() -> commentReactionService.react("c-1", "user-1", null))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REACTION_TYPE);
        verifyNoInteractions(commentRepository);
    }

    @Test
    void react_commentNotFound_throwsCommentNotFound() {
        when(commentRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentReactionService.react("missing", "user-1", CommentReactionType.LIKE))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.COMMENT_NOT_FOUND);
    }

    @Test
    void react_sameReactionClickedAgain_togglesOff() {
        Comment comment = Comment.builder().id("c-1").userId("owner").build();
        when(commentRepository.findById("c-1")).thenReturn(Optional.of(comment));
        when(hashOperations.get("comment:reaction:user:c-1", "user-1")).thenReturn("LIKE");

        CommentReactionResponse response = commentReactionService.react("c-1", "user-1", CommentReactionType.LIKE);

        assertThat(response.getMyReaction()).isNull();
        verify(hashOperations).delete("comment:reaction:user:c-1", "user-1");
        verify(redisService).hashIncrement("comment:reaction:count:c-1", "LIKE", -1);
    }

    @Test
    void react_switchFromLoveToLike_decrementsOldIncrementsNew() {
        Comment comment = Comment.builder().id("c-1").userId("owner").build();
        when(commentRepository.findById("c-1")).thenReturn(Optional.of(comment));
        when(hashOperations.get("comment:reaction:user:c-1", "user-1")).thenReturn("LOVE");

        CommentReactionResponse response = commentReactionService.react("c-1", "user-1", CommentReactionType.LIKE);

        assertThat(response.getMyReaction()).isEqualTo("LIKE");
        verify(redisService).hashIncrement("comment:reaction:count:c-1", "LOVE", -1);
        verify(redisService).hashIncrement("comment:reaction:count:c-1", "LIKE", 1);
        verify(hashOperations).put("comment:reaction:user:c-1", "user-1", "LIKE");
    }

    @Test
    void react_firstReaction_setsNewReactionWithoutDecrementingAnything() {
        Comment comment = Comment.builder().id("c-1").userId("owner").build();
        when(commentRepository.findById("c-1")).thenReturn(Optional.of(comment));
        when(hashOperations.get("comment:reaction:user:c-1", "user-1")).thenReturn(null);

        commentReactionService.react("c-1", "user-1", CommentReactionType.LOVE);

        verify(redisService, never()).hashIncrement(eq("comment:reaction:count:c-1"), anyString(), eq(-1L));
        verify(redisService).hashIncrement("comment:reaction:count:c-1", "LOVE", 1);
    }

    @Test
    void react_publishesRealtimeEventWithSourceAndCounts() {
        Comment comment = Comment.builder().id("c-1").sourceId("post-1").parentId("parent-1").userId("owner").build();
        when(commentRepository.findById("c-1")).thenReturn(Optional.of(comment));
        when(hashOperations.get("comment:reaction:user:c-1", "user-1")).thenReturn(null);
        when(redisService.hashGetAll("comment:reaction:count:c-1"))
                .thenReturn(Map.of("LIKE", "4", "LOVE", "2"));

        commentReactionService.react("c-1", "user-1", CommentReactionType.LIKE);

        verify(outboxRepository).save(argThat(outbox -> outbox.getTopic().equals("comment.reactions")
                && outbox.getPayload().contains("\"sourceId\":\"post-1\"")
                && outbox.getPayload().contains("\"likeCount\":4")));
    }

    @Test
    void react_onOwnComment_doesNotScheduleNotification() {
        Comment comment = Comment.builder().id("c-1").userId("user-1").build();
        when(commentRepository.findById("c-1")).thenReturn(Optional.of(comment));
        when(hashOperations.get(anyString(), any())).thenReturn(null);

        commentReactionService.react("c-1", "user-1", CommentReactionType.LIKE);

        verifyNoInteractions(commentReactionNotificationService);
    }

    @Test
    void react_onOthersComment_schedulesNotification() {
        Comment comment = Comment.builder().id("c-1").userId("owner-2").build();
        when(commentRepository.findById("c-1")).thenReturn(Optional.of(comment));
        when(hashOperations.get(anyString(), any())).thenReturn(null);

        commentReactionService.react("c-1", "user-1", CommentReactionType.LIKE);

        verify(commentReactionNotificationService).scheduleNotification("c-1", "user-1");
    }

    @Test
    void getCounts_cacheEmpty_returnsFallbackCounts() {
        when(redisService.hashGetAll("comment:reaction:count:c-1")).thenReturn(Map.of());

        int[] counts = commentReactionService.getCounts("c-1", 3, 7);

        assertThat(counts).containsExactly(3, 7);
    }

    @Test
    void getCounts_cacheHasValues_returnsLiveCounts() {
        when(redisService.hashGetAll("comment:reaction:count:c-1"))
                .thenReturn(Map.of("LIKE", "5", "LOVE", "2"));

        int[] counts = commentReactionService.getCounts("c-1", 0, 0);

        assertThat(counts).containsExactly(5, 2);
    }

    @Test
    void getCounts_negativeStoredValue_clampsToZero() {
        when(redisService.hashGetAll("comment:reaction:count:c-1")).thenReturn(Map.of("LIKE", "-3"));

        int[] counts = commentReactionService.getCounts("c-1", 10, 10);

        assertThat(counts[0]).isZero();
    }

    @Test
    void getCounts_redisThrows_returnsFallback() {
        when(redisService.hashGetAll("comment:reaction:count:c-1")).thenThrow(new RuntimeException("redis down"));

        int[] counts = commentReactionService.getCounts("c-1", 4, 9);

        assertThat(counts).containsExactly(4, 9);
    }

    @Test
    void getUserReaction_anonymousViewer_returnsNullWithoutTouchingRedis() {
        assertThat(commentReactionService.getUserReaction("c-1", null)).isNull();
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void getUserReaction_hasReaction_returnsType() {
        when(hashOperations.get("comment:reaction:user:c-1", "user-1")).thenReturn("LOVE");

        assertThat(commentReactionService.getUserReaction("c-1", "user-1")).isEqualTo("LOVE");
    }

    @Test
    void getUserReaction_noReaction_returnsNull() {
        when(hashOperations.get("comment:reaction:user:c-1", "user-1")).thenReturn(null);

        assertThat(commentReactionService.getUserReaction("c-1", "user-1")).isNull();
    }
}
