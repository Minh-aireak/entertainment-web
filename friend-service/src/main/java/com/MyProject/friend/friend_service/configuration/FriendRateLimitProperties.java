package com.MyProject.friend.friend_service.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "app.rate-limit")
public class FriendRateLimitProperties {
    Rule sendFriendRequest = new Rule();
    Rule acceptFriendRequest = new Rule();
    Rule unfriend = new Rule();
    Rule readFriends = new Rule();
    Rule searchFriends = new Rule();
    Rule countFriends = new Rule();
    Rule readFriendRequests = new Rule();

    @Data
    public static class Rule {
        long limitForPeriod;
        Duration limitRefreshPeriod = Duration.ofSeconds(1);
    }
}
