package com.MyProject.chat_service.repository.elasticsearch;

import com.MyProject.chat_service.document.ChatMessageDoc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatMessageElasticRepository extends ElasticsearchRepository<ChatMessageDoc, String> {
    @Query("""
            {
              "bool": {
                "must": [
                  { "term": { "conversationId": "?0" }},
                  { "match": { "content": "?1" }}
                ],
                "filter": [
                  { "term": { "deleted": false }}
                ]
              }
            }
            """)
    Page<ChatMessageDoc> searchMessages(
            String conversationId,
            String content,
            Pageable pageable
    );
}
