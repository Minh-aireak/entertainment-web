package com.MyProject.profile.profile_service.entity;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

@Document("outbox")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Outbox {
    @MongoId
    String id;

    @Field("aggregateId")
    String aggregateId;

    String topic;

    String payload; 

    @CreatedDate
    @Field("createdDate")
    LocalDateTime createdDate;
    
    @Builder.Default
    boolean processed = false;
}
