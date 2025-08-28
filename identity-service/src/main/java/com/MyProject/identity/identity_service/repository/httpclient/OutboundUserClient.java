package com.MyProject.identity.identity_service.repository.httpclient;

import com.MyProject.identity.identity_service.dto.response.OutboundUserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "outbound-user-client", url = "https://www.googleapis.com")
public interface OutboundUserClient {
    @GetMapping(value = "/oauth2/v2/userinfo")
    OutboundUserResponse getInfo(@RequestParam("alt") String alt,
                                 @RequestParam("access_token") String accessToken);
}
