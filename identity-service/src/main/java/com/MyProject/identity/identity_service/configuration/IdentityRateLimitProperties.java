package com.MyProject.identity.identity_service.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "app.rate-limit")
public class IdentityRateLimitProperties {
    Rule userRegistration = new Rule();
    Rule changePassword = new Rule();
    Rule forgotPassword = new Rule();
    Rule resetPassword = new Rule();
    Rule userManagement = new Rule();
    Rule roleManagement = new Rule();
    Rule login = new Rule();
    Rule introspect = new Rule();
    Rule logout = new Rule();
    Rule refreshToken = new Rule();
    Rule outboundGoogle = new Rule();

    @Data
    public static class Rule {
        long limitForPeriod;
        Duration limitRefreshPeriod = Duration.ofSeconds(1);
    }
}
