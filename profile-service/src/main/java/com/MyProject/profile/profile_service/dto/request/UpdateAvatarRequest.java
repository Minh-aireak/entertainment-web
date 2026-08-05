package com.MyProject.profile.profile_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class UpdateAvatarRequest {
    @NotBlank(message = "AVATAR_NOT_BLANK")
    String avatar;
}
