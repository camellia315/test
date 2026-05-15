package com.campus.user.service;

import com.campus.user.dto.SystemNotificationCreateRequest;

import java.util.Map;

public interface NotificationService {
    Map<String, Object> pageNotifications(String authorizationHeader, Integer type, Integer isRead, int page, int size);

    Map<String, Object> unreadCount(String authorizationHeader);

    Map<String, Object> markRead(String authorizationHeader, Long notificationId);

    Map<String, Object> markAllRead(String authorizationHeader);

    Map<String, Object> publishSystemNotification(String authorizationHeader, SystemNotificationCreateRequest request);
}
