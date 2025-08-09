package com.MyProject.identity.identity_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class TravelPlannerItineraryManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TravelPlannerItineraryManagerApplication.class, args);
    }
}
