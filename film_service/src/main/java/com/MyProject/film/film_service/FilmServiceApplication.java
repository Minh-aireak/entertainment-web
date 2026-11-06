package com.MyProject.film.film_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableFeignClients
public class FilmServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(FilmServiceApplication.class, args);
	}

}
