package com.MyProject.socket_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableFeignClients
@EnableScheduling
public class SocketServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(SocketServiceApplication.class, args);
	}

}
