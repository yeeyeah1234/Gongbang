package com.gongbang.notification.service;

public record CommunityPostCreatedEvent(Long authorId, Long postId, String title) {}
