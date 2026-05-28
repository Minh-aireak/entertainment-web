package com.MyProject.notification.notification_service.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TypeNotification {
    NEW_CHAT(
            "New message",
            "{sender} sent you a message"
    ),

    FRIEND_REQUEST(
            "Friend request",
            "{sender} sent you a friend request"
    ),

    FRIEND_ACCEPTED(
            "Friend accepted",
            "{sender} accepted your friend request"
    ),

    STATUS_CHANGE(
            "Status update",
            "{sender} updated status"
    ),

    POST_COMMENT(
            "Post comment",
            "{sender} commented on your post"
    ),

    SOCIAL_LIKE(
            "Post liked",
            "{sender} liked your post"
    ),

    SOCIAL_COMMENT(
            "Post comment",
            "{sender} commented on your post"
    ),

    SOCIAL_SHARE(
            "Post shared",
            "{sender} shared your post"
    ),

    FILM_LIKE(
            "Film liked",
            "{sender} liked your film review"
    ),

    FILM_COMMENT(
            "Film comment",
            "{sender} commented on your film review"
    ),

    FILM_SHARE(
            "Film shared",
            "{sender} shared your film review"
    );

    private final String title;

    private final String content;
}
