package com.MyProject.film.film_service.service;

import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.film.film_service.dto.request.DirectorRequest;
import com.MyProject.film.film_service.dto.response.DirectorResponse;
import com.MyProject.film.film_service.entity.Director;
import com.MyProject.film.film_service.exception.AppException;
import com.MyProject.film.film_service.enums.ErrorCode;
import com.MyProject.film.film_service.mapper.DirectorMapper;
import com.MyProject.film.film_service.repository.mysql.DirectorRepository;
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
public class DirectorService {
    DirectorRepository directorRepository;
    DirectorMapper directorMapper;

    public DirectorResponse createDirector(DirectorRequest request) {
        Director director = directorMapper.toDirector(request);
        return directorMapper.toDirectorResponse(directorRepository.save(director));
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
                        .collect(Collectors.toList()))
                .build();
    }

    public DirectorResponse getDirector(String id) {
        Director director = directorRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new AppException(ErrorCode.DIRECTOR_NOT_FOUND));
        return directorMapper.toDirectorResponse(director);
    }

    public DirectorResponse updateDirector(String id, DirectorRequest request) {
        Director director = directorRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new AppException(ErrorCode.DIRECTOR_NOT_FOUND));
        directorMapper.updateDirector(director, request);
        return directorMapper.toDirectorResponse(directorRepository.save(director));
    }

    public void deleteDirector(String id) {
        Director director = directorRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new AppException(ErrorCode.DIRECTOR_NOT_FOUND));
        director.setDeleted(true);
        directorRepository.save(director);
    }
}
