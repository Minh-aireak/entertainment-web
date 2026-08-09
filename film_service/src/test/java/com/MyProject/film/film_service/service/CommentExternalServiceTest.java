package com.MyProject.film.film_service.service;

import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.film.film_service.dto.response.CommentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentExternalServiceTest {

    @Mock CommentExternalClient commentExternalClient;

    CommentExternalService commentExternalService;

    @BeforeEach
    void setUp() {
        commentExternalService = new CommentExternalService(commentExternalClient);
    }

    @Test
    void getComments_happyPath_returnsCompletedFutureWithClientResult() throws Exception {
        PageResponse<CommentResponse> page = PageResponse.<CommentResponse>builder()
                .currentPage(1).pageSize(10).totalPages(1).totalElement(1L)
                .data(List.of(CommentResponse.builder().id("c-1").build())).build();
        when(commentExternalClient.fetchComments("film-1", 1, 10)).thenReturn(page);

        CompletableFuture<PageResponse<CommentResponse>> result = commentExternalService.getComments("film-1", 1, 10);

        assertThat(result).isCompleted();
        assertThat(result.get().getData()).hasSize(1);
    }

    @Test
    void getComments_clientReturnsNull_propagatesNullInsteadOfThrowing() throws Exception {
        when(commentExternalClient.fetchComments("film-1", 1, 10)).thenReturn(null);

        CompletableFuture<PageResponse<CommentResponse>> result = commentExternalService.getComments("film-1", 1, 10);

        assertThat(result).isCompleted();
        assertThat(result.get()).isNull();
    }

    @Test
    void getComments_clientThrows_propagatesExceptionSynchronouslyRatherThanAsCompletableFutureFailure() {
        when(commentExternalClient.fetchComments("film-1", 1, 10)).thenThrow(new RuntimeException("comment-service down"));

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> commentExternalService.getComments("film-1", 1, 10));
    }
}
