package com.MyProject.notification.notification_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class NotificationServiceApplication {

	static void main(String[] args) {
		SpringApplication.run(NotificationServiceApplication.class, args);
	}
}
