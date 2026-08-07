package com.MyProject.room_service.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "app.rate-limit")
public class RoomRateLimitProperties {
    Rule roomCreate = new Rule();
    Rule roomRead = new Rule();
    Rule roomPlayback = new Rule();
    Rule roomMessage = new Rule();

    @Data
    public static class Rule {
        long limitForPeriod;
        Duration limitRefreshPeriod = Duration.ofSeconds(1);
    }
}
