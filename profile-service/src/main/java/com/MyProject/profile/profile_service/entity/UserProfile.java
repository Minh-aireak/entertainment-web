package com.MyProject.profile.profile_service.entity;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Node("user_profile")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserProfile {
    @Id
    @Property("userId")
    String userId;
    String username;
    String email;
    String displayName;
    String firstName;
    String lastName;
    LocalDate dob;
    String phoneNumber;
    String city;
    LocalDateTime joinDate;
    String avatar;
}
