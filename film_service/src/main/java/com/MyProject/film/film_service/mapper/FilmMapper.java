package com.MyProject.film.film_service.mapper;

import com.MyProject.film.film_service.dto.request.FilmRequest;
import com.MyProject.film.film_service.dto.response.*;
import com.MyProject.film.film_service.entity.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import java.util.List;

@Mapper(componentModel = "spring", uses = {
    ActorMapper.class,
    DirectorMapper.class
})
public interface FilmMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "lastUpdate", ignore = true)
    @Mapping(target = "directors", ignore = true)
    @Mapping(target = "averageRating", ignore = true)
    @Mapping(target = "ratingCount", ignore = true)
    @Mapping(target = "episodes", ignore = true)
    @Mapping(target = "casts", ignore = true)
    Film toFilm(FilmRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "lastUpdate", ignore = true)
    @Mapping(target = "directors", ignore = true)
    @Mapping(target = "averageRating", ignore = true)
    @Mapping(target = "ratingCount", ignore = true)
    @Mapping(target = "followCount", ignore = true)
    @Mapping(target = "episodeCount", ignore = true)
    @Mapping(target = "episodes", ignore = true)
    @Mapping(target = "casts", ignore = true)
    void updateFilm(@MappingTarget Film film, FilmRequest request);

    FilmResponse toFilmResponse(Film film);

    FilmSummaryResponse toFilmSummaryResponse(Film film);

    FilmCastResponse toFilmCastResponse(FilmCast filmCast);

    List<FilmCastResponse> toFilmCastResponseList(List<FilmCast> filmCasts);

    FilmDirectorResponse toFilmDirectorResponse(FilmDirector filmDirector);

    List<FilmDirectorResponse> toFilmDirectorResponseList(List<FilmDirector> filmDirectors);
}
