package com.MyProject.film.film_service.controller;

import com.MyProject.common.dto.response.ApiResponse;
import com.MyProject.common.dto.response.PageResponse;
import com.MyProject.film.film_service.dto.request.ActorRequest;
import com.MyProject.film.film_service.dto.response.ActorResponse;
import com.MyProject.film.film_service.service.ActorService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/actors")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ActorController {
    ActorService actorService;

    @PostMapping
    @RolesAllowed("ADMIN")
    public ApiResponse<ActorResponse> createActor(@RequestBody @Valid ActorRequest request) {
        return ApiResponse.<ActorResponse>builder()
                .result(actorService.createActor(request))
                .build();
    }

    @GetMapping
    public ApiResponse<PageResponse<ActorResponse>> getAllActors(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.<PageResponse<ActorResponse>>builder()
                .result(actorService.getAllActors(page, size))
                .build();
    }

    @GetMapping("/{id}")
    public ApiResponse<ActorResponse> getActor(@PathVariable String id) {
        return ApiResponse.<ActorResponse>builder()
                .result(actorService.getActor(id))
                .build();
    }

    @PutMapping("/{id}")
    @RolesAllowed("ADMIN")
    public ApiResponse<ActorResponse> updateActor(@PathVariable String id, @RequestBody @Valid ActorRequest request) {
        return ApiResponse.<ActorResponse>builder()
                .result(actorService.updateActor(id, request))
                .build();
    }

    @DeleteMapping("/{id}")
    @RolesAllowed("ADMIN")
    public ApiResponse<Void> deleteActor(@PathVariable String id) {
        actorService.deleteActor(id);
        return ApiResponse.<Void>builder().build();
    }
}
