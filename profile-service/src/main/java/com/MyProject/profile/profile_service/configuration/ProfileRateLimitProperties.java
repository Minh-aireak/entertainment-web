package com.MyProject.profile.profile_service.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "app.rate-limit")
public class ProfileRateLimitProperties {
    Rule profileWrite = new Rule();
    Rule profileRead = new Rule();
    Rule profileSearch = new Rule();
    Rule profileSummary = new Rule();

    @Data
    public static class Rule {
        long limitForPeriod;
        Duration limitRefreshPeriod = Duration.ofSeconds(1);
    }
}
