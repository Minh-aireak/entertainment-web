package com.MyProject.friend.friend_service.document;

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
@Document(indexName = "friend")
public class FriendDoc {
    @Id
    String id;

    @Field(type = FieldType.Keyword)
    String userId;

    @Field(type = FieldType.Keyword)
    String friendId;

    @Field(type = FieldType.Text, analyzer = "standard")
    String friendDisplayName;

    @Field(type = FieldType.Keyword, index = false)
    String friendAvatar;
}
