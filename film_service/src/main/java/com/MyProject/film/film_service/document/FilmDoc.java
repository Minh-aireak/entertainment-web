package com.MyProject.film.film_service.document;

import com.MyProject.film.film_service.enums.FilmStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Document(indexName = "films")
public class FilmDoc {
    @Id
    String id;

    @Field(type = FieldType.Text, analyzer = "standard")
    String title;

    @Field(type = FieldType.Keyword)
    String thumbnailUrl;

    @Field(type = FieldType.Double)
    double averageRating;

    @Field(type = FieldType.Integer)
    int ratingCount;

    @Field(type = FieldType.Integer)
    int followCount;

    @Field(type = FieldType.Integer)
    int episodeCount;

    @Field(type = FieldType.Integer)
    int season;

    @Field(type = FieldType.Keyword)
    FilmStatus status;

    @Field(type = FieldType.Date)
    Instant lastUpdate;
}
