package com.MyProject.socket_service.service;

import com.MyProject.common.redis.RedisService;
import com.MyProject.socket_service.dto.event.NotificationSocket;
import com.MyProject.socket_service.dto.response.NotificationUI;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SocketKafkaServiceTest {
    private SocketKafkaService createService(CustomWebSocketHandler webSocketHandler, ObjectMapper objectMapper) {
        return new SocketKafkaService(
                webSocketHandler,
                mock(SocketDownstreamService.class),
                mock(RedisService.class),
                objectMapper
        );
    }

    @Test
    void consumeNotification_sendsRealtimeEventToEveryRecipient() throws Exception {
        CustomWebSocketHandler webSocketHandler = mock(CustomWebSocketHandler.class);
        SocketDownstreamService downstreamService = mock(SocketDownstreamService.class);
        RedisService redisService = mock(RedisService.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        ObjectMapper objectMapper = new ObjectMapper();
        SocketKafkaService service = new SocketKafkaService(
                webSocketHandler,
                downstreamService,
                redisService,
                objectMapper
        );
        String payload = objectMapper.writeValueAsString(new NotificationSocket(
                "Sender",
                "avatar.png",
                "FRIEND_REQUEST",
                "Friend request",
                "Sender sent you a friend request",
                List.of("receiver-one", "receiver-two")
        ));

        service.consumeNotification(payload, acknowledgment);

        verify(webSocketHandler).sendToUser(
                eq("receiver-one"),
                eq("notification"),
                argThat(data -> data instanceof NotificationUI notification
                        && "Sender".equals(notification.getDisplayNameSender())
                        && "FRIEND_REQUEST".equals(notification.getType())
                        && "Sender sent you a friend request".equals(notification.getContent()))
        );
        verify(webSocketHandler).sendToUser(
                eq("receiver-two"),
                eq("notification"),
                any(NotificationUI.class)
        );
        verify(acknowledgment).acknowledge();
    }

    @Test
    void consumeCommentReaction_broadcastsCountsToPostRoom() {
        CustomWebSocketHandler webSocketHandler = mock(CustomWebSocketHandler.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        SocketKafkaService service = createService(webSocketHandler, new ObjectMapper());
        String payload = "{\"commentId\":\"c-1\",\"sourceId\":\"post-1\",\"actorUserId\":\"user-1\",\"likeCount\":3,\"loveCount\":1}";

        service.consumeCommentReaction(payload, acknowledgment);

        verify(webSocketHandler).sendToRoom(eq("post-1"), eq("comment:reaction"), argThat(event ->
                event instanceof com.MyProject.socket_service.dto.event.CommentReactionChangedEvent reaction
                        && reaction.getLikeCount() == 3));
        verify(acknowledgment).acknowledge();
    }
}
