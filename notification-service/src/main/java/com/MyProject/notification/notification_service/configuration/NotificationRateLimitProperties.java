
package com.MyProject.notification.notification_service.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "app.rate-limit")
public class NotificationRateLimitProperties {
    Rule getMyNotifications = new Rule();
    Rule getUnreadCount = new Rule();
    Rule markAllAsRead = new Rule();

    @Data
    public static class Rule {
        long limitForPeriod;
        Duration limitRefreshPeriod = Duration.ofSeconds(1);
    }
}
