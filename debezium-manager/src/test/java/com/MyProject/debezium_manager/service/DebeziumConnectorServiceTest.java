package com.MyProject.debezium_manager.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class DebeziumConnectorServiceTest {
    private static final String CONNECT_URL = "http://connect:8083";
    private static final String KAFKA_BOOTSTRAP_SERVERS = "kafka:9092";

    @Mock
    private RestTemplate restTemplate;

    private DebeziumConnectorService service;

    @BeforeEach
    void setUp() {
        service = new DebeziumConnectorService(restTemplate);
        ReflectionTestUtils.setField(service, "connectUrl", CONNECT_URL);
        ReflectionTestUtils.setField(service, "kafkaBootstrapServers", KAFKA_BOOTSTRAP_SERVERS);
        ReflectionTestUtils.setField(service, "databaseUsername", "debezium-test");
        ReflectionTestUtils.setField(service, "databasePassword", "test-password");
    }

    @Test
    void isReady_isFalseBeforeRegistration() {
        assertFalse(service.isReady());
    }

    @Test
    void registerAllConnectorsWithRetry_registersMissingConnectorsWithExpectedConfiguration() {
        when(restTemplate.getForEntity(CONNECT_URL + "/", String.class))
                .thenReturn(ResponseEntity.ok("{}"));
        when(restTemplate.getForEntity(startsWith(CONNECT_URL + "/connectors/"), eq(String.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

        service.registerAllConnectorsWithRetry();

        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate, times(10))
                .postForEntity(eq(CONNECT_URL + "/connectors"), entityCaptor.capture(), eq(String.class));
        verify(restTemplate, never()).put(anyString(), any());

        Map<String, Map<String, Object>> configs = capturedConfigs(entityCaptor);
        assertEquals(
                Set.of(
                        "identity-service-connector",
                        "film-service-connector",
                        "friend-service-connector",
                        "chat-service-connector",
                        "notification-service-connector",
                        "post-service-connector",
                        "profile-service-connector",
                        "socket-service-connector",
                        "comment-service-connector",
                        "room-service-connector"),
                configs.keySet());

        Map<String, Object> identity = configs.get("identity-service-connector");
        assertEquals("io.debezium.connector.mysql.MySqlConnector", identity.get("connector.class"));
        assertEquals("184054", identity.get("database.server.id"));
        assertEquals("identity-service", identity.get("database.include.list"));
        assertEquals("identity-service.outbox", identity.get("table.include.list"));
        assertEquals("debezium-test", identity.get("database.user"));
        assertEquals("test-password", identity.get("database.password"));
        assertEquals(KAFKA_BOOTSTRAP_SERVERS, identity.get("schema.history.internal.kafka.bootstrap.servers"));
        assertEquals("${routedByValue}", identity.get("transforms.outbox.route.topic.replacement"));

        Map<String, Object> film = configs.get("film-service-connector");
        assertEquals("184055", film.get("database.server.id"));
        assertEquals("film-service.outbox", film.get("table.include.list"));

        Map<String, Object> friend = configs.get("friend-service-connector");
        assertEquals("io.debezium.connector.mongodb.MongoDbConnector", friend.get("connector.class"));
        assertEquals("friend-service.outbox", friend.get("collection.include.list"));
        assertEquals(
                "mongodb://debezium-test:test-password@mongodb:27017/?authSource=admin&replicaSet=rs0",
                friend.get("mongodb.connection.string"));
        assertEquals(
                "io.debezium.connector.mongodb.transforms.outbox.MongoEventRouter",
                friend.get("transforms.outbox.type"));
        assertEquals("org.apache.kafka.connect.storage.StringConverter", friend.get("value.converter"));
        assertTrue(service.isReady());
    }

    @Test
    void registerAllConnectorsWithRetry_updatesExistingConnectorsWithoutCreatingDuplicates() {
        when(restTemplate.getForEntity(CONNECT_URL + "/", String.class))
                .thenReturn(ResponseEntity.ok("{}"));
        when(restTemplate.getForEntity(startsWith(CONNECT_URL + "/connectors/"), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{}"));

        service.registerAllConnectorsWithRetry();

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(restTemplate, times(10)).put(urlCaptor.capture(), any());
        verify(restTemplate, never()).postForEntity(anyString(), any(), eq(String.class));
        assertTrue(urlCaptor.getAllValues().stream().allMatch(url -> url.endsWith("/config")));
        assertEquals(10, Set.copyOf(urlCaptor.getAllValues()).size());
        assertTrue(service.isReady());
    }

    @Test
    void registerAllConnectorsWithRetry_whenKafkaConnectIsUnhealthy_keepsServiceNotReady() {
        when(restTemplate.getForEntity(CONNECT_URL + "/", String.class))
                .thenReturn(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("down"));

        RuntimeException exception =
                assertThrows(RuntimeException.class, service::registerAllConnectorsWithRetry);

        assertTrue(exception.getMessage().contains("Kafka Connect is not healthy"));
        assertFalse(service.isReady());
        verify(restTemplate, never()).postForEntity(anyString(), any(), eq(String.class));
        verify(restTemplate, never()).put(anyString(), any());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> capturedConfigs(
            ArgumentCaptor<HttpEntity> entityCaptor) {
        Map<String, Map<String, Object>> configs = new HashMap<>();
        for (HttpEntity<?> entity : entityCaptor.getAllValues()) {
            assertEquals(MediaType.APPLICATION_JSON, entity.getHeaders().getContentType());
            Map<String, Object> body = (Map<String, Object>) entity.getBody();
            configs.put((String) body.get("name"), (Map<String, Object>) body.get("config"));
        }
        return configs;
    }
}
