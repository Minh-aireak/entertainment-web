package com.MyProject.friend_service.dto.response;

import com.MyProject.friend_service.entity.RelationshipStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FriendResponse {
    String userId;
    String displayName;
    String avatar;
    String hash;
    Instant date;
    RelationshipStatus status;
}
