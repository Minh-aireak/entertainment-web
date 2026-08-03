package com.MyProject.film.film_service.mapper;

import com.MyProject.film.film_service.dto.request.DirectorRequest;
import com.MyProject.film.film_service.dto.response.DirectorResponse;
import com.MyProject.film.film_service.entity.Director;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface DirectorMapper {
    @Mapping(target = "id", ignore = true)
    Director toDirector(DirectorRequest request);

    DirectorResponse toDirectorResponse(Director director);

    @Mapping(target = "id", ignore = true)
    void updateDirector(@MappingTarget Director director, DirectorRequest request);
}
