package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.exception.BusinessException;
import com.campus.market.dto.ChatReadRequest;
import com.campus.market.dto.ChatSendRequest;
import com.campus.market.entity.ChatMessageEntity;
import com.campus.market.entity.ChatSession;
import com.campus.market.mapper.ChatMessageMapper;
import com.campus.market.mapper.ChatSessionMapper;
import com.campus.market.service.ChatService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ChatServiceImpl implements ChatService {

    private static final int MSG_TYPE_TEXT = 1;
    private static final int UNREAD_TRUE = 0;

    private final ChatMessageMapper chatMessageMapper;
    private final ChatSessionMapper chatSessionMapper;

    public ChatServiceImpl(ChatMessageMapper chatMessageMapper, ChatSessionMapper chatSessionMapper) {
        this.chatMessageMapper = chatMessageMapper;
        this.chatSessionMapper = chatSessionMapper;
    }

    @Override
    @Transactional
    public ChatMessageEntity sendMessage(ChatSendRequest request) {
        validateSendRequest(request);
        ChatMessageEntity message = new ChatMessageEntity();
        message.setFromUserId(request.getFromUserId());
        message.setToUserId(request.getToUserId());
        message.setProductId(request.getProductId());
        message.setContent(request.getContent().trim());
        message.setMsgType(request.getMsgType() == null ? MSG_TYPE_TEXT : request.getMsgType());
        message.setIsRead(UNREAD_TRUE);
        message.setCreateTime(LocalDateTime.now());
        chatMessageMapper.insert(message);

        upsertSession(request.getFromUserId(), request.getToUserId(), message.getContent(), false);
        upsertSession(request.getToUserId(), request.getFromUserId(), message.getContent(), true);
        return message;
    }

    @Override
    public Map<String, Object> pageMessages(Long userId, Long otherUserId, int page, int size) {
        if (userId == null || otherUserId == null) {
            throw new BusinessException(400, "userId and otherUserId are required");
        }
        Page<ChatMessageEntity> pageReq = new Page<>(normalizePage(page), normalizeSize(size));
        LambdaQueryWrapper<ChatMessageEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w
                .and(i -> i.eq(ChatMessageEntity::getFromUserId, userId).eq(ChatMessageEntity::getToUserId, otherUserId))
                .or(i -> i.eq(ChatMessageEntity::getFromUserId, otherUserId).eq(ChatMessageEntity::getToUserId, userId))
        );
        wrapper.orderByDesc(ChatMessageEntity::getCreateTime);
        Page<ChatMessageEntity> result = chatMessageMapper.selectPage(pageReq, wrapper);
        return toPageData(result);
    }

    @Override
    public List<ChatSession> listSessions(Long userId) {
        if (userId == null) {
            throw new BusinessException(400, "userId is required");
        }
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getUserId, userId);
        wrapper.orderByDesc(ChatSession::getUpdateTime);
        return chatSessionMapper.selectList(wrapper);
    }

    @Override
    @Transactional
    public Map<String, Object> markRead(ChatReadRequest request) {
        if (request == null || request.getUserId() == null || request.getOtherUserId() == null) {
            throw new BusinessException(400, "userId and otherUserId are required");
        }
        int updated = chatMessageMapper.markAsRead(request.getUserId(), request.getOtherUserId());
        chatSessionMapper.resetUnreadCount(request.getUserId(), request.getOtherUserId());
        Map<String, Object> result = new HashMap<>();
        result.put("updated", updated);
        return result;
    }

    @Override
    public Map<String, Object> unreadSummary(Long userId) {
        if (userId == null) {
            throw new BusinessException(400, "userId is required");
        }
        List<ChatSession> sessions = listSessions(userId);
        int unreadTotal = sessions.stream()
                .map(ChatSession::getUnreadCount)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("unreadTotal", unreadTotal);
        result.put("sessions", sessions);
        return result;
    }

    private void validateSendRequest(ChatSendRequest request) {
        if (request == null || request.getFromUserId() == null || request.getToUserId() == null) {
            throw new BusinessException(400, "fromUserId and toUserId are required");
        }
        if (Objects.equals(request.getFromUserId(), request.getToUserId())) {
            throw new BusinessException(409, "cannot chat with yourself");
        }
        if (!StringUtils.hasText(request.getContent())) {
            throw new BusinessException(400, "content is required");
        }
    }

    private void upsertSession(Long userId, Long otherUserId, String lastMessage, boolean increaseUnread) {
        ChatSession session = chatSessionMapper.selectByUserAndOther(userId, otherUserId);
        if (session == null) {
            session = new ChatSession();
            session.setUserId(userId);
            session.setOtherUserId(otherUserId);
            session.setLastMessage(lastMessage);
            session.setUnreadCount(increaseUnread ? 1 : 0);
            session.setUpdateTime(LocalDateTime.now());
            chatSessionMapper.insert(session);
            return;
        }
        session.setLastMessage(lastMessage);
        session.setUnreadCount(increaseUnread
                ? (session.getUnreadCount() == null ? 1 : session.getUnreadCount() + 1)
                : 0);
        session.setUpdateTime(LocalDateTime.now());
        chatSessionMapper.updateById(session);
    }

    private int normalizePage(int page) {
        return page <= 0 ? 1 : page;
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, 200);
    }

    private Map<String, Object> toPageData(Page<?> result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("records", result.getRecords());
        response.put("total", result.getTotal());
        response.put("pages", result.getPages());
        response.put("current", result.getCurrent());
        response.put("size", result.getSize());
        return response;
    }
}

