package com.MyProject.chat_service.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "app.rate-limit")
public class ChatRateLimitProperties {
    Rule messageWrite = new Rule();
    Rule messageUpdate = new Rule();
    Rule messageDelete = new Rule();
    Rule messageRead = new Rule();
    Rule messageSearch = new Rule();
    Rule seen = new Rule();
    Rule unreadCount = new Rule();
    Rule conversationWrite = new Rule();
    Rule conversationRead = new Rule();
    Rule conversationSearch = new Rule();

    @Data
    public static class Rule {
        long limitForPeriod;
        Duration limitRefreshPeriod = Duration.ofSeconds(1);
    }
}
