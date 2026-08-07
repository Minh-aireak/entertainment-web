package com.MyProject.friend.friend_service.repository.elasticsearch;

import com.MyProject.friend.friend_service.document.FriendDoc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FriendElasticRepository extends ElasticsearchRepository<FriendDoc, String> {
    Page<FriendDoc> findByUserIdAndFriendDisplayNameContaining(String userId, String friendDisplayName, Pageable pageable);

    // Mỗi lần A-B kết bạn tạo 2 FriendDoc (1 cho mỗi chiều xem như "bạn của X") - khi X đổi tên,
    // phải update lại friendDisplayName ở TẤT CẢ doc có friendId = X (xem FriendSyncKafkaConsumer).
    List<FriendDoc> findByFriendId(String friendId);
}
