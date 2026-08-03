package com.MyProject.post.post_service.entity;

public record ActionConfig(
        String requiredStatus,
        String newStatus,
        String messageTemplate
) {}
