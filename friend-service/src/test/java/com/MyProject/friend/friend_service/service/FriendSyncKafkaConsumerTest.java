package com.MyProject.friend.friend_service.service;

import com.MyProject.common.redis.RedisService;
import com.MyProject.friend.friend_service.document.FriendDoc;
import com.MyProject.friend.friend_service.dto.event.ProfileSearchUpdatedEvent;
import com.MyProject.friend.friend_service.repository.elasticsearch.FriendElasticRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FriendSyncKafkaConsumerTest {

    @Mock FriendElasticRepository friendElasticRepository;
    @Mock RedisService redisService;
    @Mock Acknowledgment acknowledgment;

    FriendSyncKafkaConsumer consumer;
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        consumer = new FriendSyncKafkaConsumer(friendElasticRepository, objectMapper, redisService);
    }

    // ---------- listenFriendSync ----------

    @Test
    void listenFriendSync_newEvent_savesToElasticsearchAndMarksProcessed() throws Exception {
        FriendDoc doc = FriendDoc.builder().id("agg-1").userId("user-1").friendId("user-2").build();
        String payload = objectMapper.writeValueAsString(doc);
        when(redisService.getAsString("friend:event:processed:sync:agg-1")).thenReturn(null);

        consumer.listenFriendSync(payload, acknowledgment);

        verify(friendElasticRepository).save(any(FriendDoc.class));
        verify(redisService).setWithExpiration(eq("friend:event:processed:sync:agg-1"), eq("1"), eq(7L), any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void listenFriendSync_alreadyProcessed_skipsSaveButAcknowledges() throws Exception {
        FriendDoc doc = FriendDoc.builder().id("agg-1").userId("user-1").friendId("user-2").build();
        String payload = objectMapper.writeValueAsString(doc);
        when(redisService.getAsString("friend:event:processed:sync:agg-1")).thenReturn("1");

        consumer.listenFriendSync(payload, acknowledgment);

        verify(friendElasticRepository, never()).save(any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void listenFriendSync_malformedPayload_throwsAndDoesNotAcknowledge() {
        assertThatThrownBy(() -> consumer.listenFriendSync("not-json", acknowledgment))
                .isInstanceOf(RuntimeException.class);

        verify(acknowledgment, never()).acknowledge();
        verifyNoInteractions(friendElasticRepository);
    }

    // ---------- listenSearchSync ----------

    @Test
    void listenSearchSync_newEvent_updatesMatchingDocsAndMarksProcessed() throws Exception {
        ProfileSearchUpdatedEvent event = ProfileSearchUpdatedEvent.builder()
                .eventId("evt-1").userId("user-1").displayName("New Name").build();
        String payload = objectMapper.writeValueAsString(event);
        when(redisService.getAsString("friend:event:processed:search:evt-1")).thenReturn(null);
        FriendDoc doc1 = FriendDoc.builder().id("d1").friendId("user-1").friendDisplayName("Old Name").build();
        when(friendElasticRepository.findByFriendId("user-1")).thenReturn(List.of(doc1));

        consumer.listenSearchSync(payload, acknowledgment);

        verify(friendElasticRepository).saveAll(List.of(doc1));
        assert doc1.getFriendDisplayName().equals("New Name");
        verify(acknowledgment).acknowledge();
    }

    @Test
    void listenSearchSync_noMatchingDocs_skipsSaveAllButAcknowledges() throws Exception {
        ProfileSearchUpdatedEvent event = ProfileSearchUpdatedEvent.builder()
                .eventId("evt-1").userId("user-1").displayName("New Name").build();
        String payload = objectMapper.writeValueAsString(event);
        when(redisService.getAsString("friend:event:processed:search:evt-1")).thenReturn(null);
        when(friendElasticRepository.findByFriendId("user-1")).thenReturn(List.of());

        consumer.listenSearchSync(payload, acknowledgment);

        verify(friendElasticRepository, never()).saveAll(any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void listenSearchSync_alreadyProcessed_skipsLookup() throws Exception {
        ProfileSearchUpdatedEvent event = ProfileSearchUpdatedEvent.builder()
                .eventId("evt-1").userId("user-1").displayName("New Name").build();
        String payload = objectMapper.writeValueAsString(event);
        when(redisService.getAsString("friend:event:processed:search:evt-1")).thenReturn("1");

        consumer.listenSearchSync(payload, acknowledgment);

        verify(friendElasticRepository, never()).findByFriendId(anyString());
        verify(acknowledgment).acknowledge();
    }
}
