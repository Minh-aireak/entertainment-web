package com.MyProject.friend.friend_service.repository.elasticsearch;

import com.MyProject.friend.friend_service.document.FriendDoc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FriendElasticRepository extends ElasticsearchRepository<FriendDoc, String> {
    Page<FriendDoc> findByUserIdAndFriendDisplayNameContaining(String userId, String friendDisplayName, Pageable pageable);
}
