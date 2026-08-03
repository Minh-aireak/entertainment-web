package com.MyProject.identity.identity_service.repository.httpclient;

import com.MyProject.identity.identity_service.configuration.OutboundFeignConfig;
import com.MyProject.identity.identity_service.dto.response.ExchangeTokenResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "outbound-identity", url = "https://oauth2.googleapis.com", configuration = OutboundFeignConfig.class)
public interface OutboundIdentityClient {
    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    ExchangeTokenResponse exchangeToken(@RequestBody MultiValueMap<String, String> data);
}
