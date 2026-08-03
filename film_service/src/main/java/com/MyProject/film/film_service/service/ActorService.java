package com.MyProject.film.film_service.service;

import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.film.film_service.dto.request.ActorRequest;
import com.MyProject.film.film_service.dto.response.ActorResponse;
import com.MyProject.film.film_service.entity.Actor;
import com.MyProject.film.film_service.exception.AppException;
import com.MyProject.film.film_service.enums.ErrorCode;
import com.MyProject.film.film_service.mapper.ActorMapper;
import com.MyProject.film.film_service.repository.mysql.ActorRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ActorService {
    ActorRepository actorRepository;                                                                        
    ActorMapper actorMapper;

    public ActorResponse createActor(ActorRequest request) {
        Actor actor = actorMapper.toActor(request);
        return actorMapper.toActorResponse(actorRepository.save(actor));
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
                        .collect(Collectors.toList()))
                .build();
    }

    public ActorResponse getActor(String id) {
        Actor actor = actorRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new AppException(ErrorCode.ACTOR_NOT_FOUND));
        return actorMapper.toActorResponse(actor);
    }

    public ActorResponse updateActor(String id, ActorRequest request) {
        Actor actor = actorRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new AppException(ErrorCode.ACTOR_NOT_FOUND));
        actorMapper.updateActor(actor, request);
        return actorMapper.toActorResponse(actorRepository.save(actor));
    }

    public void deleteActor(String id) {
        Actor actor = actorRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new AppException(ErrorCode.ACTOR_NOT_FOUND));
        actor.setDeleted(true);
        actorRepository.save(actor);
    }
}
