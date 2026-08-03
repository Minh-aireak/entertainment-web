package com.MyProject.film.film_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ActorRequest {
    @NotBlank(message = "NAME_REQUIRED")
    String name;
    String avatarUrl;
}
