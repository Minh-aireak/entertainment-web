package com.MyProject.common.elasticsearch;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.support.HttpHeaders;

@Configuration
@ConditionalOnClass(ElasticsearchOperations.class)
public class ElasticsearchConfig extends ElasticsearchConfiguration {

    @Value("${spring.elasticsearch.uris:localhost:9200}")
    private String elasticsearchUris;

    @Override
    public ClientConfiguration clientConfiguration() {
        // Remove http:// or https:// if present because connectedTo() expects host:port
        String cleanUris = elasticsearchUris.replace("http://", "").replace("https://", "");
        
        HttpHeaders headers = new HttpHeaders();
        headers.add("Accept", "application/vnd.elasticsearch+json;compatible-with=8");
        headers.add("Content-Type", "application/vnd.elasticsearch+json;compatible-with=8");

        return ClientConfiguration.builder()
                .connectedTo(cleanUris)
                .withDefaultHeaders(headers)
                .build();
    }
}
