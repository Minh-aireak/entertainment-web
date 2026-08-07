package com.MyProject.notification.notification_service.service;

import com.MyProject.common.dto.response.UserProfileResponse;
import com.MyProject.common.redis.RedisService;
import com.MyProject.notification.notification_service.dto.event.NotificationEvent;
import com.MyProject.notification.notification_service.dto.event.NotificationSocket;
import com.MyProject.notification.notification_service.entity.Notification;
import com.MyProject.notification.notification_service.entity.TypeNotification;
import com.MyProject.notification.notification_service.mapper.NotificationMapper;
import com.MyProject.notification.notification_service.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

/**
 * Covers the NEW_EPISODE notification message bug: the {film} placeholder in
 * TypeNotification content must be substituted with the actual film title
 * that flows in on the NotificationEvent from film_service.
 */
class NotificationServiceTest {

    NotificationRepository notificationRepository;
    NotificationMapper notificationMapper;
    OutboxEventPublisher outboxEventPublisher;
    NotificationProfileService notificationProfileService;
    RedisService redisService;
    NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationRepository = mock(NotificationRepository.class);
        notificationMapper = mock(NotificationMapper.class);
        outboxEventPublisher = mock(OutboxEventPublisher.class);
        notificationProfileService = mock(NotificationProfileService.class);
        redisService = mock(RedisService.class);

        notificationService = new NotificationService(
                notificationRepository,
                notificationMapper,
                outboxEventPublisher,
                notificationProfileService,
                redisService);

        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(notificationProfileService.getProfile(anyString())).thenReturn(
                UserProfileResponse.builder().userId("sender-1").displayName("Sender").build());
    }

    private NotificationEvent newEpisodeEvent(String filmTitle) {
        return NotificationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .typeNotification(TypeNotification.NEW_EPISODE.name())
                .userIdSender("sender-1")
                .toUserIds(List.of("follower-1"))
                .filmTitle(filmTitle)
                .build();
    }

    private String publishedContent() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(outboxEventPublisher).publish(any(), anyString(), captor.capture());
        return ((NotificationSocket) captor.getValue()).content();
    }

    @Test
    void createNotification_newEpisode_includesActualFilmTitle() {
        notificationService.createNotification(newEpisodeEvent("Attack on Titan"));

        assertEquals("The film \"Attack on Titan\" has a new episode", publishedContent());
    }

    @Test
    void createNotification_newEpisode_persistsFilmTitleOnNotification() {
        notificationService.createNotification(newEpisodeEvent("Attack on Titan"));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertEquals("Attack on Titan", captor.getValue().getFilmTitle());
    }

    @Test
    void createNotification_newEpisode_handlesSpecialCharactersInFilmTitle() {
        String filmTitle = "Bố Già: Phần 2 (Extended) — \"Director's Cut\" & More!";

        notificationService.createNotification(newEpisodeEvent(filmTitle));

        assertEquals("The film \"" + filmTitle + "\" has a new episode", publishedContent());
    }

    @Test
    void createNotification_newEpisode_handlesLongFilmTitle() {
        String filmTitle = "A".repeat(300);

        notificationService.createNotification(newEpisodeEvent(filmTitle));

        String content = publishedContent();
        assertTrue(content.contains(filmTitle), "Long film title must not be truncated in the message");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void createNotification_newEpisode_blankFilmTitle_fallsBackWithoutPlaceholderLeak(String blankTitle) {
        notificationService.createNotification(newEpisodeEvent(blankTitle));

        String content = publishedContent();
        assertFalse(content.contains("{film}"), "Raw {film} placeholder must never leak into the message");
        assertFalse(content.toLowerCase().contains("undefined"));
        assertFalse(content.contains("\"\""), "Must not render an empty quoted film name");
    }

    @Test
    void createNotification_friendRequest_isUnaffectedByFilmPlaceholderLogic() {
        NotificationEvent event = NotificationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .typeNotification(TypeNotification.FRIEND_REQUEST.name())
                .userIdSender("sender-1")
                .toUserIds(List.of("follower-1"))
                .build();

        notificationService.createNotification(event);

        assertEquals("Sender sent you a friend request", publishedContent());
    }
}
