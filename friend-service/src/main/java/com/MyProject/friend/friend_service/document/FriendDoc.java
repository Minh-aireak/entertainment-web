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

    // Chỉ dùng để search/match theo tên - KHÔNG dùng để hiển thị avatar/tên trên response (xem
    // FriendService.searchFriends): trước đây có field friendAvatar lưu snapshot presigned URL từ
    // profile-service, hết hạn sau ~1h và không bao giờ được refresh (listenSearchSync từng là
    // no-op) - đã bỏ field đó, avatar giờ luôn lấy live từ profile-service tại thời điểm trả response.
    @Field(type = FieldType.Text, analyzer = "standard")
    String friendDisplayName;
}
