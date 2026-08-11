package com.MyProject.film.film_service.service;

import com.MyProject.film.film_service.dto.request.FilmRequest;
import com.MyProject.film.film_service.dto.response.CommentResponse;
import com.MyProject.film.film_service.dto.response.ActorResponse;
import com.MyProject.film.film_service.dto.response.DirectorResponse;
import com.MyProject.film.film_service.dto.response.FilmAggregateResponse;
import com.MyProject.film.film_service.dto.response.FilmDetailResponse;
import com.MyProject.film.film_service.dto.response.FilmResponse;
import com.MyProject.film.film_service.dto.response.FilmSummaryResponse;
import com.MyProject.film.film_service.entity.*;
import com.MyProject.film.film_service.enums.Country;
import com.MyProject.film.film_service.enums.FilmCategory;
import com.MyProject.film.film_service.enums.FilmSortField;
import com.MyProject.film.film_service.enums.FilmStatus;
import com.MyProject.film.film_service.enums.Genre;
import com.MyProject.film.film_service.mapper.FilmMapper;
import com.MyProject.film.film_service.repository.elasticsearch.FilmElasticRepository;
import com.MyProject.film.film_service.repository.httpclient.FileClient;
import com.MyProject.film.film_service.repository.mysql.*;
import com.MyProject.film.film_service.document.FilmDoc;
import com.MyProject.common.redis.RedisService;
import com.MyProject.film.film_service.exception.AppException;
import com.MyProject.film.film_service.enums.ErrorCode;
import com.MyProject.common.security.SecurityUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.MyProject.film.film_service.dto.request.RatingRequest;
import com.MyProject.common.dto.response.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.MyProject.film.film_service.dto.event.RatingEvent;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FilmService {
    FilmRepository filmRepository;
    FilmMapper filmMapper;
    RedisService redisService;
    OutboxRepository outboxRepository;
    ObjectMapper objectMapper;
    RatingRepository ratingRepository;
    EpisodeRepository episodeRepository;
    DirectorRepository directorRepository;
    ActorRepository actorRepository;
    FilmCastRepository filmCastRepository;
    FilmDirectorRepository filmDirectorRepository;
    FilmElasticRepository filmElasticRepository;
    FilmFollowService filmFollowService;
    CommentExternalService commentExternalService;
    FileClient fileClient;

    // Nếu thumbnail được upload qua file-service (bucket B2 private), thumbnailUrl lưu trong DB/cache
    // chỉ là metadata cũ/rỗng - phải resolve presigned URL mới ở đây mỗi lần trả response, nếu không
    // URL sẽ hết hạn sau ~1h (xem B2_PRESIGNED_URL_TTL). thumbnailUrl do admin tự nhập (external) thì
    // giữ nguyên. Áp dụng sau khi đọc từ cache/Elasticsearch (không phải trước khi ghi cache) để mỗi
    // lần trả về client luôn là URL còn hiệu lực, kể cả khi dữ liệu gốc đã nằm trong cache lâu.
    private FilmResponse resolveThumbnail(FilmResponse response) {
        if (response.getThumbnailFileId() == null || response.getThumbnailFileId().isBlank()) {
            return response;
        }
        try {
            response.setThumbnailUrl(fileClient.getFileInfo(response.getThumbnailFileId()).getResult().getUrl());
        } catch (Exception e) {
            log.warn("Failed to resolve thumbnail file {} for film {}", response.getThumbnailFileId(), response.getId(), e);
        }
        return response;
    }

    private void resolveAvatar(ActorResponse actor) {
        if (actor == null || actor.getAvatarFileId() == null || actor.getAvatarFileId().isBlank()) {
            return;
        }
        try {
            actor.setAvatarUrl(fileClient.getFileInfo(actor.getAvatarFileId()).getResult().getUrl());
        } catch (Exception e) {
            log.warn("Failed to resolve avatar file {} for actor {}", actor.getAvatarFileId(), actor.getId(), e);
        }
    }

    private void resolveAvatar(DirectorResponse director) {
        if (director == null || director.getAvatarFileId() == null || director.getAvatarFileId().isBlank()) {
            return;
        }
        try {
            director.setAvatarUrl(fileClient.getFileInfo(director.getAvatarFileId()).getResult().getUrl());
        } catch (Exception e) {
            log.warn("Failed to resolve avatar file {} for director {}", director.getAvatarFileId(), director.getId(), e);
        }
    }

    // FilmResponse is mapped directly from the Film aggregate, so it does not pass through
    // ActorService/DirectorService. Resolve nested people here as well as the film thumbnail;
    // otherwise detail responses contain avatarFileId but a null avatarUrl.
    private FilmResponse resolveDetailMedia(FilmResponse response) {
        resolveThumbnail(response);
        if (response.getCasts() != null) {
            response.getCasts().forEach(cast -> {
                if (cast != null) {
                    resolveAvatar(cast.getActor());
                }
            });
        }
        if (response.getDirectors() != null) {
            response.getDirectors().forEach(filmDirector -> {
                if (filmDirector != null) {
                    resolveAvatar(filmDirector.getDirector());
                }
            });
        }
        return response;
    }

    private FilmSummaryResponse resolveThumbnail(FilmSummaryResponse response) {
        if (response.getThumbnailFileId() == null || response.getThumbnailFileId().isBlank()) {
            return response;
        }
        try {
            response.setThumbnailUrl(fileClient.getFileInfo(response.getThumbnailFileId()).getResult().getUrl());
        } catch (Exception e) {
            log.warn("Failed to resolve thumbnail file {} for film {}", response.getThumbnailFileId(), response.getId(), e);
        }
        return response;
    }

    private void resolveThumbnails(PageResponse<FilmSummaryResponse> pageResponse) {
        if (pageResponse == null || pageResponse.getData() == null) {
            return;
        }
        pageResponse.setData(pageResponse.getData().stream()
                .map(this::resolveThumbnail)
                .collect(Collectors.toList()));
    }

    // Nếu request đi kèm thumbnailFileId (upload qua file-service), thumbnailUrl gửi lên chỉ là URL
    // preview tạm thời (xem AvatarUploadField ở frontend) - không lưu vào DB, để tránh baked-in một
    // presigned URL sẽ hết hạn sau ~1h. resolveThumbnail() luôn resolve lại URL mới từ thumbnailFileId
    // khi đọc.
    private void clearStalePreviewUrl(Film film) {
        if (film.getThumbnailFileId() != null && !film.getThumbnailFileId().isBlank()) {
            film.setThumbnailUrl(null);
        }
    }

    @Transactional
    public FilmResponse createFilm(FilmRequest request) {
        Film film = filmMapper.toFilm(request);
        if (request.getStatus() != null) {
            film.setStatus(request.getStatus());
        }
        clearStalePreviewUrl(film);

        var filmSaved = filmRepository.save(film);

        if (request.getDirectorIds() != null && !request.getDirectorIds().isEmpty()) {
            List<FilmDirector> directors = resolveFilmDirectors(filmSaved, request.getDirectorIds());
            filmDirectorRepository.saveAll(directors);
            filmSaved.setDirectors(directors);
        }

        if (request.getCasts() != null && !request.getCasts().isEmpty()) {
            List<FilmCast> casts = request.getCasts().stream().map(castReq -> {
                Actor actor = actorRepository.findById(castReq.getActorId())
                        .orElseThrow(() -> new AppException(ErrorCode.ACTOR_NOT_FOUND));
                return FilmCast.builder()
                        .film(filmSaved)
                        .actor(actor)
                        .characterName(castReq.getCharacterName())
                        .displayOrder(castReq.getDisplayOrder())
                        .build();
            }).collect(Collectors.toList());
            filmCastRepository.saveAll(casts);
            filmSaved.setCasts(casts);
        }

        syncFilmToElasticsearch(filmSaved);

        FilmResponse response = filmMapper.toFilmResponse(filmSaved);
        // Invalidate caches on creation
        invalidateFilmCaches(filmSaved.getId());

        return resolveThumbnail(response);
    }

    private List<FilmDirector> resolveFilmDirectors(Film film, List<String> directorIds) {
        List<FilmDirector> directors = new ArrayList<>();
        for (int i = 0; i < directorIds.size(); i++) {
            Director director = directorRepository.findById(directorIds.get(i))
                    .orElseThrow(() -> new AppException(ErrorCode.DIRECTOR_NOT_FOUND));
            directors.add(FilmDirector.builder()
                    .film(film)
                    .director(director)
                    .displayOrder(i)
                    .build());
        }
        return directors;
    }

    @Transactional
    public FilmResponse updateFilm(String id, FilmRequest request) {
        Film film = filmRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.FILM_NOT_FOUND));

        filmMapper.updateFilm(film, request);
        if (request.getStatus() != null) {
            film.setStatus(request.getStatus());
        }
        clearStalePreviewUrl(film);

        var filmSaved = filmRepository.save(film);

        if (request.getDirectorIds() != null) {
            filmDirectorRepository.deleteByFilm_Id(filmSaved.getId());
            List<FilmDirector> directors = resolveFilmDirectors(filmSaved, request.getDirectorIds());
            filmDirectorRepository.saveAll(directors);
            filmSaved.setDirectors(directors);
        }

        if (request.getCasts() != null) {
            filmCastRepository.deleteByFilm_Id(filmSaved.getId());
            List<FilmCast> casts = request.getCasts().stream().map(castReq -> {
                Actor actor = actorRepository.findById(castReq.getActorId())
                        .orElseThrow(() -> new AppException(ErrorCode.ACTOR_NOT_FOUND));
                return FilmCast.builder()
                        .film(filmSaved)
                        .actor(actor)
                        .characterName(castReq.getCharacterName())
                        .displayOrder(castReq.getDisplayOrder())
                        .build();
            }).collect(Collectors.toList());
            filmCastRepository.saveAll(casts);
            filmSaved.setCasts(casts);
        }

        syncFilmToElasticsearch(filmSaved);

        FilmResponse response = filmMapper.toFilmResponse(filmSaved);
        invalidateFilmCaches(filmSaved.getId());

        return resolveThumbnail(response);
    }

    public PageResponse<FilmSummaryResponse> getPageFilms(int page, int size) {
        String cacheKey = "film:latest:page:" + page + ":size:" + size;

        // Cache only for the first two pages
        if (page <= 2) {
            try {
                PageResponse<FilmSummaryResponse> cached = redisService.get(
                        cacheKey,
                        new TypeReference<PageResponse<FilmSummaryResponse>>() {}
                );
                if (cached != null) {
                    log.info("Cache hit for films page: {}", page);
                    resolveThumbnails(cached);
                    return cached;
                }
            } catch (Exception e) {
                log.warn("Failed to retrieve from cache for films page: {}", page, e);
            }
        }

        Sort sort = Sort.by(Sort.Direction.DESC, "lastUpdate");
        Pageable pageable = PageRequest.of(page - 1, size, sort);
        Page<Film> pageData = filmRepository.findAll(pageable);

        PageResponse<FilmSummaryResponse> response = PageResponse.<FilmSummaryResponse>builder()
                .currentPage(page)
                .pageSize(size)
                .totalPages(pageData.getTotalPages())
                .totalElement(pageData.getTotalElements())
                .data(pageData.getContent().stream()
                        .map(filmMapper::toFilmSummaryResponse)
                        .collect(Collectors.toList()))
                .build();

        // Save to cache only for the first two pages
        if (page <= 2) {
            try {
                redisService.setWithExpiration(cacheKey, response, 10, TimeUnit.MINUTES);
                log.info("Cached films page: {}", page);
            } catch (Exception e) {
                log.warn("Failed to cache films page: {}", page, e);
            }
        }

        resolveThumbnails(response);
        return response;
    }

    public FilmAggregateResponse getAggregateFilms() {
        String hotCacheKey = "film:hot:page:1:size:10";
        String latestCacheKey = "film:latest:page:1:size:10";

        PageResponse<FilmSummaryResponse> topHotFilms = null;
        PageResponse<FilmSummaryResponse> latestFilms = null;

        try {
            topHotFilms = redisService.get(
                    hotCacheKey,
                    new TypeReference<PageResponse<FilmSummaryResponse>>() {}
            );

            latestFilms = redisService.get(
                    latestCacheKey,
                    new TypeReference<PageResponse<FilmSummaryResponse>>() {}
            );

        } catch (Exception e) {
            log.warn("Failed to retrieve from cache in getAggregateFilms", e);
        }

        if (topHotFilms == null) {
            // Fetch 10 hot films to split into 2 pages of 5
            Pageable hotPageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "followCount"));
            Page<Film> hotFilmsData = filmRepository.findAll(hotPageable);
            
            List<FilmSummaryResponse> allHotFilms = hotFilmsData.getContent().stream()
                    .map(filmMapper::toFilmSummaryResponse)
                    .collect(Collectors.toList());

            topHotFilms = PageResponse.<FilmSummaryResponse>builder()
                    .currentPage(1)
                    .pageSize(10)
                    .totalPages((int) Math.ceil(hotFilmsData.getTotalElements() / 5.0))
                    .totalElement(hotFilmsData.getTotalElements())
                    .data(allHotFilms)
                    .build();
            
            try {
                redisService.setWithExpiration(hotCacheKey, topHotFilms, 5, TimeUnit.MINUTES);
            } catch (Exception e) {
                log.warn("Failed to cache hot films", e);
            }
        }

        if (latestFilms == null) {
            Pageable latestPageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "lastUpdate"));
            Page<Film> latestFilmsData = filmRepository.findAll(latestPageable);
            
            latestFilms = PageResponse.<FilmSummaryResponse>builder()
                    .currentPage(1)
                    .pageSize(10)
                    .totalPages(latestFilmsData.getTotalPages())
                    .totalElement(latestFilmsData.getTotalElements())
                    .data(latestFilmsData.getContent().stream()
                            .map(filmMapper::toFilmSummaryResponse)
                            .collect(Collectors.toList()))
                    .build();
            
            try {
                redisService.setWithExpiration(latestCacheKey, latestFilms, 1, TimeUnit.HOURS);
            } catch (Exception e) {
                log.warn("Failed to cache latest films", e);
            }
        }

        resolveThumbnails(topHotFilms);
        resolveThumbnails(latestFilms);

        return FilmAggregateResponse.builder()
                .topHotFilms(topHotFilms)
                .latestFilms(latestFilms)
                .build();
    }

    @Transactional
    public void syncFilmToElasticsearch(Film film) {
        FilmDoc filmDoc = FilmDoc.builder()
                .id(film.getId())
                .title(film.getTitle())
                .thumbnailUrl(film.getThumbnailUrl())
                .thumbnailFileId(film.getThumbnailFileId())
                .averageRating(film.getAverageRating())
                .ratingCount(film.getRatingCount())
                .followCount(film.getFollowCount())
                .episodeCount(film.getEpisodeCount())
                .season(film.getSeason())
                .status(film.getStatus() == null ? null : film.getStatus().name())
                .lastUpdate(film.getLastUpdate())
                .build();

        try {
            String payload = objectMapper.writeValueAsString(filmDoc);
            Outbox outbox = Outbox.builder()
                    .aggregateId(film.getId())
                    .topic("film.sync")
                    .payload(payload)
                    .build();
            outboxRepository.save(outbox);
            log.info("Saved outbox record for film: {}", film.getId());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize film doc for outbox", e);
            throw new RuntimeException("Failed to serialize film doc", e);
        }
    }

    @Transactional
    public Integer rateFilm(String filmId, RatingRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        Film film = filmRepository.findById(filmId)
                .orElseThrow(() -> new AppException(ErrorCode.FILM_NOT_FOUND));

        Optional<Rating> existingRating = ratingRepository.findByFilmAndUserId(film, userId);
        int oldStars = existingRating.map(Rating::getStars).orElse(0);

        // 1. Cập nhật bảng Rating ngay lập tức
        if (existingRating.isPresent()) {
            Rating rating = existingRating.get();
            rating.setStars(request.getStars());
            ratingRepository.save(rating);
        } else {
            ratingRepository.save(Rating.builder()
                    .film(film)
                    .userId(userId)
                    .stars(request.getStars())
                    .build());
        }

        // 2. Gửi event qua Outbox để cập nhật Film statistics (averageRating, ratingCount) qua Kafka
        RatingEvent ratingEvent = RatingEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .filmId(filmId)
                .userId(userId)
                .stars(request.getStars())
                .oldStars(oldStars)
                .build();

        try {
            String payload = objectMapper.writeValueAsString(ratingEvent);
            Outbox outbox = Outbox.builder()
                    .aggregateId(filmId)
                    .topic("film.rating")
                    .payload(payload)
                    .build();
            outboxRepository.save(outbox);
            log.info("Saved outbox record for rating update: film={}, user={}", filmId, userId);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize rating event for outbox", e);
            throw new RuntimeException("Failed to serialize rating event for outbox", e);
        }

        // 3. Invalidate cache
        invalidateFilmCaches(filmId);

        // 4. Trả về số sao đánh giá
        return request.getStars();
    }

    /**
     * Clears every cached representation that contains mutable film summary fields such as
     * episodeCount. EpisodeService also calls this after an episode is created, renumbered,
     * moved, or deleted so the administration list cannot keep an old count for ten minutes.
     */
    public void invalidateFilmCaches(String filmId) {
        try {
            redisService.delete("film:detail:" + filmId);
            redisService.deletePattern("film:comments:" + filmId + ":*");
            redisService.deletePattern("film:latest:page:*");
            redisService.deletePattern("film:hot:page:*");
            redisService.deletePattern("film:ongoing:page:*");
            log.info("Invalidated film caches for film: {}", filmId);
        } catch (Exception e) {
            log.warn("Failed to invalidate caches for film: {}", filmId, e);
        }
    }

    public PageResponse<FilmSummaryResponse> getNowPlayingFilms(int page, int size) {
        String cacheKey = "film:ongoing:page:" + page + ":size:" + size;

        if (page <= 2) {
            try {
                PageResponse<FilmSummaryResponse> cached = redisService.get(
                        cacheKey,
                        new TypeReference<PageResponse<FilmSummaryResponse>>() {}
                );
                if (cached != null) {
                    log.info("Cache hit for ongoing films page: {}", page);
                    resolveThumbnails(cached);
                    return cached;
                }
            } catch (Exception e) {
                log.warn("Failed to retrieve from cache for ongoing films page: {}", page, e);
            }
        }

        Sort sort = Sort.by(Sort.Direction.DESC, "followCount");
        Pageable pageable = PageRequest.of(page - 1, size, sort);
        Page<Film> pageData = filmRepository.findByStatus(FilmStatus.ONGOING, pageable);

        PageResponse<FilmSummaryResponse> response = PageResponse.<FilmSummaryResponse>builder()
                .currentPage(page)
                .pageSize(size)
                .totalPages(pageData.getTotalPages())
                .totalElement(pageData.getTotalElements())
                .data(pageData.getContent().stream()
                        .map(filmMapper::toFilmSummaryResponse)
                        .collect(Collectors.toList()))
                .build();

        if (page <= 2) {
            try {
                redisService.setWithExpiration(cacheKey, response, 10, TimeUnit.MINUTES);
                log.info("Cached now playing films page: {}", page);
            } catch (Exception e) {
                log.warn("Failed to cache now playing films page: {}", page, e);
            }
        }

        resolveThumbnails(response);
        return response;
    }

    public List<FilmSummaryResponse> getTopRatedFilms(int limit) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "averageRating"));
        return filmRepository.findAll(pageable).getContent().stream()
                .map(filmMapper::toFilmSummaryResponse)
                .map(this::resolveThumbnail)
                .collect(Collectors.toList());
    }

    public PageResponse<FilmSummaryResponse> browseFilms(FilmCategory category, Country country, Genre genre,
                                                           FilmSortField sortBy, Sort.Direction sortDir,
                                                           int page, int size) {
        Sort sort = Sort.by(sortDir, sortBy.getFieldName());
        Pageable pageable = PageRequest.of(page - 1, size, sort);

        Page<Film> pageData = switch (category) {
            case SERIES -> filmRepository.findSeriesOrStandalone(true, Genre.ANIMATION, country, genre, pageable);
            case STANDALONE -> filmRepository.findSeriesOrStandalone(false, Genre.ANIMATION, country, genre, pageable);
            case ANIMATION -> filmRepository.findByGenreContaining(Genre.ANIMATION, country, pageable);
        };

        return PageResponse.<FilmSummaryResponse>builder()
                .currentPage(page)
                .pageSize(size)
                .totalPages(pageData.getTotalPages())
                .totalElement(pageData.getTotalElements())
                .data(pageData.getContent().stream()
                        .map(filmMapper::toFilmSummaryResponse)
                        .map(this::resolveThumbnail)
                        .collect(Collectors.toList()))
                .build();
    }

    public List<FilmSummaryResponse> searchFilms(String title) {
        return filmElasticRepository.findByTitleContaining(title).stream()
                .map(doc -> FilmSummaryResponse.builder()
                        .id(doc.getId())
                        .title(doc.getTitle())
                        .thumbnailUrl(doc.getThumbnailUrl())
                        .thumbnailFileId(doc.getThumbnailFileId())
                        .season(doc.getSeason())
                        .status(FilmStatus.fromIndexedValue(doc.getStatus()))
                        .ratingCount(doc.getRatingCount())
                        .followCount(doc.getFollowCount())
                        .episodeCount(doc.getEpisodeCount())
                        .averageRating(doc.getAverageRating())
                        .lastUpdate(doc.getLastUpdate())
                        .build())
                .map(this::resolveThumbnail)
                .collect(Collectors.toList());
    }

    public FilmDetailResponse getFilm(String id) {
        String filmCacheKey = "film:detail:" + id;
        String commentsCacheKey = "film:comments:" + id + ":page:1";
        
        log.info("Fetching film detail for ID: {}", id);

        // 1. Lấy FilmResponse (từ cache hoặc DB)
        FilmResponse filmResponse = null;
        try {
            filmResponse = redisService.get(filmCacheKey, new TypeReference<FilmResponse>() {});
        } catch (Exception e) {
            log.warn("Failed to retrieve film basic info from cache", e);
        }

        if (filmResponse == null) {
            log.info("Film basic cache miss for ID: {}. Fetching from DB...", id);
            Film film = filmRepository.findById(id)
                    .orElseThrow(() -> new AppException(ErrorCode.FILM_NOT_FOUND));
            filmResponse = filmMapper.toFilmResponse(film);
            try {
                redisService.setWithExpiration(filmCacheKey, filmResponse, 1, TimeUnit.HOURS);
            } catch (Exception e) {
                log.warn("Failed to cache film basic info", e);
            }
        }

        filmResponse = resolveDetailMedia(filmResponse);

        // 2. Lấy Comments (từ cache hoặc Service)
        PageResponse<CommentResponse> comments = null;
        try {
            comments = redisService.get(commentsCacheKey, new TypeReference<PageResponse<CommentResponse>>() {});
        } catch (Exception e) {
            log.warn("Failed to retrieve comments from cache", e);
        }

        if (comments == null) {
            try {
                comments = commentExternalService.getComments(id, 1, 10)
                        .handle((res, ex) -> ex == null ? res : null)
                        .join();
                
                if (comments != null && comments.getData() != null && !comments.getData().isEmpty()) {
                    redisService.setWithExpiration(commentsCacheKey, comments, 30, TimeUnit.MINUTES);
                }
            } catch (Exception e) {
                log.error("Failed to fetch comments for film {}", id, e);
            }
        }

        // 3. Lấy thông tin cá nhân hóa (không cache: follow, rating)
        Integer userRating = 0;
        String userId = SecurityUtils.getCurrentUserId();

        boolean followed = filmFollowService.isFollowing(id);
        Optional<Rating> rating = ratingRepository.findByFilmIdAndUserId(id, userId);
        userRating = rating.map(Rating::getStars).orElse(0);

        return FilmDetailResponse.builder()
                .film(filmResponse)
                .userRating(userRating)
                .followed(followed)
                .comments(comments)
                .build();
    }
}
