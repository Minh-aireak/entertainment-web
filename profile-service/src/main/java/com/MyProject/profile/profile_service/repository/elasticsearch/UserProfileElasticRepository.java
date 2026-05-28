package com.MyProject.profile.profile_service.repository.elasticsearch;

import com.MyProject.profile.profile_service.document.UserProfileDoc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserProfileElasticRepository extends ElasticsearchRepository<UserProfileDoc, String> {

    @Query("{\"wildcard\": {\"username\": {\"value\": \"*?0*\", \"case_insensitive\": true}}}")
    Page<UserProfileDoc> searchByUsernameContaining(String query, Pageable pageable);
}
