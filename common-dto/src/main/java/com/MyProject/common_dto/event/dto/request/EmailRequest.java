package com.MyProject.common_dto.event.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class EmailRequest {
    String channel;
    String recipient;
    String templateCode;
    Map<String, Object> params;
    String subject;
    String body;
}
