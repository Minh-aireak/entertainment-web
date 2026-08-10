package com.MyProject.api_gateway.configuration;

import com.MyProject.common.dto.response.ApiResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtGrantedAuthoritiesConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;

import java.util.List;

@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SecurityConfig {
    CustomJwtDecoder customJwtDecoder;
    ObjectMapper objectMapper;

    String[] publicEndpoints = {
            "/api/v1/identities/auth/login",
            "/api/v1/identities/auth/introspect",
            "/api/v1/identities/auth/outbound/google",
            "/api/v1/identities/auth/refresh-token",
            "/api/v1/identities/auth/logout",
            "/api/v1/identities/users/forgot-password",
            "/api/v1/identities/users/reset-password",
            "/api/v1/identities/users/registration",
            "/api/v1/files/media/info/**",
            "/api/v1/sockets/**",
    };

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http.authorizeExchange(exchanges -> exchanges
                .pathMatchers(publicEndpoints).permitAll()
                .pathMatchers(HttpMethod.GET,
                        "/api/v1/films/follows/my",
                        "/api/v1/comments/**"
                ).authenticated()
                .pathMatchers(HttpMethod.GET, "/api/v1/films/**").permitAll()
                .anyExchange().authenticated()
        );

        http.oauth2ResourceServer(oauth2 -> oauth2
                .bearerTokenConverter(cookieServerAuthenticationConverter())
                .jwt(jwtSpec -> jwtSpec
                        .jwtDecoder(customJwtDecoder)
                        .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
                .authenticationEntryPoint(customAuthenticationEntryPoint())
        );

        http.cors(corsSpec -> corsSpec.configurationSource(corsConfigurationSource()));
        http.csrf(ServerHttpSecurity.CsrfSpec::disable);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration corsConfiguration = new CorsConfiguration();
        corsConfiguration.setAllowedOrigins(List.of("http://localhost:5173"));
        corsConfiguration.setAllowedHeaders(List.of("*"));
        corsConfiguration.setAllowedMethods(List.of("*"));
        corsConfiguration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource urlBasedCorsConfigurationSource = new UrlBasedCorsConfigurationSource();
        urlBasedCorsConfigurationSource.registerCorsConfiguration("/**", corsConfiguration);

        return urlBasedCorsConfigurationSource;
    }

    @Bean
    public CookieServerAuthenticationConverter cookieServerAuthenticationConverter() {
        return new CookieServerAuthenticationConverter();
    }

    @Bean
    public ReactiveJwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter jwtGrantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        jwtGrantedAuthoritiesConverter.setAuthorityPrefix("");

        ReactiveJwtAuthenticationConverter jwtAuthenticationConverter = new ReactiveJwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(
                new ReactiveJwtGrantedAuthoritiesConverterAdapter(jwtGrantedAuthoritiesConverter));
        return jwtAuthenticationConverter;
    }

    @Bean
    public ServerAuthenticationEntryPoint customAuthenticationEntryPoint() {
        return (exchange, e) -> {
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

            ApiResponse<?> apiResponse = ApiResponse.builder()
                    .code(1401)
                    .message("Unauthenticated!")
                    .build();

            try {
                String body = objectMapper.writeValueAsString(apiResponse);
                DataBufferFactory bufferFactory = response.bufferFactory();
                DataBuffer buffer = bufferFactory.wrap(body.getBytes());
                return response.writeWith(Mono.just(buffer));
            } catch (JsonProcessingException ex) {
                return Mono.error(ex);
            }
        };
    }
}
