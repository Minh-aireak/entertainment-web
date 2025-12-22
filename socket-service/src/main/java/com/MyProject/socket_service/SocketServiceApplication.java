package com.MyProject.socket_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class SocketServiceApplication {

	static void main(String[] args) {
		SpringApplication.run(SocketServiceApplication.class, args);
	}

}
