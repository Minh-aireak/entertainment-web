package com.MyProject.post.post_service.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "app.rate-limit")
public class PostRateLimitProperties {
    Rule postWrite = new Rule();
    Rule postUpdate = new Rule();
    Rule postDelete = new Rule();
    Rule postRead = new Rule();
    Rule postSearch = new Rule();
    Rule postCount = new Rule();
    Rule postRandom = new Rule();

    @Data
    public static class Rule {
        long limitForPeriod;
        Duration limitRefreshPeriod = Duration.ofSeconds(1);
    }
}
