package com.MyProject.identity.identity_service.repository.httpclient;

import com.MyProject.identity.identity_service.configuration.AuthenticationRequestInterceptor;
import com.MyProject.identity.identity_service.dto.request.UserProfileCreationRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "profile-service", url = "${app.services.profile}", configuration = {AuthenticationRequestInterceptor.class})
public interface UserProfileClient {
    @PostMapping(value = "/internal/registration", produces = MediaType.APPLICATION_JSON_VALUE)
    void createProfile(@RequestBody UserProfileCreationRequest request);
}
