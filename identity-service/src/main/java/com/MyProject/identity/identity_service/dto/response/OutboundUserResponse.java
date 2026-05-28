package com.MyProject.identity.identity_service.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class OutboundUserResponse {
    String id;
    String email;
    boolean verifiedEmail;
    String givenName;
    String familyName;
    String picture;
    String locale;
}
