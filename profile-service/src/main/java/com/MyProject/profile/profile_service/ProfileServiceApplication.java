package com.MyProject.profile.profile_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class ProfileServiceApplication {

	static void main(String[] args) {
		SpringApplication.run(ProfileServiceApplication.class, args);
	}

}
