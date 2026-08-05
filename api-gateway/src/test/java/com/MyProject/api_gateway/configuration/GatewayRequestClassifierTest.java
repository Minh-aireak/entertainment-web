package com.MyProject.api_gateway.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayRequestClassifierTest {

    @Test
    void followedFilmsEndpointRequiresAuthentication() {
        assertFalse(GatewayRequestClassifier.isPublicRequest(
                "/api/v1/films/follows/my",
                HttpMethod.GET
        ));
    }

    @Test
    void regularFilmGetEndpointRemainsPublic() {
        assertTrue(GatewayRequestClassifier.isPublicRequest(
                "/api/v1/films/film-id",
                HttpMethod.GET
        ));
    }
}
