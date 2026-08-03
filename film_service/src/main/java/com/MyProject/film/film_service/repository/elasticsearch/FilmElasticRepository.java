package com.MyProject.film.film_service.repository.elasticsearch;

import com.MyProject.film.film_service.document.FilmDoc;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FilmElasticRepository extends ElasticsearchRepository<FilmDoc, String> {
    List<FilmDoc> findByTitleContaining(String title);
}
