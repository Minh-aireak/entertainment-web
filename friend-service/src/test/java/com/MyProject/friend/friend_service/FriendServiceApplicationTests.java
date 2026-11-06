package com.MyProject.friend.friend_service;

import com.MyProject.friend.friend_service.repository.elasticsearch.FriendElasticRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// application.yaml reads these from environment variables that only Docker's .env provides.
// Without them the Feign client URL stays "${PROFILES_SERVICE_URL}" and the context fails to load.
// The Elasticsearch repository is mocked (as in FilmServiceApplicationTests) because the real one
// creates its index at startup and needs a running Elasticsearch.
@SpringBootTest
@TestPropertySource(properties = {
		"app.services.profile.url=http://localhost:0",
		"spring.data.mongodb.uri=mongodb://localhost:27017/friend-service-test",
		"spring.data.redis.host=localhost",
		"spring.data.redis.port=6379",
		"spring.data.redis.password=test-only",
		"spring.kafka.bootstrap-servers=localhost:9092",
		"spring.kafka.listener.auto-startup=false",
		"spring.elasticsearch.uris=http://localhost:9200",
		"jwt.signerKey=test-only-signer-key-not-for-production-use-0000"
})
class FriendServiceApplicationTests {

	@MockitoBean(name = "friendElasticRepository")
	private FriendElasticRepository friendElasticRepository;

	@Test
	void contextLoads() {
	}

}
