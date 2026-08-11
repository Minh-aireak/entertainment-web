package com.MyProject.socket_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
		"app.services.chat.url=http://localhost:0",
		"app.services.profile.url=http://localhost:0",
		"app.services.identity.url=http://localhost:0",
		"spring.data.mongodb.uri=mongodb://localhost:27017/socket-service-test",
		"spring.data.redis.host=localhost",
		"spring.data.redis.port=6379",
		"spring.data.redis.password=test-only",
		"spring.kafka.bootstrap-servers=localhost:9092",
		"spring.kafka.listener.auto-startup=false",
		"spring.task.scheduling.enabled=false",
		"jwt.signerKey=test-only-signer-key-not-for-production-use-0000"
})
class SocketServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
