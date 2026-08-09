package com.MyProject.comment_service.service;

import com.MyProject.comment_service.configuration.DateTimeFormatter;
import com.MyProject.comment_service.dto.request.CreateCommentRequest;
import com.MyProject.comment_service.dto.request.UpdateCommentRequest;
import com.MyProject.comment_service.dto.response.CommentResponse;
import com.MyProject.comment_service.entity.Comment;
import com.MyProject.comment_service.enums.CommentStatus;
import com.MyProject.comment_service.enums.CommentType;
import com.MyProject.comment_service.enums.ErrorCode;
import com.MyProject.comment_service.exception.AppException;
import com.MyProject.comment_service.mapper.CommentMapper;
import com.MyProject.comment_service.repository.CommentRepository;
import com.MyProject.comment_service.repository.OutboxRepository;
import com.MyProject.common.redis.RedisService;
import com.MyProject.common.security.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    private static final String USER_ID = "user-1";

    @Mock CommentRepository commentRepository;
    @Mock CommentMapper commentMapper;
    @Mock CommentProfileExternalService commentProfileExternalService;
    @Mock CommentPostExternalService commentPostExternalService;
    @Mock CommentReactionService commentReactionService;
    @Mock RedisService redisService;
    @Mock OutboxRepository outboxRepository;
    @Mock MongoTemplate mongoTemplate;

    CommentService commentService;
    MockedStatic<SecurityUtils> securityUtils;

    @BeforeEach
    void setUp() {
        commentService = new CommentService(commentRepository, commentMapper, commentProfileExternalService,
                commentPostExternalService, commentReactionService, redisService, new DateTimeFormatter(),
                outboxRepository, new ObjectMapper().registerModule(new JavaTimeModule()), mongoTemplate);

        securityUtils = mockStatic(SecurityUtils.class);
        securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);

        lenient().when(commentMapper.toCommentResponse(any(Comment.class))).thenAnswer(inv -> {
            Comment c = inv.getArgument(0);
            return CommentResponse.builder().id(c.getId()).sourceId(c.getSourceId()).userId(c.getUserId())
                    .content(c.getContent()).parentId(c.getParentId()).type(c.getType()).status(c.getStatus())
                    .likeCount(c.getLikeCount()).loveCount(c.getLoveCount()).build();
        });
        lenient().when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> {
            Comment c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId("comment-" + System.nanoTime());
                c.setCreatedDate(Instant.now());
            }
            return c;
        });
        lenient().when(commentReactionService.getCounts(anyString(), anyInt(), anyInt()))
                .thenAnswer(inv -> new int[]{inv.getArgument(1), inv.getArgument(2)});
    }

    private static int anyInt() {
        return org.mockito.ArgumentMatchers.anyInt();
    }

    @AfterEach
    void tearDown() {
        securityUtils.close();
    }

    private CreateCommentRequest textRequest(String sourceId, String content) {
        return CreateCommentRequest.builder().sourceId(sourceId).content(content).type(CommentType.TEXT).build();
    }

    // ---------- createComment ----------

    @Test
    void createComment_blankContent_throwsCommentContentEmpty() {
        assertThatThrownBy(() -> commentService.createComment(textRequest("post-1", "   ")))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.COMMENT_CONTENT_EMPTY);
        verifyNoInteractions(commentRepository);
    }

    @Test
    void createComment_nullContent_throwsCommentContentEmpty() {
        assertThatThrownBy(() -> commentService.createComment(textRequest("post-1", null)))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.COMMENT_CONTENT_EMPTY);
    }

    @Test
    void createComment_contentExceeds2000Chars_throwsCommentContentTooLong() {
        String tooLong = "a".repeat(2001);

        assertThatThrownBy(() -> commentService.createComment(textRequest("post-1", tooLong)))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.COMMENT_CONTENT_TOO_LONG);
    }

    @Test
    void createComment_topLevel_savesAndPublishesEventWithoutBumpingParent() {
        CreateCommentRequest request = textRequest("post-1", "hello world");
        when(commentPostExternalService.getPostOwner("post-1")).thenReturn(null);

        CommentResponse response = commentService.createComment(request);

        assertThat(response.getContent()).isEqualTo("hello world");
        assertThat(response.getUserId()).isEqualTo(USER_ID);
        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class), eq(Comment.class));
        verify(outboxRepository).save(argThat(o -> o.getTopic().equals("comment.events")));
    }

    @Test
    void createComment_reply_bumpsParentReplyCountAtomically() {
        CreateCommentRequest request = CreateCommentRequest.builder()
                .sourceId("post-1").content("a reply").type(CommentType.TEXT).parentId("parent-1").build();
        when(commentPostExternalService.getPostOwner("post-1")).thenReturn(null);

        commentService.createComment(request);

        verify(mongoTemplate).updateFirst(any(Query.class), any(Update.class), eq(Comment.class));
    }

    @Test
    void createComment_notifiesPostOwner_whenDifferentFromActor() {
        CreateCommentRequest request = textRequest("post-1", "hello");
        when(commentPostExternalService.getPostOwner("post-1")).thenReturn("owner-2");

        commentService.createComment(request);

        verify(outboxRepository).save(argThat(o -> o.getTopic().equals("notification.events")
                && o.getPayload().contains("SOCIAL_COMMENT")));
    }

    @Test
    void createComment_actorIsPostOwner_doesNotSelfNotify() {
        CreateCommentRequest request = textRequest("post-1", "hello");
        when(commentPostExternalService.getPostOwner("post-1")).thenReturn(USER_ID);

        commentService.createComment(request);

        verify(outboxRepository, never()).save(argThat(o -> o.getTopic().equals("notification.events")));
    }

    // ---------- updateComment ----------

    @Test
    void updateComment_notFound_throwsCommentNotFound() {
        when(commentRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.updateComment("missing", new UpdateCommentRequest("edited")))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.COMMENT_NOT_FOUND);
    }

    @Test
    void updateComment_notOwner_throwsUnauthorized() {
        Comment comment = Comment.builder().id("c-1").userId("someone-else").type(CommentType.TEXT).build();
        when(commentRepository.findById("c-1")).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.updateComment("c-1", new UpdateCommentRequest("edited")))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void updateComment_nonTextType_throwsInvalidCommentType() {
        Comment comment = Comment.builder().id("c-1").userId(USER_ID).type(CommentType.ICON).build();
        when(commentRepository.findById("c-1")).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.updateComment("c-1", new UpdateCommentRequest("edited")))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_COMMENT_TYPE);
    }

    @Test
    void updateComment_happyPath_updatesContentAndStatus() {
        Comment comment = Comment.builder().id("c-1").sourceId("post-1").userId(USER_ID)
                .type(CommentType.TEXT).content("old").status(CommentStatus.SENT).createdDate(Instant.now()).build();
        when(commentRepository.findById("c-1")).thenReturn(Optional.of(comment));

        CommentResponse response = commentService.updateComment("c-1", new UpdateCommentRequest("new content"));

        assertThat(comment.getStatus()).isEqualTo(CommentStatus.EDITED);
        assertThat(response.getContent()).isEqualTo("new content");
        verify(outboxRepository).save(argThat(o -> o.getPayload().contains("COMMENT_UPDATED")));
    }

    // ---------- deleteComment ----------

    @Test
    void deleteComment_notOwner_throwsUnauthorized() {
        Comment comment = Comment.builder().id("c-1").userId("someone-else").build();
        when(commentRepository.findById("c-1")).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.deleteComment("c-1"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
        verify(commentRepository, never()).save(any());
    }

    @Test
    void deleteComment_reply_decrementsParentReplyCount() {
        Comment comment = Comment.builder().id("c-1").sourceId("post-1").userId(USER_ID).parentId("parent-1")
                .createdDate(Instant.now()).build();
        when(commentRepository.findById("c-1")).thenReturn(Optional.of(comment));

        commentService.deleteComment("c-1");

        assertThat(comment.getStatus()).isEqualTo(CommentStatus.DELETED);
        verify(mongoTemplate).updateFirst(any(Query.class), any(Update.class), eq(Comment.class));
    }

    @Test
    void deleteComment_topLevel_doesNotTouchParent() {
        Comment comment = Comment.builder().id("c-1").sourceId("post-1").userId(USER_ID)
                .createdDate(Instant.now()).build();
        when(commentRepository.findById("c-1")).thenReturn(Optional.of(comment));

        commentService.deleteComment("c-1");

        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class), eq(Comment.class));
    }

    // ---------- getComments ----------

    @Test
    void getComments_cacheMiss_fetchesFromMongoAndCachesFirstPage() {
        when(redisService.get(anyString(), any())).thenReturn(null);
        Comment comment = Comment.builder().id("c-1").sourceId("post-1").userId(USER_ID)
                .content("hi").likeCount(2).loveCount(1).createdDate(Instant.now()).build();
        Page<Comment> page = new PageImpl<>(List.of(comment));
        when(commentRepository.findBySourceIdAndParentIdIsNullAndStatusNot(
                eq("post-1"), eq(CommentStatus.DELETED), any(Pageable.class))).thenReturn(page);
        when(redisService.multiGet(anyList(), any())).thenReturn(List.of());

        var result = commentService.getComments("post-1", 1, 10);

        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).getLikeCount()).isEqualTo(2);
        verify(redisService).setWithExpiration(anyString(), any(), eq(60L), any());
    }

    private static List<String> anyList() {
        return org.mockito.ArgumentMatchers.anyList();
    }

    // ---------- countAllComments ----------

    @SuppressWarnings("unchecked")
    @Test
    void countAllComments_noMatches_returnsZero() {
        AggregationResults<Document> emptyResults = mock(AggregationResults.class);
        when(emptyResults.getUniqueMappedResult()).thenReturn(null);
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("comments"), eq(Document.class)))
                .thenReturn(emptyResults);

        long count = commentService.countAllComments("post-1");

        assertThat(count).isZero();
    }

    @SuppressWarnings("unchecked")
    @Test
    void countAllComments_sumsTopLevelAndReplies() {
        Document doc = new Document();
        doc.put("topLevelCount", 5);
        doc.put("totalReplies", 12);
        AggregationResults<Document> results = mock(AggregationResults.class);
        when(results.getUniqueMappedResult()).thenReturn(doc);
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("comments"), eq(Document.class)))
                .thenReturn(results);

        long count = commentService.countAllComments("post-1");

        assertThat(count).isEqualTo(17L);
    }

    @SuppressWarnings("unchecked")
    @Test
    void countAllComments_noRepliesField_treatsAsZero() {
        Document doc = new Document();
        doc.put("topLevelCount", 3);
        AggregationResults<Document> results = mock(AggregationResults.class);
        when(results.getUniqueMappedResult()).thenReturn(doc);
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("comments"), eq(Document.class)))
                .thenReturn(results);

        long count = commentService.countAllComments("post-1");

        assertThat(count).isEqualTo(3L);
    }
}
