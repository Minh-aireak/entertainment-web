package com.MyProject.identity.identity_service.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

/**
 * SecurityConfig requires a JwtAuthenticationConverter bean to build its SecurityFilterChain, but
 * nothing in the app registers one explicitly (the resource-server auto-configuration only wires
 * it internally). @WebMvcTest slices never pick that up, so every controller test that @Imports
 * SecurityConfig needs this alongside it.
 */
public class JwtAuthenticationConverterTestConfig {
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        return new JwtAuthenticationConverter();
    }
}
