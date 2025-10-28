package com.MyProject.notification.notification_service.service;

import com.MyProject.notification.notification_service.dto.request.EmailRequest;
import com.MyProject.notification.notification_service.dto.request.SendEmailRequest;
import com.MyProject.notification.notification_service.dto.request.Sender;
import com.MyProject.notification.notification_service.exception.AppException;
import com.MyProject.notification.notification_service.exception.ErrorCode;
import com.MyProject.notification.notification_service.repository.httpclient.EmailClient;
import feign.FeignException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EmailService {
    EmailClient emailClient;

    public void sendEmail(SendEmailRequest request){
        EmailRequest emailRequest = EmailRequest.builder()
                .sender(Sender.builder()
                        .name("aireak.com")
                        .email("tuanminh18122005@gmail.com")
                        .build())
                .to(request.getTo())
                .subject(request.getSubject())
                .htmlContent(request.getHtmlContent())
                .build();
        try {
            String apiKey = "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";
            emailClient.sendEmail(apiKey, emailRequest);
        } catch (FeignException exception){
            throw new AppException(ErrorCode.CANNOT_SEND_EMAIL);
        }
    }
}
