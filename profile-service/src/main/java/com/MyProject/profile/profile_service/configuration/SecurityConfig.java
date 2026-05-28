package com.MyProject.profile.profile_service.configuration;

import com.MyProject.common.security.CommonJwtAuthenticationEntryPoint;
import com.MyProject.common.security.CommonJwtDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    private final String[] publicEndpoint = {};

    private final CommonJwtDecoder jwtDecoder;
    private final JwtAuthenticationConverter jwtAuthenticationConverter;

    public SecurityConfig(CommonJwtDecoder jwtDecoder, JwtAuthenticationConverter jwtAuthenticationConverter) {
        this.jwtDecoder = jwtDecoder;
        this.jwtAuthenticationConverter = jwtAuthenticationConverter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity httpSecurity) throws Exception {
        httpSecurity.authorizeHttpRequests(request ->
                request.requestMatchers(publicEndpoint).permitAll()
                .anyRequest()
                .authenticated());

        httpSecurity.csrf(AbstractHttpConfigurer::disable);

        httpSecurity.oauth2ResourceServer(oauth2 ->
                oauth2.jwt(jwtConfigurer ->
                                jwtConfigurer.decoder(jwtDecoder)
                                        .jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(new CommonJwtAuthenticationEntryPoint()));
        return httpSecurity.build();
    }
}
