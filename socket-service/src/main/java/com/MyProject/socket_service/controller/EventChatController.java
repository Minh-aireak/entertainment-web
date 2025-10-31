//package com.MyProject.socket_service.controller;
//
//import lombok.AccessLevel;
//import lombok.RequiredArgsConstructor;
//import lombok.experimental.FieldDefaults;
//import org.springframework.kafka.annotation.KafkaListener;
//import org.springframework.stereotype.Component;
//
//@Component
//@RequiredArgsConstructor
//@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
//public class EventController {
//
//    @KafkaListener(topics = "profile-updated")
//    public void handleProfileUpdatedEvent(ProfileUpdatedEvent event) {
//        userProfileService.updateUserProfile(event);
//    }
//}
