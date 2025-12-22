package com.MyProject.friend_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class FriendServiceApplication {

	static void main(String[] args) {
		SpringApplication.run(FriendServiceApplication.class, args);
	}

}
