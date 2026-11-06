package com.MyProject.film.film_service.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Repository scanning lives here instead of on {@code FilmServiceApplication}.
 *
 * <p>Test slices such as {@code @WebMvcTest} skip {@code @Configuration} classes found by component
 * scanning, but they always process annotations on the main application class. When these two
 * annotations sat on the main class, every controller test had to mock every repository, and one
 * new repository without a mock ({@code WatchProgressRepository}) broke the context of all of them.
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.MyProject.film.film_service.repository.mysql")
@EnableElasticsearchRepositories(basePackages = "com.MyProject.film.film_service.repository.elasticsearch")
public class RepositoryConfiguration {
}
