package com.MyProject.socket_service.repository.httpclient;

import com.MyProject.common.dto.response.ApiResponse;
import com.MyProject.socket_service.dto.response.IntrospectResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "chat-identity-service", url = "${app.services.identity.url}")
public interface IdentityClient {
    @PostMapping(value = "/identities/auth/introspect")
    ApiResponse<IntrospectResponse> introspect(@RequestParam("token") String token);
}
