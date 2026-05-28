package com.MyProject.debezium_manager.service;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.web.client.ResourceAccessException;
@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DebeziumConnectorService {
    final RestTemplate restTemplate;

    @Value("${app.services.debezium.connect-url:http://localhost:8100}")
    String connectUrl;

    @Value("${spring.kafka.bootstrap-servers:kafka:9092}")
    String kafkaBootstrapServers;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Application is ready. Checking Kafka Connect health...");
        registerAllConnectorsWithRetry();
    }

    @Retryable(
            retryFor = {ResourceAccessException.class, Exception.class},
            maxAttempts = 10,
            backoff = @Backoff(delay = 5000, multiplier = 1.5)
    )
    public void registerAllConnectorsWithRetry() {
        log.info("Attempting to connect to Kafka Connect at {}...", connectUrl);
        
        // Healthcheck Kafka Connect
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(connectUrl + "/", String.class);
            if (response.getStatusCode() != HttpStatus.OK) {
                throw new RuntimeException("Kafka Connect is not healthy: " + response.getStatusCode());
            }
            log.info("Kafka Connect is up and running!");
        } catch (Exception e) {
            log.warn("Kafka Connect is not ready yet at {}. Retrying...", connectUrl);
            throw e;
        }

        log.info("Starting Debezium Connectors registration...");
        
        // 1. MySQL Connector (Identity Service) - server.id = 184054
        registerMySQLConnector("identity-service", "identity-service", "outbox", "184054");

        // 1.1 MySQL Connector (Friend Service) - server.id = 184055 (Phải khác identity-service)
        registerMySQLConnector("friend-service", "friend-service", "outbox", "184055");

        // 2. MongoDB Connector (Chat Service)
        registerMongoConnector("chat-service-connector", "chat-service", "outbox");

        // 3. MongoDB Connector (Notification Service)
        registerMongoConnector("notification-service-connector", "notification-service", "outbox");

        // 3.1 MongoDB Connector (Post Service)
        registerMongoConnector("post-service-connector", "post-service", "outbox");

        // 4. MongoDB Connector (Profile Service)
        registerMongoConnector("profile-service-connector", "profile-service", "outbox");
    }

    private void registerMySQLConnector(String topicPrefix, String dbName, String tableName, String serverId) {
        String connectorName = topicPrefix + "-connector";
        Map<String, Object> config = new HashMap<>();
        config.put("connector.class", "io.debezium.connector.mysql.MySqlConnector");
        config.put("tasks.max", "1");
        config.put("database.hostname", "mysql");
        config.put("database.port", "3306");
        config.put("database.user", "debezium");
        config.put("database.password", "REDACTED_DBZ_CREDENTIAL");
        config.put("database.server.id", serverId);
        config.put("topic.prefix", topicPrefix);
        config.put("database.include.list", dbName);
        config.put("table.include.list", dbName + "." + tableName);
        config.put("schema.history.internal.kafka.bootstrap.servers", kafkaBootstrapServers);
        config.put("schema.history.internal.kafka.topic", "schema-changes." + topicPrefix);
        
        // Outbox SMT for MySQL
        config.put("transforms", "outbox");
        config.put("transforms.outbox.type", "io.debezium.transforms.outbox.EventRouter");
        config.put("transforms.outbox.route.topic.replacement", "${routedByValue}");
        config.put("transforms.outbox.route.by.field", "topic");
        config.put("transforms.outbox.table.field.event.payload", "payload");
        config.put("transforms.outbox.table.field.event.id", "id");
        config.put("transforms.outbox.table.expand.json.payload", "true");

        // Cấu hình chính xác để khớp với các cột trong bảng outbox của bạn
        config.put("transforms.outbox.table.field.event.key", "id");   // Ánh xạ aggregateid vào cột id
        config.put("transforms.outbox.table.field.event.type", "topic"); // Ánh xạ aggregatetype vào cột topic
        
        config.put("value.converter", "org.apache.kafka.connect.json.JsonConverter");
        config.put("value.converter.schemas.enable", "false");

        register(connectorName, config);
    }

    private void registerMongoConnector(String connectorName, String dbName, String collectionName) {
        Map<String, Object> config = new HashMap<>();
        config.put("connector.class", "io.debezium.connector.mongodb.MongoDbConnector");
        config.put("tasks.max", "1");
        config.put("mongodb.connection.string",
                "mongodb://debezium:REDACTED_DBZ_CREDENTIAL@mongodb:27017/?authSource=admin&replicaSet=rs0");
        config.put("topic.prefix", dbName);
        config.put("collection.include.list", dbName + "." + collectionName);

        config.put("transforms", "outbox");
        config.put("transforms.outbox.type",
                "io.debezium.connector.mongodb.transforms.outbox.MongoEventRouter");

        // ✅ Route config (không cần prefix "collection.")
        config.put("transforms.outbox.route.by.field", "topic");
        config.put("transforms.outbox.route.topic.replacement", "${routedByValue}");

        // ✅ Field config phải có prefix "collection."
        config.put("transforms.outbox.collection.field.event.id", "_id");
        config.put("transforms.outbox.collection.field.event.key", "aggregateId");
        config.put("transforms.outbox.collection.field.event.payload", "payload");
        config.put("transforms.outbox.collection.expand.json.payload", "true");

        // ✅ Converter cần thiết khi dùng expand.json.payload
        config.put("value.converter", "org.apache.kafka.connect.storage.StringConverter");

        register(connectorName, config);
    }

    private void register(String name, Map<String, Object> config) {
        String checkUrl = connectUrl + "/connectors/" + name;
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(checkUrl, String.class);
            if (response.getStatusCode() == HttpStatus.OK) {
                log.info("Connector '{}' already exists. Updating configuration...", name);
                restTemplate.put(checkUrl + "/config", config);
                return;
            }
        } catch (Exception e) {
            log.info("Connector '{}' not found, registering...", name);
        }

        try {
            Map<String, Object> request = new HashMap<>();
            request.put("name", name);
            request.put("config", config);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            restTemplate.postForEntity(connectUrl + "/connectors", entity, String.class);
            log.info("Successfully registered Debezium connector: {}", name);
        } catch (Exception e) {
            log.error("Failed to register Debezium connector '{}' at {}", name, connectUrl, e);
        }
    }
}
