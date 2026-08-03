package com.MyProject.film.film_service.mapper;

import com.MyProject.film.film_service.dto.response.FilmFollowResponse;
import com.MyProject.film.film_service.entity.FilmFollow;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = {FilmMapper.class})
public interface FilmFollowMapper {
    FilmFollowResponse toFilmFollowResponse(FilmFollow filmFollow);
}
