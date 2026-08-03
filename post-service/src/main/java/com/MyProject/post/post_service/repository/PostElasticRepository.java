package com.MyProject.post.post_service.repository;

import com.MyProject.post.post_service.document.PostDoc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PostElasticRepository extends ElasticsearchRepository<PostDoc, String> {
    
    @Query("{\"bool\": {\"should\": [" +
            "{\"match\": {\"title\": {\"query\": \"?0\", \"fuzziness\": \"AUTO\"}}}," +
            "{\"match\": {\"content\": {\"query\": \"?0\", \"fuzziness\": \"AUTO\"}}}" +
            "]}}")
    Page<PostDoc> searchByTitleOrContent(String query, Pageable pageable);
}
