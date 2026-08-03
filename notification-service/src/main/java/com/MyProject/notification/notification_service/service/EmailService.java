package com.MyProject.notification.notification_service.service;

import com.MyProject.notification.notification_service.dto.request.EmailRequest;
import com.MyProject.notification.notification_service.dto.request.Sender;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EmailService {
    EmailExternalService emailExternalService;

    @Value("${notification.email.brevo-apikey}")
    @NonFinal
    String apiKey;

    @Value("${notification.email.sender-name}")
    @NonFinal
    String senderName;

    @Value("${notification.email.sender-email}")
    @NonFinal
    String senderEmail;

    @CircuitBreaker(name = "brevoEmailService")
    @Retry(name = "brevoEmailService")
    public void sendEmail(EmailRequest request){
        EmailRequest emailRequest = EmailRequest.builder()
                .sender(Sender.builder()
                        .name(senderName)
                        .email(senderEmail)
                        .build())
                .to(request.getTo())
                .subject(request.getSubject())
                .htmlContent(request.getHtmlContent())
                .build();
        try {
            log.info("Sending email to: {}", request.getTo());
            emailExternalService.sendEmail(apiKey, emailRequest);
        } catch (FeignException exception){
            log.error("Feign error while sending email: Status={}, Content={}, Method={}, URL={}",
                exception.status(), exception.contentUTF8(), exception.request().httpMethod(), exception.request().url());
        }
    }
}
