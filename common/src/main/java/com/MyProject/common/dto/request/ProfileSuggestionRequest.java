package com.MyProject.common.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Collections;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProfileSuggestionRequest {
    @Builder.Default
    Set<String> excludedUserIds = Collections.emptySet();

    int page;
    int size;
}
