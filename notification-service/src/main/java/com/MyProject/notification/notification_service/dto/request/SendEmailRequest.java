package com.MyProject.notification.notification_service.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SendEmailRequest {
    List<Recipient> to;
    String subject;
    String htmlContent;
}
