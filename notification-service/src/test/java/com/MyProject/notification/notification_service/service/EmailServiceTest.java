package com.MyProject.notification.notification_service.service;

import com.MyProject.notification.notification_service.dto.request.EmailRequest;
import com.MyProject.notification.notification_service.dto.request.Recipient;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    EmailExternalService emailExternalService;

    EmailService emailService;

    final String mockApiKey = "test-api-key";

    @BeforeEach
    void setUp() {
        emailService = new EmailService(emailExternalService);
        ReflectionTestUtils.setField(emailService, "apiKey", mockApiKey);
        ReflectionTestUtils.setField(emailService, "senderName", "AIREAK");
        ReflectionTestUtils.setField(emailService, "senderEmail", "no-reply@aireak.test");
    }

    private EmailRequest request(String toEmail) {
        return EmailRequest.builder()
                .to(List.of(Recipient.builder().email(toEmail).build()))
                .subject("Welcome")
                .htmlContent("<p>Hello</p>")
                .build();
    }

    @Test
    void sendEmail_happyPath_buildsRequestWithSenderAndDelegatesToExternalService() {
        emailService.sendEmail(request("user@gmail.com"));

        verify(emailExternalService).sendEmail(eq(mockApiKey), argThat(req ->
                req.getSender().getName().equals("AIREAK")
                        && req.getSender().getEmail().equals("no-reply@aireak.test")
                        && req.getTo().get(0).getEmail().equals("user@gmail.com")
                        && req.getSubject().equals("Welcome")));
    }

    @Test
    void sendEmail_externalServiceThrowsFeignException_isSwallowedAndLoggedRatherThanPropagated() {
        FeignException feignException = mock(FeignException.class);
        Request feignRequest = Request.create(Request.HttpMethod.POST, "https://brevo/send",
                java.util.Map.of(), null, java.nio.charset.StandardCharsets.UTF_8, null);
        when(feignException.status()).thenReturn(500);
        when(feignException.contentUTF8()).thenReturn("{\"error\":\"down\"}");
        when(feignException.request()).thenReturn(feignRequest);
        doThrow(feignException).when(emailExternalService).sendEmail(eq(mockApiKey), any());

        assertThatCode(() -> emailService.sendEmail(request("user@gmail.com"))).doesNotThrowAnyException();
    }

    @Test
    void sendEmail_externalServiceThrowsNonFeignException_propagatesInsteadOfBeingSwallowed() {
        doThrow(new RuntimeException("unexpected failure")).when(emailExternalService).sendEmail(eq(mockApiKey), any());

        assertThatThrownBy(() -> emailService.sendEmail(request("user@gmail.com")))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("unexpected failure");
    }
}
