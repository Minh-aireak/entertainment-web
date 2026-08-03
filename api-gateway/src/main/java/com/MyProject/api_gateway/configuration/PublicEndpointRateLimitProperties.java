package com.MyProject.api_gateway.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "app.gateway.public-rate-limit")
public class PublicEndpointRateLimitProperties {
    private boolean enabled = true;
    private List<Rule> rules = new ArrayList<>();

    @Data
    public static class Rule {
        private String name;
        private String pathPattern;
        private String method;
        private int limit;
        private int windowSeconds;
    }
}
