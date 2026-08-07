package com.MyProject.film.film_service.service;

import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.film.film_service.dto.request.DirectorRequest;
import com.MyProject.film.film_service.dto.response.DirectorResponse;
import com.MyProject.film.film_service.entity.Director;
import com.MyProject.film.film_service.exception.AppException;
import com.MyProject.film.film_service.enums.ErrorCode;
import com.MyProject.film.film_service.mapper.DirectorMapper;
import com.MyProject.film.film_service.repository.httpclient.FileClient;
import com.MyProject.film.film_service.repository.mysql.DirectorRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DirectorService {
    DirectorRepository directorRepository;
    DirectorMapper directorMapper;
    FileClient fileClient;

    // Xem ActorService.resolveAvatar - cùng lý do: avatarFileId (upload qua file-service, bucket B2
    // private) cần resolve presigned URL mới mỗi lần đọc; avatarUrl tự nhập (external) giữ nguyên.
    private DirectorResponse resolveAvatar(DirectorResponse response) {
        if (response.getAvatarFileId() == null || response.getAvatarFileId().isBlank()) {
            return response;
        }
        try {
            response.setAvatarUrl(fileClient.getFileInfo(response.getAvatarFileId()).getResult().getUrl());
        } catch (Exception e) {
            log.warn("Failed to resolve avatar file {} for director {}", response.getAvatarFileId(), response.getId(), e);
        }
        return response;
    }

    // Nếu request đi kèm avatarFileId (upload qua file-service), avatarUrl gửi lên chỉ là URL preview
    // tạm thời (xem AvatarUploadField ở frontend) - không lưu vào DB, để tránh baked-in một presigned
    // URL sẽ hết hạn sau ~1h. resolveAvatar() luôn resolve lại URL mới từ avatarFileId khi đọc.
    private void clearStalePreviewUrl(Director director) {
        if (director.getAvatarFileId() != null && !director.getAvatarFileId().isBlank()) {
            director.setAvatarUrl(null);
        }
    }

    public DirectorResponse createDirector(DirectorRequest request) {
        Director director = directorMapper.toDirector(request);
        clearStalePreviewUrl(director);
        return resolveAvatar(directorMapper.toDirectorResponse(directorRepository.save(director)));
    }

    public PageResponse<DirectorResponse> getAllDirectors(int page, int size) {
        Sort sort = Sort.by(Sort.Direction.DESC, "name");
        Pageable pageable = PageRequest.of(page - 1, size, sort);
        Page<Director> pageData = directorRepository.findAllByDeletedFalse(pageable);

        return PageResponse.<DirectorResponse>builder()
                .currentPage(page)
                .pageSize(size)
                .totalPages(pageData.getTotalPages())
                .totalElement(pageData.getTotalElements())
                .data(pageData.getContent().stream()
                        .map(directorMapper::toDirectorResponse)
                        .map(this::resolveAvatar)
                        .collect(Collectors.toList()))
                .build();
    }

    public DirectorResponse getDirector(String id) {
        Director director = directorRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new AppException(ErrorCode.DIRECTOR_NOT_FOUND));
        return resolveAvatar(directorMapper.toDirectorResponse(director));
    }

    public DirectorResponse updateDirector(String id, DirectorRequest request) {
        Director director = directorRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new AppException(ErrorCode.DIRECTOR_NOT_FOUND));
        directorMapper.updateDirector(director, request);
        clearStalePreviewUrl(director);
        return resolveAvatar(directorMapper.toDirectorResponse(directorRepository.save(director)));
    }

    public void deleteDirector(String id) {
        Director director = directorRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new AppException(ErrorCode.DIRECTOR_NOT_FOUND));
        director.setDeleted(true);
        directorRepository.save(director);
    }
}
