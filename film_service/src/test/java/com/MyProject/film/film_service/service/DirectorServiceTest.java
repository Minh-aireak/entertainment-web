package com.MyProject.film.film_service.service;

import com.MyProject.common.dto.response.ApiResponse;
import com.MyProject.film.film_service.dto.request.DirectorRequest;
import com.MyProject.film.film_service.dto.response.DirectorResponse;
import com.MyProject.film.film_service.dto.response.FileResponse;
import com.MyProject.film.film_service.entity.Director;
import com.MyProject.film.film_service.enums.ErrorCode;
import com.MyProject.film.film_service.exception.AppException;
import com.MyProject.film.film_service.mapper.DirectorMapper;
import com.MyProject.film.film_service.repository.httpclient.FileClient;
import com.MyProject.film.film_service.repository.mysql.DirectorRepository;
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
class DirectorServiceTest {

    @Mock DirectorRepository directorRepository;
    @Mock DirectorMapper directorMapper;
    @Mock FileClient fileClient;

    DirectorService directorService;

    @BeforeEach
    void setUp() {
        directorService = new DirectorService(directorRepository, directorMapper, fileClient);
    }

    @Test
    void createDirector_happyPath_savesAndResolvesAvatar() {
        DirectorRequest request = new DirectorRequest("Director Name", null, "file-1");
        Director mapped = new Director();
        mapped.setAvatarFileId("file-1");
        when(directorMapper.toDirector(request)).thenReturn(mapped);
        Director saved = new Director();
        saved.setId("d-1");
        saved.setAvatarFileId("file-1");
        when(directorRepository.save(mapped)).thenReturn(saved);
        when(directorMapper.toDirectorResponse(saved)).thenReturn(
                DirectorResponse.builder().id("d-1").avatarFileId("file-1").build());
        when(fileClient.getFileInfo("file-1")).thenReturn(
                ApiResponse.<FileResponse>builder().result(FileResponse.builder().url("https://cdn/file-1").build()).build());

        DirectorResponse response = directorService.createDirector(request);

        assertThat(response.getAvatarUrl()).isEqualTo("https://cdn/file-1");
        verify(fileClient).getFileInfo("file-1");
    }

    @Test
    void createDirector_avatarResolveFails_returnsResponseWithoutAvatarUrl() {
        DirectorRequest request = new DirectorRequest("Director Name", null, "file-1");
        Director mapped = new Director();
        mapped.setAvatarFileId("file-1");
        when(directorMapper.toDirector(request)).thenReturn(mapped);
        Director saved = new Director();
        saved.setId("d-1");
        saved.setAvatarFileId("file-1");
        when(directorRepository.save(mapped)).thenReturn(saved);
        when(directorMapper.toDirectorResponse(saved)).thenReturn(
                DirectorResponse.builder().id("d-1").avatarFileId("file-1").build());
        when(fileClient.getFileInfo("file-1")).thenThrow(new RuntimeException("file-service down"));

        DirectorResponse response = directorService.createDirector(request);

        assertThat(response.getAvatarUrl()).isNull();
    }

    @Test
    void createDirector_noAvatarFileId_skipsResolveCall() {
        DirectorRequest request = new DirectorRequest("Director Name", "https://external/pic.png", null);
        Director mapped = new Director();
        when(directorMapper.toDirector(request)).thenReturn(mapped);
        Director saved = new Director();
        saved.setId("d-1");
        when(directorRepository.save(mapped)).thenReturn(saved);
        when(directorMapper.toDirectorResponse(saved)).thenReturn(
                DirectorResponse.builder().id("d-1").avatarUrl("https://external/pic.png").build());

        DirectorResponse response = directorService.createDirector(request);

        assertThat(response.getAvatarUrl()).isEqualTo("https://external/pic.png");
        verifyNoInteractions(fileClient);
    }

    @Test
    void getAllDirectors_returnsPagedResponsesExcludingDeleted() {
        Director director = new Director();
        director.setId("d-1");
        Page<Director> page = new PageImpl<>(List.of(director));
        when(directorRepository.findAllByDeletedFalse(any(Pageable.class))).thenReturn(page);
        when(directorMapper.toDirectorResponse(director)).thenReturn(DirectorResponse.builder().id("d-1").build());

        var response = directorService.getAllDirectors(1, 10);

        assertThat(response.getData()).hasSize(1);
    }

    @Test
    void getDirector_notFound_throwsDirectorNotFound() {
        when(directorRepository.findByIdAndDeletedFalse("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> directorService.getDirector("missing"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.DIRECTOR_NOT_FOUND);
    }

    @Test
    void updateDirector_notFound_throws() {
        when(directorRepository.findByIdAndDeletedFalse("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> directorService.updateDirector("missing", new DirectorRequest("X", null, null)))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.DIRECTOR_NOT_FOUND);
    }

    @Test
    void deleteDirector_notFound_throws() {
        when(directorRepository.findByIdAndDeletedFalse("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> directorService.deleteDirector("missing"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.DIRECTOR_NOT_FOUND);
    }

    @Test
    void deleteDirector_happyPath_softDeletesInsteadOfRemoving() {
        Director director = new Director();
        director.setId("d-1");
        director.setDeleted(false);
        when(directorRepository.findByIdAndDeletedFalse("d-1")).thenReturn(Optional.of(director));

        directorService.deleteDirector("d-1");

        assertThat(director.isDeleted()).isTrue();
        verify(directorRepository).save(director);
    }
}
