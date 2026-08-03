package com.MyProject.film.film_service.mapper;

import com.MyProject.film.film_service.dto.request.EpisodeRequest;
import com.MyProject.film.film_service.dto.response.EpisodeResponse;
import com.MyProject.film.film_service.entity.Episode;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface EpisodeMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "film", ignore = true)
    Episode toEpisode(EpisodeRequest request);

    @Mapping(target = "filmId", source = "film.id")
    EpisodeResponse toEpisodeResponse(Episode episode);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "film", ignore = true)
    void updateEpisode(@MappingTarget Episode episode, EpisodeRequest request);
}
