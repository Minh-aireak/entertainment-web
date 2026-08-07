package com.MyProject.room_service.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.room")
public class RoomProperties {
    int maxParticipants = 50;
    int inviteCodeLength = 8;
}
