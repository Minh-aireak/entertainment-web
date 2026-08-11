package com.MyProject.film.film_service;

import com.MyProject.film.film_service.repository.elasticsearch.FilmElasticRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class FilmServiceApplicationTests {

	@MockitoBean(name = "filmElasticRepository")
	private FilmElasticRepository filmElasticRepository;

	@Test
	void contextLoads() {
	}

}
