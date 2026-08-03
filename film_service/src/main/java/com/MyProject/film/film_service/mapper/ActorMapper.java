package com.MyProject.film.film_service.mapper;

import com.MyProject.film.film_service.dto.request.ActorRequest;
import com.MyProject.film.film_service.dto.response.ActorResponse;
import com.MyProject.film.film_service.entity.Actor;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface ActorMapper {
    @Mapping(target = "id", ignore = true)
    Actor toActor(ActorRequest request);

    ActorResponse toActorResponse(Actor actor);

    @Mapping(target = "id", ignore = true)
    void updateActor(@MappingTarget Actor actor, ActorRequest request);
}
