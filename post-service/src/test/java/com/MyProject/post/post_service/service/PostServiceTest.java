package com.MyProject.post.post_service.service;

import com.MyProject.common.security.SecurityUtils;
import com.MyProject.post.post_service.dto.request.PostRequest;
import com.MyProject.post.post_service.dto.request.PostUpdateRequest;
import com.MyProject.post.post_service.dto.response.LikeResponse;
import com.MyProject.post.post_service.dto.response.PostResponse;
import com.MyProject.post.post_service.entity.Post;
import com.MyProject.post.post_service.entity.PostType;
import com.MyProject.post.post_service.exception.AppException;
import com.MyProject.post.post_service.exception.ErrorCode;
import com.MyProject.post.post_service.mapper.PostMapper;
import com.MyProject.post.post_service.repository.PostElasticRepository;
import com.MyProject.post.post_service.repository.PostLikeRepository;
import com.MyProject.post.post_service.repository.PostRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    private static final String USER_ID = "user-1";

    @Mock PostRepository postRepository;
    @Mock PostElasticRepository postElasticRepository;
    @Mock PostLikeRepository postLikeRepository;
    @Mock PostMapper postMapper;
    @Mock PostCacheService postCacheService;
    @Mock OutboxEventPublisher outboxEventPublisher;
    @Mock MongoTemplate mongoTemplate;
    @Mock PostProfileExternalService postProfileExternalService;
    @Mock PostFileExternalService postFileExternalService;
    @Mock PostLikeNotificationService postLikeNotificationService;

    PostService postService;
    MockedStatic<SecurityUtils> securityUtils;

    @BeforeEach
    void setUp() {
        postService = new PostService(postRepository, postElasticRepository, postLikeRepository, postMapper,
                postCacheService, outboxEventPublisher, mongoTemplate, postProfileExternalService,
                postFileExternalService, postLikeNotificationService);

        securityUtils = mockStatic(SecurityUtils.class);
        securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);

        lenient().when(postMapper.toPostResponse(any(Post.class))).thenAnswer(inv -> {
            Post p = inv.getArgument(0);
            return PostResponse.builder().id(p.getId()).userId(p.getUserId()).title(p.getTitle())
                    .content(p.getContent()).postType(p.getPostType() != null ? p.getPostType().name() : null)
                    .likeCount(p.getLikeCount()).build();
        });
        lenient().when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(postProfileExternalService.getBulkUserProfiles(any())).thenReturn(java.util.Map.of());
    }

    @AfterEach
    void tearDown() {
        securityUtils.close();
    }

    private PostRequest.PostRequestBuilder textRequestBuilder() {
        return PostRequest.builder().postType("TEXT").title("Hello").content("World");
    }

    // ---------- createPost ----------

    @Test
    void createPost_blankPostType_throwsInvalidPostType() {
        PostRequest request = textRequestBuilder().postType("  ").build();

        assertThatThrownBy(() -> postService.createPost(request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_POST_TYPE);
        verifyNoInteractions(postRepository);
    }

    @Test
    void createPost_unknownPostType_throwsInvalidPostType() {
        PostRequest request = textRequestBuilder().postType("STORY").build();

        assertThatThrownBy(() -> postService.createPost(request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_POST_TYPE);
    }

    @Test
    void createPost_imageTypeMissingIds_throwsImagePostEmptyIds() {
        PostRequest request = textRequestBuilder().postType("IMAGE").imageFileIds(List.of()).build();

        assertThatThrownBy(() -> postService.createPost(request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.IMAGE_POST_EMPTY_IDS);
    }

    @Test
    void createPost_watchTogetherMissingRoom_throwsWatchPostMissingRoom() {
        PostRequest request = textRequestBuilder().postType("WATCH_TOGETHER").build();

        assertThatThrownBy(() -> postService.createPost(request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.WATCH_POST_MISSING_ROOM);
    }

    @Test
    void createPost_textType_happyPath_savesPublishesAndInvalidatesCache() {
        PostRequest request = textRequestBuilder().build();

        PostResponse response = postService.createPost(request);

        assertThat(response.getContent()).isEqualTo("World");
        assertThat(response.getUserId()).isEqualTo(USER_ID);
        verify(outboxEventPublisher).publish(eq("post.sync"), any(), any());
        verify(postCacheService).invalidateAllUserPosts(USER_ID);
    }

    @Test
    void createPost_currentUserAutoAddedToListUsersJoin() {
        PostRequest request = textRequestBuilder()
                .listUsersJoin(new java.util.ArrayList<>(List.of("other-user"))).build();

        postService.createPost(request);

        verify(postRepository).save(argThat(p -> p.getListUsersJoin().contains(USER_ID)
                && p.getListUsersJoin().contains("other-user")));
    }

    // ---------- toggleLike ----------

    @Test
    void toggleLike_postNotFound_throws() {
        when(postRepository.findById("p-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.toggleLike("p-1"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.POST_NOT_FOUND);
    }

    @Test
    void toggleLike_notYetLiked_incrementsAndSchedulesNotificationForOthersPost() {
        Post post = Post.builder().id("p-1").userId("owner-2").likeCount(3).build();
        when(postRepository.findById("p-1")).thenReturn(Optional.of(post));
        when(postLikeRepository.existsByPostIdAndUserId("p-1", USER_ID)).thenReturn(false);

        LikeResponse response = postService.toggleLike("p-1");

        assertThat(response.isLiked()).isTrue();
        assertThat(response.getLikeCount()).isEqualTo(4);
        verify(postLikeRepository).save(any());
        verify(postLikeNotificationService).scheduleNotification("p-1", USER_ID);
    }

    @Test
    void toggleLike_alreadyLiked_decrementsWithoutGoingNegative() {
        Post post = Post.builder().id("p-1").userId("owner-2").likeCount(0).build();
        when(postRepository.findById("p-1")).thenReturn(Optional.of(post));
        when(postLikeRepository.existsByPostIdAndUserId("p-1", USER_ID)).thenReturn(true);

        LikeResponse response = postService.toggleLike("p-1");

        assertThat(response.isLiked()).isFalse();
        assertThat(response.getLikeCount()).isZero();
        verify(postLikeRepository).deleteByPostIdAndUserId("p-1", USER_ID);
    }

    @Test
    void toggleLike_ownPost_doesNotScheduleNotification() {
        Post post = Post.builder().id("p-1").userId(USER_ID).likeCount(0).build();
        when(postRepository.findById("p-1")).thenReturn(Optional.of(post));
        when(postLikeRepository.existsByPostIdAndUserId("p-1", USER_ID)).thenReturn(false);

        postService.toggleLike("p-1");

        verifyNoInteractions(postLikeNotificationService);
    }

    // ---------- updatePost ----------

    @Test
    void updatePost_notFound_throws() {
        when(postRepository.findById("p-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.updatePost("p-1", new PostUpdateRequest()))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.POST_NOT_FOUND);
    }

    @Test
    void updatePost_happyPath_updatesPublishesAndInvalidatesCache() {
        Post post = Post.builder().id("p-1").userId(USER_ID).title("old").build();
        when(postRepository.findById("p-1")).thenReturn(Optional.of(post));

        postService.updatePost("p-1", new PostUpdateRequest());

        assertThat(post.getModifiedDate()).isNotNull();
        verify(outboxEventPublisher).publish(eq("post.sync"), any(), any());
        verify(postCacheService).invalidateAllUserPosts(USER_ID);
    }

    // ---------- deletePost ----------

    @Test
    void deletePost_notFound_throws() {
        when(postRepository.findById("p-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.deletePost("p-1"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.POST_NOT_FOUND);
        verify(postRepository, never()).delete(any());
    }

    @Test
    void deletePost_happyPath_deletesAndInvalidatesCache() {
        Post post = Post.builder().id("p-1").userId(USER_ID).build();
        when(postRepository.findById("p-1")).thenReturn(Optional.of(post));

        postService.deletePost("p-1");

        verify(postRepository).delete(post);
        verify(postCacheService).invalidateAllUserPosts(USER_ID);
    }

    // ---------- getMyPost ----------

    @Test
    void getMyPost_notFound_throws() {
        when(postRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.getMyPost("missing"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.POST_NOT_FOUND);
    }

    @Test
    void getMyPost_happyPath_setsLikedFlag() {
        Post post = Post.builder().id("p-1").userId(USER_ID).postType(PostType.TEXT).build();
        when(postRepository.findById("p-1")).thenReturn(Optional.of(post));
        when(postLikeRepository.existsByPostIdAndUserId("p-1", USER_ID)).thenReturn(true);

        PostResponse response = postService.getMyPost("p-1");

        assertThat(response.isLiked()).isTrue();
    }

    // ---------- getRandomPosts ----------

    @SuppressWarnings("unchecked")
    @Test
    void getRandomPosts_noSamples_returnsEmptyList() {
        AggregationResults<Post> emptyResults = mock(AggregationResults.class);
        when(emptyResults.getMappedResults()).thenReturn(List.of());
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("post"), eq(Post.class))).thenReturn(emptyResults);

        List<PostResponse> result = postService.getRandomPosts(5, List.of());

        assertThat(result).isEmpty();
        verifyNoInteractions(postProfileExternalService);
    }

    // ---------- countByUserId / getPostOwner ----------

    @Test
    void countByUserId_delegatesToRepositoryForCurrentUser() {
        when(postRepository.countByUserId(USER_ID)).thenReturn(7);

        assertThat(postService.countByUserId()).isEqualTo(7);
    }

    @Test
    void getPostOwner_found_returnsUserId() {
        Post post = Post.builder().id("p-1").userId("owner-2").build();
        when(postRepository.findById("p-1")).thenReturn(Optional.of(post));

        assertThat(postService.getPostOwner("p-1")).isEqualTo("owner-2");
    }

    @Test
    void getPostOwner_notFound_returnsNull() {
        when(postRepository.findById("missing")).thenReturn(Optional.empty());

        assertThat(postService.getPostOwner("missing")).isNull();
    }
}
