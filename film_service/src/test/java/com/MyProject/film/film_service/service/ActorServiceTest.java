package com.MyProject.film.film_service.service;

import com.MyProject.common.dto.response.ApiResponse;
import com.MyProject.film.film_service.dto.request.ActorRequest;
import com.MyProject.film.film_service.dto.response.ActorResponse;
import com.MyProject.film.film_service.dto.response.FileResponse;
import com.MyProject.film.film_service.entity.Actor;
import com.MyProject.film.film_service.enums.ErrorCode;
import com.MyProject.film.film_service.exception.AppException;
import com.MyProject.film.film_service.mapper.ActorMapper;
import com.MyProject.film.film_service.repository.httpclient.FileClient;
import com.MyProject.film.film_service.repository.mysql.ActorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActorServiceTest {

    @Mock ActorRepository actorRepository;
    @Mock ActorMapper actorMapper;
    @Mock FileClient fileClient;

    ActorService actorService;

    @BeforeEach
    void setUp() {
        actorService = new ActorService(actorRepository, actorMapper, fileClient);
    }

    @Test
    void createActor_happyPath_savesAndResolvesAvatar() {
        ActorRequest request = new ActorRequest("Actor Name", null, "file-1");
        Actor mapped = new Actor();
        mapped.setName("Actor Name");
        mapped.setAvatarFileId("file-1");
        when(actorMapper.toActor(request)).thenReturn(mapped);
        Actor saved = new Actor();
        saved.setId("a-1");
        saved.setName("Actor Name");
        saved.setAvatarFileId("file-1");
        when(actorRepository.save(mapped)).thenReturn(saved);
        when(actorMapper.toActorResponse(saved)).thenReturn(
                ActorResponse.builder().id("a-1").name("Actor Name").avatarFileId("file-1").build());
        when(fileClient.getFileInfo("file-1")).thenReturn(
                ApiResponse.<FileResponse>builder().result(FileResponse.builder().url("https://cdn/file-1").build()).build());

        ActorResponse response = actorService.createActor(request);

        assertThat(response.getAvatarUrl()).isEqualTo("https://cdn/file-1");
        verify(fileClient).getFileInfo("file-1");
    }

    @Test
    void createActor_avatarResolveFails_returnsResponseWithoutAvatarUrl() {
        ActorRequest request = new ActorRequest("Actor Name", null, "file-1");
        Actor mapped = new Actor();
        mapped.setAvatarFileId("file-1");
        when(actorMapper.toActor(request)).thenReturn(mapped);
        Actor saved = new Actor();
        saved.setId("a-1");
        saved.setAvatarFileId("file-1");
        when(actorRepository.save(mapped)).thenReturn(saved);
        when(actorMapper.toActorResponse(saved)).thenReturn(
                ActorResponse.builder().id("a-1").avatarFileId("file-1").build());
        when(fileClient.getFileInfo("file-1")).thenThrow(new RuntimeException("file-service down"));

        ActorResponse response = actorService.createActor(request);

        assertThat(response.getAvatarUrl()).isNull();
    }

    @Test
    void createActor_noAvatarFileId_skipsResolveCall() {
        ActorRequest request = new ActorRequest("Actor Name", "https://external/pic.png", null);
        Actor mapped = new Actor();
        when(actorMapper.toActor(request)).thenReturn(mapped);
        Actor saved = new Actor();
        saved.setId("a-1");
        when(actorRepository.save(mapped)).thenReturn(saved);
        when(actorMapper.toActorResponse(saved)).thenReturn(
                ActorResponse.builder().id("a-1").avatarUrl("https://external/pic.png").build());

        ActorResponse response = actorService.createActor(request);

        assertThat(response.getAvatarUrl()).isEqualTo("https://external/pic.png");
        verifyNoInteractions(fileClient);
    }

    @Test
    void getAllActors_returnsPagedResponsesExcludingDeleted() {
        Actor actor = new Actor();
        actor.setId("a-1");
        Page<Actor> page = new PageImpl<>(List.of(actor));
        when(actorRepository.findAllByDeletedFalse(any(Pageable.class))).thenReturn(page);
        when(actorMapper.toActorResponse(actor)).thenReturn(ActorResponse.builder().id("a-1").build());

        var response = actorService.getAllActors(1, 10);

        assertThat(response.getData()).hasSize(1);
    }

    @Test
    void getActor_notFound_throwsActorNotFound() {
        when(actorRepository.findByIdAndDeletedFalse("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> actorService.getActor("missing"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACTOR_NOT_FOUND);
    }

    @Test
    void updateActor_notFound_throws() {
        when(actorRepository.findByIdAndDeletedFalse("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> actorService.updateActor("missing", new ActorRequest("X", null, null)))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACTOR_NOT_FOUND);
    }

    @Test
    void deleteActor_notFound_throws() {
        when(actorRepository.findByIdAndDeletedFalse("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> actorService.deleteActor("missing"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACTOR_NOT_FOUND);
    }

    @Test
    void deleteActor_happyPath_softDeletesInsteadOfRemoving() {
        Actor actor = new Actor();
        actor.setId("a-1");
        actor.setDeleted(false);
        when(actorRepository.findByIdAndDeletedFalse("a-1")).thenReturn(Optional.of(actor));

        actorService.deleteActor("a-1");

        assertThat(actor.isDeleted()).isTrue();
        verify(actorRepository).save(actor);
    }
}
