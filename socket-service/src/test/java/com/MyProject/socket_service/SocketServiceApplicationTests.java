package com.MyProject.socket_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
		"app.services.chat.url=http://localhost:0",
		"app.services.profile.url=http://localhost:0",
		"app.services.identity.url=http://localhost:0",
		"jwt.signerKey=test-only-signer-key-not-for-production-use-0000"
})
class SocketServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
