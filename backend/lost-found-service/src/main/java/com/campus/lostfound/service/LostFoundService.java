package com.campus.lostfound.service;

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

import java.util.List;
import java.util.Map;

public interface LostFoundService {
    Map<String, Object> pageItems(int page, int size, String keyword, String status, String itemType, Long publisherUserId);
    Map<String, Object> pageRecoveredItems(int page, int size, String keyword, String status, String itemType, Long publisherUserId);

    LostFoundItem getById(Long id);

    LostFoundItem createItem(LostFoundCreateRequest request);

    LostFoundItem updateStatus(Long id, String status, Long operatorUserId);

    Map<String, Object> pagePendingAuditItems(int page, int size, String operatorRole);

    LostFoundItem auditItem(Long id, LostFoundAuditRequest request);

    List<LostFoundAudit> listAuditRecords(Long lostFoundId);

    void deleteItem(Long id, Long operatorUserId);

    List<LfComment> listComments(Long lostFoundId);

    LfComment createComment(Long lostFoundId, LfCommentCreateRequest request);

    void deleteComment(Long lostFoundId, Long commentId, Long operatorUserId);

    List<LfPrivateSession> listPrivateSessions(Long userId, Long itemId);

    Map<String, Object> pagePrivateMessages(Long itemId, Long userId, Long otherUserId, int page, int size);

    LfPrivateMessage sendPrivateMessage(LfPrivateMessageSendRequest request);

    Map<String, Object> markPrivateMessageRead(LfPrivateMessageReadRequest request);

    Map<String, Object> privateUnreadSummary(Long userId);
}
