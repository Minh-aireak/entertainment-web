package com.MyProject.identity.identity_service.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ExchangeTokenResponse {
    String accessToken;
    String expiresIn;
    String refreshToken;
    String scope;
    String tokenType;
}
