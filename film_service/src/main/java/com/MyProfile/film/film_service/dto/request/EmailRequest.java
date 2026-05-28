package com.MyProfile.film.film_service.dto.request;

import com.MyProject.identity.identity_service.dto.request.Recipient;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class EmailRequest {
    List<Recipient> to;
    String subject;
    String htmlContent;
}
