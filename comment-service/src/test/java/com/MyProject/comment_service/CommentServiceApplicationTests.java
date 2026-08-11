package com.MyProject.comment_service;

import com.MyProject.comment_service.repository.CommentRepository;
import com.MyProject.comment_service.repository.OutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class CommentServiceApplicationTests {

	@MockitoBean
	private RedisConnectionFactory redisConnectionFactory;

	@MockitoBean
	private MongoTemplate mongoTemplate;

	@MockitoBean(name = "mongoMappingContext")
	private MongoMappingContext mongoMappingContext;

	@MockitoBean
	private CommentRepository commentRepository;

	@MockitoBean
	private OutboxRepository outboxRepository;

	@Test
	void contextLoads() {
	}

}
