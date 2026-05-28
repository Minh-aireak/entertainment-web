package com.MyProject.notification.notification_service.service;

import com.MyProject.notification.notification_service.dto.request.EmailRequest;
import com.MyProject.notification.notification_service.dto.request.Sender;
import com.MyProject.notification.notification_service.exception.AppException;
import com.MyProject.notification.notification_service.exception.ErrorCode;
import com.MyProject.notification.notification_service.repository.httpclient.EmailClient;
import feign.FeignException;
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
    EmailClient emailClient;

    @Value("${notification.email.brevo-apikey}")
    @NonFinal
    String apiKey;

    @Value("${notification.email.sender-name}")
    @NonFinal
    String senderName;

    @Value("${notification.email.sender-email}")
    @NonFinal
    String senderEmail;

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
            emailClient.sendEmail(apiKey, emailRequest);
        } catch (FeignException exception){
            log.error("Feign error while sending email: Status={}, Content={}, Method={}, URL={}",
                exception.status(), exception.contentUTF8(), exception.request().httpMethod(), exception.request().url());
            throw new AppException(ErrorCode.CANNOT_SEND_EMAIL);
        } catch (Exception e) {
            log.error("Unexpected error while sending email: Class={}, Message={}", 
                e.getClass().getName(), e.getMessage(), e);
            throw new AppException(ErrorCode.CANNOT_SEND_EMAIL);
        }
    }
}
