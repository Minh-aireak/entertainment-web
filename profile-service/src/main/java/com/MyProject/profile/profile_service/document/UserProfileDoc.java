package com.MyProject.profile.profile_service.document;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Document(indexName = "profiles")
public class UserProfileDoc {
    @Id
    String userId;

    @Field(type = FieldType.Text, analyzer = "standard")
    String username;

    @Field(type = FieldType.Text, analyzer = "standard")
    String displayName;

    @Field(type = FieldType.Keyword)
    String avatar;
}
