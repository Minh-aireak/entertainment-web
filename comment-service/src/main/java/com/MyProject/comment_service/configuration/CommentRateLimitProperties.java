package com.MyProject.comment_service.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "app.rate-limit")
public class CommentRateLimitProperties {
    Rule commentWrite = new Rule();
    Rule commentRead = new Rule();
    Rule commentUpdate = new Rule();
    Rule commentDelete = new Rule();

    @Data
    public static class Rule {
        long limitForPeriod;
        Duration limitRefreshPeriod = Duration.ofSeconds(1);
    }
}
