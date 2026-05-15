package com.campus.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.common.exception.BusinessException;
import com.campus.user.dto.SystemNotificationCreateRequest;
import com.campus.user.entity.MessageNotification;
import com.campus.user.entity.UserEntity;
import com.campus.user.mapper.MessageNotificationMapper;
import com.campus.user.mapper.UserMapper;
import com.campus.user.service.AuthService;
import com.campus.user.service.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class NotificationServiceImpl implements NotificationService {

    private static final int TYPE_SYSTEM = 1;
    private static final int READ_YES = 1;
    private static final int READ_NO = 0;

    private final MessageNotificationMapper notificationMapper;
    private final UserMapper userMapper;
    private final AuthService authService;

    public NotificationServiceImpl(MessageNotificationMapper notificationMapper,
                                   UserMapper userMapper,
                                   AuthService authService) {
        this.notificationMapper = notificationMapper;
        this.userMapper = userMapper;
        this.authService = authService;
    }

    @Override
    public Map<String, Object> pageNotifications(String authorizationHeader, Integer type, Integer isRead, int page, int size) {
        UserEntity current = authService.getCurrentUser(authorizationHeader);
        int currentPage = Math.max(1, page);
        int pageSize = Math.max(1, Math.min(size, 100));
        int offset = (currentPage - 1) * pageSize;

        LambdaQueryWrapper<MessageNotification> countWrapper = buildWrapper(current.getId(), type, isRead);
        Long total = notificationMapper.selectCount(countWrapper);

        LambdaQueryWrapper<MessageNotification> wrapper = buildWrapper(current.getId(), type, isRead);
        wrapper.orderByDesc(MessageNotification::getCreateTime).orderByDesc(MessageNotification::getId)
                .last("LIMIT " + offset + "," + pageSize);
        List<MessageNotification> rows = notificationMapper.selectList(wrapper);

        List<Map<String, Object>> records = new ArrayList<>(rows.size());
        for (MessageNotification row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.getId());
            item.put("type", row.getType() == null ? TYPE_SYSTEM : row.getType());
            item.put("title", safe(row.getTitle()));
            item.put("content", safe(row.getContent()));
            item.put("linkUrl", safe(row.getLinkUrl()));
            item.put("isRead", row.getIsRead() == null ? READ_NO : row.getIsRead());
            item.put("createTime", row.getCreateTime() == null ? "" : row.getCreateTime().toString());
            records.add(item);
        }

        long totalValue = total == null ? 0L : total;
        long pages = totalValue == 0 ? 0 : (totalValue + pageSize - 1) / pageSize;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", totalValue);
        result.put("current", currentPage);
        result.put("size", pageSize);
        result.put("pages", pages);
        return result;
    }

    @Override
    public Map<String, Object> unreadCount(String authorizationHeader) {
        UserEntity current = authService.getCurrentUser(authorizationHeader);
        LambdaQueryWrapper<MessageNotification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MessageNotification::getUserId, current.getId());
        wrapper.eq(MessageNotification::getIsRead, READ_NO);
        Long unread = notificationMapper.selectCount(wrapper);
        return Map.of("unreadCount", unread == null ? 0L : unread);
    }

    @Override
    @Transactional
    public Map<String, Object> markRead(String authorizationHeader, Long notificationId) {
        if (notificationId == null || notificationId <= 0) {
            throw new BusinessException(400, "notificationId is invalid");
        }
        UserEntity current = authService.getCurrentUser(authorizationHeader);
        MessageNotification row = notificationMapper.selectById(notificationId);
        if (row == null) {
            throw new BusinessException(404, "notification not found");
        }
        if (!current.getId().equals(row.getUserId())) {
            throw new BusinessException(403, "not allowed to update this notification");
        }
        if (row.getIsRead() != null && row.getIsRead() == READ_YES) {
            return Map.of("updated", false);
        }
        row.setIsRead(READ_YES);
        notificationMapper.updateById(row);
        return Map.of("updated", true);
    }

    @Override
    @Transactional
    public Map<String, Object> markAllRead(String authorizationHeader) {
        UserEntity current = authService.getCurrentUser(authorizationHeader);
        LambdaQueryWrapper<MessageNotification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MessageNotification::getUserId, current.getId());
        wrapper.eq(MessageNotification::getIsRead, READ_NO);
        List<MessageNotification> rows = notificationMapper.selectList(wrapper);
        long count = 0;
        for (MessageNotification row : rows) {
            row.setIsRead(READ_YES);
            notificationMapper.updateById(row);
            count++;
        }
        return Map.of("updatedCount", count);
    }

    @Override
    @Transactional
    public Map<String, Object> publishSystemNotification(String authorizationHeader, SystemNotificationCreateRequest request) {
        UserEntity operator = authService.getCurrentUser(authorizationHeader);
        List<String> roles = authService.getCurrentRoles(authorizationHeader);
        if (!roles.contains("ADMIN")) {
            throw new BusinessException(403, "only ADMIN can publish system notifications");
        }
        if (request == null) {
            throw new BusinessException(400, "request is required");
        }
        String title = normalize(request.getTitle());
        String content = normalize(request.getContent());
        if (!StringUtils.hasText(title) || !StringUtils.hasText(content)) {
            throw new BusinessException(400, "title and content are required");
        }

        List<Long> targetUserIds = resolveTargetUserIds(request.getTargetUserId());
        long sentCount = 0;
        for (Long userId : targetUserIds) {
            MessageNotification row = new MessageNotification();
            row.setUserId(userId);
            row.setType(TYPE_SYSTEM);
            row.setTitle(title);
            row.setContent(content);
            row.setLinkUrl(normalize(request.getLinkUrl()));
            row.setIsRead(READ_NO);
            row.setCreateTime(LocalDateTime.now());
            notificationMapper.insert(row);
            sentCount++;
        }

        return Map.of(
                "operatorId", operator.getId(),
                "sentCount", sentCount
        );
    }

    private LambdaQueryWrapper<MessageNotification> buildWrapper(Long userId, Integer type, Integer isRead) {
        LambdaQueryWrapper<MessageNotification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MessageNotification::getUserId, userId);
        if (type != null && type > 0) {
            wrapper.eq(MessageNotification::getType, type);
        }
        if (isRead != null && (isRead == READ_NO || isRead == READ_YES)) {
            wrapper.eq(MessageNotification::getIsRead, isRead);
        }
        return wrapper;
    }

    private List<Long> resolveTargetUserIds(Long targetUserId) {
        if (targetUserId != null && targetUserId > 0) {
            UserEntity target = userMapper.selectById(targetUserId);
            if (target == null) {
                throw new BusinessException(404, "target user not found");
            }
            return List.of(targetUserId);
        }
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserEntity::getStatus, 1);
        wrapper.select(UserEntity::getId);
        List<UserEntity> users = userMapper.selectList(wrapper);
        List<Long> ids = new ArrayList<>(users.size());
        for (UserEntity user : users) {
            if (user.getId() != null) {
                ids.add(user.getId());
            }
        }
        if (ids.isEmpty()) {
            throw new BusinessException(409, "no available users for notification");
        }
        return ids;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
