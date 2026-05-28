package com.MyProject.chat_service.repository.elasticsearch;

import com.MyProject.chat_service.document.ConversationDoc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConversationElasticRepository extends ElasticsearchRepository<ConversationDoc, String> {
    Page<ConversationDoc> findByUserIdsContainingAndGroupNameContaining(String userId, String groupName, Pageable pageable);
}
