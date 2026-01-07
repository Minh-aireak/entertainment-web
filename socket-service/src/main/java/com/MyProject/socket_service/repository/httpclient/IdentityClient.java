package com.MyProject.socket_service.repository.httpclient;

import com.MyProject.common.dto.request.IntrospectRequest;
import com.MyProject.common.dto.response.IntrospectResponse;
import com.MyProject.socket_service.dto.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "chat-identity-service", url = "${app.services.identity.url}")
public interface IdentityClient {
    @PostMapping(value = "/auth/introspect")
    ApiResponse<IntrospectResponse> introspect(@RequestBody IntrospectRequest request);
}
