package com.MyProject.film.film_service.service;

import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.film.film_service.dto.request.ActorRequest;
import com.MyProject.film.film_service.dto.response.ActorResponse;
import com.MyProject.film.film_service.entity.Actor;
import com.MyProject.film.film_service.exception.AppException;
import com.MyProject.film.film_service.enums.ErrorCode;
import com.MyProject.film.film_service.mapper.ActorMapper;
import com.MyProject.film.film_service.repository.httpclient.FileClient;
import com.MyProject.film.film_service.repository.mysql.ActorRepository;
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
public class ActorService {
    ActorRepository actorRepository;
    ActorMapper actorMapper;
    FileClient fileClient;

    // Nếu avatar được upload qua file-service (bucket B2 private), avatarUrl lưu trong DB chỉ là
    // metadata cũ/rỗng - phải resolve presigned URL mới ở đây mỗi lần trả response, nếu không URL sẽ
    // hết hạn sau ~1h (xem B2_PRESIGNED_URL_TTL). avatarUrl do admin tự nhập (external) thì giữ nguyên.
    private ActorResponse resolveAvatar(ActorResponse response) {
        if (response.getAvatarFileId() == null || response.getAvatarFileId().isBlank()) {
            return response;
        }
        try {
            response.setAvatarUrl(fileClient.getFileInfo(response.getAvatarFileId()).getResult().getUrl());
        } catch (Exception e) {
            log.warn("Failed to resolve avatar file {} for actor {}", response.getAvatarFileId(), response.getId(), e);
        }
        return response;
    }

    // Nếu request đi kèm avatarFileId (upload qua file-service), avatarUrl gửi lên chỉ là URL preview
    // tạm thời (xem AvatarUploadField ở frontend) - không lưu vào DB, để tránh baked-in một presigned
    // URL sẽ hết hạn sau ~1h. resolveAvatar() luôn resolve lại URL mới từ avatarFileId khi đọc.
    private void clearStalePreviewUrl(Actor actor) {
        if (actor.getAvatarFileId() != null && !actor.getAvatarFileId().isBlank()) {
            actor.setAvatarUrl(null);
        }
    }

    public ActorResponse createActor(ActorRequest request) {
        Actor actor = actorMapper.toActor(request);
        clearStalePreviewUrl(actor);
        return resolveAvatar(actorMapper.toActorResponse(actorRepository.save(actor)));
    }

    public PageResponse<ActorResponse> getAllActors(int page, int size) {
        Sort sort = Sort.by(Sort.Direction.DESC, "name");
        Pageable pageable = PageRequest.of(page - 1, size, sort);
        Page<Actor> pageData = actorRepository.findAllByDeletedFalse(pageable);

        return PageResponse.<ActorResponse>builder()
                .currentPage(page)
                .pageSize(size)
                .totalPages(pageData.getTotalPages())
                .totalElement(pageData.getTotalElements())
                .data(pageData.getContent().stream()
                        .map(actorMapper::toActorResponse)
                        .map(this::resolveAvatar)
                        .collect(Collectors.toList()))
                .build();
    }

    public ActorResponse getActor(String id) {
        Actor actor = actorRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new AppException(ErrorCode.ACTOR_NOT_FOUND));
        return resolveAvatar(actorMapper.toActorResponse(actor));
    }

    public ActorResponse updateActor(String id, ActorRequest request) {
        Actor actor = actorRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new AppException(ErrorCode.ACTOR_NOT_FOUND));
        actorMapper.updateActor(actor, request);
        clearStalePreviewUrl(actor);
        return resolveAvatar(actorMapper.toActorResponse(actorRepository.save(actor)));
    }

    public void deleteActor(String id) {
        Actor actor = actorRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new AppException(ErrorCode.ACTOR_NOT_FOUND));
        actor.setDeleted(true);
        actorRepository.save(actor);
    }
}
