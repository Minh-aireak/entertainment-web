package com.MyProject.identity.identity_service.service;

import com.MyProject.identity.identity_service.dto.response.ExchangeTokenResponse;
import com.MyProject.identity.identity_service.dto.response.OutboundUserResponse;
import com.MyProject.identity.identity_service.exception.AppException;
import com.MyProject.identity.identity_service.exception.ErrorCode;
import com.MyProject.identity.identity_service.repository.httpclient.OutboundIdentityClient;
import com.MyProject.identity.identity_service.repository.httpclient.OutboundUserClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class GoogleOAuthClient {

    private final OutboundIdentityClient outboundIdentityClient;
    private final OutboundUserClient outboundUserClient;

    @CircuitBreaker(name = "outboundIdentity", fallbackMethod = "exchangeTokenFallback")
    @Retry(name = "outboundIdentity")
    public ExchangeTokenResponse exchangeToken(MultiValueMap<String, String> data) {
        return outboundIdentityClient.exchangeToken(data);
    }

    @CircuitBreaker(name = "outboundUserClient", fallbackMethod = "getInfoFallback")
    @Retry(name = "outboundUserClient")
    public OutboundUserResponse getInfo(String alt, String accessToken) {
        return outboundUserClient.getInfo(alt, accessToken);
    }

    public ExchangeTokenResponse exchangeTokenFallback(MultiValueMap<String, String> data, Throwable ex) {
        log.error("Fallback for Google token exchange. Reason: {}", ex.getMessage());
        throw new AppException(ErrorCode.SERVICE_UNAVAILABLE);
    }

    public OutboundUserResponse getInfoFallback(String alt, String accessToken, Throwable ex) {
        log.error("Fallback for Google userinfo. Reason: {}", ex.getMessage());
        throw new AppException(ErrorCode.SERVICE_UNAVAILABLE);
    }
}
