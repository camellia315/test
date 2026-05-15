package com.campus.lostfound.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.exception.BusinessException;
import com.campus.lostfound.dto.LfCommentCreateRequest;
import com.campus.lostfound.dto.LfPrivateMessageReadRequest;
import com.campus.lostfound.dto.LfPrivateMessageSendRequest;
import com.campus.lostfound.dto.LostFoundAuditRequest;
import com.campus.lostfound.dto.LostFoundCreateRequest;
import com.campus.lostfound.entity.LfComment;
import com.campus.lostfound.entity.LfPrivateMessage;
import com.campus.lostfound.entity.LfPrivateSession;
import com.campus.lostfound.entity.LostFoundAudit;
import com.campus.lostfound.entity.LostFoundItem;
import com.campus.lostfound.mapper.LfCommentMapper;
import com.campus.lostfound.mapper.LfPrivateMessageMapper;
import com.campus.lostfound.mapper.LfPrivateSessionMapper;
import com.campus.lostfound.mapper.LostFoundAuditMapper;
import com.campus.lostfound.mapper.LostFoundItemMapper;
import com.campus.lostfound.service.FileStorageService;
import com.campus.lostfound.service.LostFoundService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class LostFoundServiceImpl implements LostFoundService {

    private static final Set<String> VALID_ITEM_TYPES = Set.of("LOST", "FOUND");
    private static final String STATUS_PENDING_AUDIT = "PENDING_AUDIT";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String STATUS_SEARCHING = "SEARCHING";
    private static final String STATUS_FOUND = "FOUND";
    private static final String STATUS_RETURNED = "RETURNED";

    private static final Set<String> VALID_STATUS = Set.of(STATUS_SEARCHING, STATUS_FOUND, STATUS_RETURNED);
    private static final Set<String> ADMIN_ROLES = Set.of("ADMIN");
    private static final int AUDIT_APPROVED = 1;
    private static final int AUDIT_REJECTED = 2;
    private static final int PRIVATE_MSG_TEXT = 1;
    private static final int PRIVATE_MSG_UNREAD = 0;
    private static final int NOTIFICATION_TYPE_PRIVATE = 5;

    private final LostFoundItemMapper itemMapper;
    private final LostFoundAuditMapper auditMapper;
    private final LfCommentMapper commentMapper;
    private final LfPrivateMessageMapper privateMessageMapper;
    private final LfPrivateSessionMapper privateSessionMapper;
    private final FileStorageService fileStorageService;
    private final JdbcTemplate jdbcTemplate;

    public LostFoundServiceImpl(LostFoundItemMapper itemMapper,
                                LostFoundAuditMapper auditMapper,
                                LfCommentMapper commentMapper,
                                LfPrivateMessageMapper privateMessageMapper,
                                LfPrivateSessionMapper privateSessionMapper,
                                FileStorageService fileStorageService,
                                JdbcTemplate jdbcTemplate) {
        this.itemMapper = itemMapper;
        this.auditMapper = auditMapper;
        this.commentMapper = commentMapper;
        this.privateMessageMapper = privateMessageMapper;
        this.privateSessionMapper = privateSessionMapper;
        this.fileStorageService = fileStorageService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Map<String, Object> pageItems(int page,
                                         int size,
                                         String keyword,
                                         String status,
                                         String itemType,
                                         Long publisherUserId) {
        Page<LostFoundItem> pageReq = new Page<>(page, size);
        LambdaQueryWrapper<LostFoundItem> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(LostFoundItem::getTitle, keyword)
                    .or()
                    .like(LostFoundItem::getDescription, keyword));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(LostFoundItem::getStatus, status.trim().toUpperCase());
        } else if (publisherUserId == null) {
            // 广场默认仅展示“寻找中”的条目，找到/归还后从广场移除。
            wrapper.eq(LostFoundItem::getStatus, STATUS_SEARCHING);
        }
        if (StringUtils.hasText(itemType)) {
            wrapper.eq(LostFoundItem::getItemType, itemType.trim().toUpperCase());
        }
        if (publisherUserId != null) {
            wrapper.eq(LostFoundItem::getUserId, publisherUserId);
        }
        wrapper.orderByDesc(LostFoundItem::getCreatedAt);

        Page<LostFoundItem> result = itemMapper.selectPage(pageReq, wrapper);
        Map<String, Object> response = new HashMap<>();
        response.put("records", result.getRecords());
        response.put("total", result.getTotal());
        response.put("pages", result.getPages());
        response.put("current", result.getCurrent());
        response.put("size", result.getSize());
        return response;
    }

    @Override
    public Map<String, Object> pageRecoveredItems(int page,
                                                  int size,
                                                  String keyword,
                                                  String status,
                                                  String itemType,
                                                  Long publisherUserId) {
        Page<LostFoundItem> pageReq = new Page<>(Math.max(page, 1), normalizeSize(size));
        LambdaQueryWrapper<LostFoundItem> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(LostFoundItem::getTitle, keyword)
                    .or()
                    .like(LostFoundItem::getDescription, keyword));
        }
        if (StringUtils.hasText(status)) {
            String normalized = status.trim().toUpperCase();
            if (STATUS_FOUND.equals(normalized) || STATUS_RETURNED.equals(normalized)) {
                wrapper.eq(LostFoundItem::getStatus, normalized);
            } else {
                wrapper.in(LostFoundItem::getStatus, STATUS_FOUND, STATUS_RETURNED);
            }
        } else {
            wrapper.in(LostFoundItem::getStatus, STATUS_FOUND, STATUS_RETURNED);
        }
        if (StringUtils.hasText(itemType)) {
            wrapper.eq(LostFoundItem::getItemType, itemType.trim().toUpperCase());
        }
        if (publisherUserId != null) {
            wrapper.eq(LostFoundItem::getUserId, publisherUserId);
        }
        wrapper.orderByDesc(LostFoundItem::getRecoveredAt).orderByDesc(LostFoundItem::getCreatedAt);

        Page<LostFoundItem> result = itemMapper.selectPage(pageReq, wrapper);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("records", result.getRecords());
        response.put("total", result.getTotal());
        response.put("pages", result.getPages());
        response.put("current", result.getCurrent());
        response.put("size", result.getSize());
        return response;
    }

    @Override
    public LostFoundItem getById(Long id) {
        LostFoundItem item = itemMapper.selectById(id);
        if (item == null) {
            throw new BusinessException(404, "Lost-found item not found");
        }
        return item;
    }

    @Override
    public LostFoundItem createItem(LostFoundCreateRequest request) {
        if (request.getUserId() == null) {
            throw new BusinessException(400, "userId is required");
        }
        if (!StringUtils.hasText(request.getTitle())) {
            throw new BusinessException(400, "title is required");
        }
        if (!StringUtils.hasText(request.getItemType())) {
            throw new BusinessException(400, "itemType is required");
        }

        String itemType = request.getItemType().trim().toUpperCase();
        if (!VALID_ITEM_TYPES.contains(itemType)) {
            throw new BusinessException(400, "itemType must be LOST or FOUND");
        }

        LostFoundItem item = new LostFoundItem();
        item.setUserId(request.getUserId());
        item.setCategoryId(request.getCategoryId());
        item.setTitle(request.getTitle().trim());
        item.setDescription(request.getDescription());
        item.setImageUrl(request.getImageUrl());
        item.setLocationText(request.getLocationText());
        item.setItemType(itemType);
        item.setStatus(STATUS_SEARCHING);
        item.setCreatedAt(LocalDateTime.now());

        itemMapper.insert(item);
        return item;
    }

    @Override
    public LostFoundItem updateStatus(Long id, String status, Long operatorUserId) {
        if (!StringUtils.hasText(status)) {
            throw new BusinessException(400, "status is required");
        }
        if (operatorUserId == null) {
            throw new BusinessException(400, "operatorUserId is required");
        }
        String normalizedStatus = status.trim().toUpperCase();
        if (!VALID_STATUS.contains(normalizedStatus)) {
            throw new BusinessException(400, "status must be one of " + Arrays.toString(VALID_STATUS.toArray()));
        }

        LostFoundItem item = getById(id);
        if (!operatorUserId.equals(item.getUserId())) {
            throw new BusinessException(403, "only publisher can update status");
        }
        item.setStatus(normalizedStatus);
        if (STATUS_FOUND.equals(normalizedStatus) || STATUS_RETURNED.equals(normalizedStatus)) {
            item.setRecoveredAt(LocalDateTime.now());
        } else if (STATUS_SEARCHING.equals(normalizedStatus)) {
            item.setRecoveredAt(null);
        }
        itemMapper.updateById(item);
        return itemMapper.selectById(id);
    }

    @Override
    public Map<String, Object> pagePendingAuditItems(int page, int size, String operatorRole) {
        ensureAdminRole(operatorRole);

        Page<LostFoundItem> pageReq = new Page<>(Math.max(page, 1), normalizeSize(size));
        LambdaQueryWrapper<LostFoundItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LostFoundItem::getStatus, STATUS_PENDING_AUDIT);
        wrapper.orderByAsc(LostFoundItem::getCreatedAt);

        Page<LostFoundItem> result = itemMapper.selectPage(pageReq, wrapper);
        Map<String, Object> response = new HashMap<>();
        response.put("records", result.getRecords());
        response.put("total", result.getTotal());
        response.put("pages", result.getPages());
        response.put("current", result.getCurrent());
        response.put("size", result.getSize());
        return response;
    }

    @Override
    @Transactional
    public LostFoundItem auditItem(Long id, LostFoundAuditRequest request) {
        if (request == null) {
            throw new BusinessException(400, "request is required");
        }
        if (request.getAuditorId() == null) {
            throw new BusinessException(400, "auditorId is required");
        }
        ensureAdminRole(request.getAuditorRole());
        if (request.getStatus() == null || (request.getStatus() != AUDIT_APPROVED && request.getStatus() != AUDIT_REJECTED)) {
            throw new BusinessException(400, "status must be 1(approved) or 2(rejected)");
        }
        if (request.getStatus() == AUDIT_REJECTED && !StringUtils.hasText(request.getReason())) {
            throw new BusinessException(400, "reason is required when rejected");
        }

        LostFoundItem item = getById(id);
        if (!STATUS_PENDING_AUDIT.equals(item.getStatus())) {
            throw new BusinessException(409, "item is not in pending-audit status");
        }

        LostFoundAudit audit = new LostFoundAudit();
        audit.setLostFoundId(id);
        audit.setAuditorId(request.getAuditorId());
        audit.setStatus(request.getStatus());
        audit.setReason(normalizeText(request.getReason()));
        audit.setAuditTime(LocalDateTime.now());
        auditMapper.insert(audit);

        item.setStatus(request.getStatus() == AUDIT_APPROVED ? STATUS_SEARCHING : STATUS_REJECTED);
        itemMapper.updateById(item);
        return itemMapper.selectById(id);
    }

    @Override
    public List<LostFoundAudit> listAuditRecords(Long lostFoundId) {
        getById(lostFoundId);
        LambdaQueryWrapper<LostFoundAudit> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LostFoundAudit::getLostFoundId, lostFoundId);
        wrapper.orderByDesc(LostFoundAudit::getAuditTime);
        return auditMapper.selectList(wrapper);
    }

    @Override
    public void deleteItem(Long id, Long operatorUserId) {
        if (operatorUserId == null) {
            throw new BusinessException(400, "operatorUserId is required");
        }

        LostFoundItem item = getById(id);
        if (!operatorUserId.equals(item.getUserId())) {
            throw new BusinessException(403, "Only publisher can delete this item");
        }

        LambdaQueryWrapper<LfComment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LfComment::getLostFoundId, id);
        commentMapper.delete(wrapper);
        itemMapper.deleteById(id);

        if (StringUtils.hasText(item.getImageUrl())) {
            try {
                fileStorageService.deleteImage(item.getImageUrl());
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public List<LfComment> listComments(Long lostFoundId) {
        getById(lostFoundId);
        try {
            return commentMapper.selectWithUserInfo(lostFoundId);
        } catch (Exception ignored) {
            // Some local screenshot/demo databases only create lost-found tables.
            // Keep comments usable even when the shared user table is absent.
        }
        LambdaQueryWrapper<LfComment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LfComment::getLostFoundId, lostFoundId);
        wrapper.orderByAsc(LfComment::getCreatedAt);
        return commentMapper.selectList(wrapper);
    }

    @Override
    public LfComment createComment(Long lostFoundId, LfCommentCreateRequest request) {
        getById(lostFoundId);
        if (request.getUserId() == null) {
            throw new BusinessException(400, "userId is required");
        }
        if (!StringUtils.hasText(request.getContent())) {
            throw new BusinessException(400, "content is required");
        }

        LfComment comment = new LfComment();
        comment.setLostFoundId(lostFoundId);
        comment.setUserId(request.getUserId());
        comment.setContent(request.getContent().trim());
        comment.setCreatedAt(LocalDateTime.now());
        commentMapper.insert(comment);
        return comment;
    }

    @Override
    public void deleteComment(Long lostFoundId, Long commentId, Long operatorUserId) {
        if (operatorUserId == null) {
            throw new BusinessException(400, "operatorUserId is required");
        }
        getById(lostFoundId);
        LfComment comment = commentMapper.selectById(commentId);
        if (comment == null || !Objects.equals(comment.getLostFoundId(), lostFoundId)) {
            throw new BusinessException(404, "comment not found");
        }
        if (!Objects.equals(comment.getUserId(), operatorUserId)) {
            throw new BusinessException(403, "only comment author can delete this comment");
        }
        commentMapper.deleteById(commentId);
    }

    @Override
    public List<LfPrivateSession> listPrivateSessions(Long userId, Long itemId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(400, "userId is required");
        }
        LambdaQueryWrapper<LfPrivateSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LfPrivateSession::getUserId, userId);
        if (itemId != null && itemId > 0) {
            wrapper.eq(LfPrivateSession::getItemId, itemId);
        }
        wrapper.orderByDesc(LfPrivateSession::getUpdateTime).orderByDesc(LfPrivateSession::getId);
        return privateSessionMapper.selectList(wrapper);
    }

    @Override
    public Map<String, Object> pagePrivateMessages(Long itemId, Long userId, Long otherUserId, int page, int size) {
        if (itemId == null || itemId <= 0) {
            throw new BusinessException(400, "itemId is required");
        }
        if (userId == null || userId <= 0 || otherUserId == null || otherUserId <= 0) {
            throw new BusinessException(400, "userId and otherUserId are required");
        }
        getById(itemId);

        Page<LfPrivateMessage> pageReq = new Page<>(Math.max(1, page), normalizeSize(size));
        LambdaQueryWrapper<LfPrivateMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LfPrivateMessage::getItemId, itemId);
        wrapper.and(w -> w
                .and(i -> i.eq(LfPrivateMessage::getFromUserId, userId).eq(LfPrivateMessage::getToUserId, otherUserId))
                .or(i -> i.eq(LfPrivateMessage::getFromUserId, otherUserId).eq(LfPrivateMessage::getToUserId, userId))
        );
        wrapper.orderByDesc(LfPrivateMessage::getCreateTime);
        Page<LfPrivateMessage> result = privateMessageMapper.selectPage(pageReq, wrapper);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("records", result.getRecords());
        response.put("total", result.getTotal());
        response.put("pages", result.getPages());
        response.put("current", result.getCurrent());
        response.put("size", result.getSize());
        return response;
    }

    @Override
    @Transactional
    public LfPrivateMessage sendPrivateMessage(LfPrivateMessageSendRequest request) {
        if (request == null) {
            throw new BusinessException(400, "request is required");
        }
        if (request.getItemId() == null || request.getItemId() <= 0) {
            throw new BusinessException(400, "itemId is required");
        }
        if (request.getFromUserId() == null || request.getFromUserId() <= 0
                || request.getToUserId() == null || request.getToUserId() <= 0) {
            throw new BusinessException(400, "fromUserId and toUserId are required");
        }
        if (Objects.equals(request.getFromUserId(), request.getToUserId())) {
            throw new BusinessException(409, "cannot send private message to yourself");
        }
        if (!StringUtils.hasText(request.getContent())) {
            throw new BusinessException(400, "content is required");
        }

        LostFoundItem item = getById(request.getItemId());
        LfPrivateMessage message = new LfPrivateMessage();
        message.setItemId(request.getItemId());
        message.setFromUserId(request.getFromUserId());
        message.setToUserId(request.getToUserId());
        message.setContent(request.getContent().trim());
        message.setMsgType(request.getMsgType() == null ? PRIVATE_MSG_TEXT : request.getMsgType());
        message.setIsRead(PRIVATE_MSG_UNREAD);
        message.setCreateTime(LocalDateTime.now());
        privateMessageMapper.insert(message);

        upsertPrivateSession(item.getId(), request.getFromUserId(), request.getToUserId(), message.getContent(), false);
        upsertPrivateSession(item.getId(), request.getToUserId(), request.getFromUserId(), message.getContent(), true);
        pushPrivateMessageNotification(item, message);
        return message;
    }

    @Override
    @Transactional
    public Map<String, Object> markPrivateMessageRead(LfPrivateMessageReadRequest request) {
        if (request == null || request.getItemId() == null || request.getItemId() <= 0
                || request.getUserId() == null || request.getUserId() <= 0
                || request.getOtherUserId() == null || request.getOtherUserId() <= 0) {
            throw new BusinessException(400, "itemId/userId/otherUserId are required");
        }
        int updated = privateMessageMapper.markAsRead(request.getItemId(), request.getUserId(), request.getOtherUserId());
        privateSessionMapper.resetUnreadCount(request.getItemId(), request.getUserId(), request.getOtherUserId());
        return Map.of("updated", updated);
    }

    @Override
    public Map<String, Object> privateUnreadSummary(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(400, "userId is required");
        }
        List<LfPrivateSession> sessions = listPrivateSessions(userId, null);
        int unreadTotal = sessions.stream()
                .map(LfPrivateSession::getUnreadCount)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("unreadTotal", unreadTotal);
        result.put("sessions", sessions);
        return result;
    }

    private void upsertPrivateSession(Long itemId, Long userId, Long otherUserId, String content, boolean increaseUnread) {
        LfPrivateSession session = privateSessionMapper.selectByItemAndUsers(itemId, userId, otherUserId);
        if (session == null) {
            session = new LfPrivateSession();
            session.setItemId(itemId);
            session.setUserId(userId);
            session.setOtherUserId(otherUserId);
            session.setLastMessage(content);
            session.setUnreadCount(increaseUnread ? 1 : 0);
            session.setUpdateTime(LocalDateTime.now());
            privateSessionMapper.insert(session);
            return;
        }
        session.setLastMessage(content);
        session.setUnreadCount(increaseUnread
                ? (session.getUnreadCount() == null ? 1 : session.getUnreadCount() + 1)
                : 0);
        session.setUpdateTime(LocalDateTime.now());
        privateSessionMapper.updateById(session);
    }

    private void pushPrivateMessageNotification(LostFoundItem item, LfPrivateMessage message) {
        String title = "【失物招领-私信】来自用户#" + message.getFromUserId();
        String content = "物品#" + item.getId() + "《" + safe(item.getTitle()) + "》收到私信：" + shortText(message.getContent(), 80);
        try {
            jdbcTemplate.update(
                    "INSERT INTO message_notification(user_id, type, title, content, link_url, is_read, create_time) VALUES (?, ?, ?, ?, ?, 0, NOW())",
                    message.getToUserId(),
                    NOTIFICATION_TYPE_PRIVATE,
                    title,
                    content,
                    "/lost-found"
            );
        } catch (Exception ignored) {
        }
    }

    private String shortText(String content, int limit) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String text = content.trim();
        if (text.length() <= limit) {
            return text;
        }
        return text.substring(0, Math.max(limit - 1, 1)) + "…";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void ensureAdminRole(String operatorRole) {
        if (!StringUtils.hasText(operatorRole)) {
            throw new BusinessException(403, "only ADMIN can audit");
        }
        String normalized = operatorRole.trim().toUpperCase();
        if (!ADMIN_ROLES.contains(normalized)) {
            throw new BusinessException(403, "only ADMIN can audit");
        }
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return 10;
        }
        return Math.min(size, 100);
    }
}
