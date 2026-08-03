package com.MyProject.notification.notification_service.service;

import com.MyProject.notification.notification_service.dto.request.EmailRequest;
import com.MyProject.notification.notification_service.exception.AppException;
import com.MyProject.notification.notification_service.exception.ErrorCode;
import com.MyProject.notification.notification_service.repository.httpclient.EmailClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailExternalService {

    private final EmailClient emailClient;

    @CircuitBreaker(name = "brevoEmailService", fallbackMethod = "sendEmailFallback")
    @RateLimiter(name = "brevoEmailService")
    @Retry(name = "brevoEmailService")
    public void sendEmail(String apiKey, EmailRequest request) {
        emailClient.sendEmail(apiKey, request);
    }

    public void sendEmailFallback(String apiKey, EmailRequest request, Throwable throwable) {
        log.error("Fallback triggered while sending email to {}: {}",
                request.getTo(),
                throwable.getMessage());
        throw new AppException(ErrorCode.CANNOT_SEND_EMAIL);
    }
}
