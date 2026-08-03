package com.MyProject.chat_service.repository.elasticsearch;

import com.MyProject.chat_service.document.ConversationDoc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConversationElasticRepository extends ElasticsearchRepository<ConversationDoc, String> {
    @Query("""
            {
              "bool": {
                "must": [
                  { "match": { "conversationName": "?1" }}
                ],
                "filter": [
                  { "term": { "userIds": "?0" }},
                  { "term": { "deleted": false }}
                ]
              }
            }
            """)
    Page<ConversationDoc> searchConversations(String userId, String query, Pageable pageable);
}
