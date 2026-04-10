package com.campus.market.service;

import com.campus.market.dto.ChatReadRequest;
import com.campus.market.dto.ChatSendRequest;
import com.campus.market.entity.ChatMessageEntity;
import com.campus.market.entity.ChatSession;

import java.util.List;
import java.util.Map;

public interface ChatService {
    ChatMessageEntity sendMessage(ChatSendRequest request);

    Map<String, Object> pageMessages(Long userId, Long otherUserId, int page, int size);

    List<ChatSession> listSessions(Long userId);

    Map<String, Object> markRead(ChatReadRequest request);

    Map<String, Object> unreadSummary(Long userId);
}

