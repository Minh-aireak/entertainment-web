package com.MyProject.film.film_service.controller;

import com.MyProject.common.dto.response.ApiResponse;
import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.film.film_service.dto.request.DirectorRequest;
import com.MyProject.film.film_service.dto.response.DirectorResponse;
import com.MyProject.film.film_service.service.DirectorService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/directors")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DirectorController {
    DirectorService directorService;

    @PostMapping
    @RolesAllowed("ADMIN")
    public ApiResponse<DirectorResponse> createDirector(@RequestBody @Valid DirectorRequest request) {
        return ApiResponse.<DirectorResponse>builder()
                .result(directorService.createDirector(request))
                .build();
    }

    @GetMapping
    public ApiResponse<PageResponse<DirectorResponse>> getAllDirectors(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.<PageResponse<DirectorResponse>>builder()
                .result(directorService.getAllDirectors(page, size))
                .build();
    }

    @GetMapping("/{id}")
    public ApiResponse<DirectorResponse> getDirector(@PathVariable String id) {
        return ApiResponse.<DirectorResponse>builder()
                .result(directorService.getDirector(id))
                .build();
    }

    @PutMapping("/{id}")
    @RolesAllowed("ADMIN")
    public ApiResponse<DirectorResponse> updateDirector(@PathVariable String id, @RequestBody @Valid DirectorRequest request) {
        return ApiResponse.<DirectorResponse>builder()
                .result(directorService.updateDirector(id, request))
                .build();
    }

    @DeleteMapping("/{id}")
    @RolesAllowed("ADMIN")
    public ApiResponse<Void> deleteDirector(@PathVariable String id) {
        directorService.deleteDirector(id);
        return ApiResponse.<Void>builder().build();
    }
}
