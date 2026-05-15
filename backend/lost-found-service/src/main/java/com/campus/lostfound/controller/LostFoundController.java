package com.campus.lostfound.controller;

import com.campus.common.api.ApiResponse;
import com.campus.lostfound.dto.LfCommentCreateRequest;
import com.campus.lostfound.dto.LfPrivateMessageReadRequest;
import com.campus.lostfound.dto.LfPrivateMessageSendRequest;
import com.campus.lostfound.dto.LostFoundAuditRequest;
import com.campus.lostfound.dto.LostFoundCreateRequest;
import com.campus.lostfound.dto.LostFoundStatusUpdateRequest;
import com.campus.lostfound.entity.LfComment;
import com.campus.lostfound.entity.LfPrivateMessage;
import com.campus.lostfound.entity.LfPrivateSession;
import com.campus.lostfound.entity.LostFoundAudit;
import com.campus.lostfound.entity.LostFoundItem;
import com.campus.lostfound.service.LostFoundService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/lost-found")
public class LostFoundController {

    private final LostFoundService lostFoundService;

    public LostFoundController(LostFoundService lostFoundService) {
        this.lostFoundService = lostFoundService;
    }

    @GetMapping("/items")
    public ApiResponse<Map<String, Object>> list(@RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "10") int size,
                                                  @RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) String status,
                                                  @RequestParam(required = false) String itemType,
                                                  @RequestParam(required = false) Long publisherUserId) {
        return ApiResponse.success(lostFoundService.pageItems(page, size, keyword, status, itemType, publisherUserId));
    }

    @GetMapping("/items/history")
    public ApiResponse<Map<String, Object>> history(@RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "10") int size,
                                                     @RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) String status,
                                                     @RequestParam(required = false) String itemType,
                                                     @RequestParam(required = false) Long publisherUserId) {
        return ApiResponse.success(lostFoundService.pageRecoveredItems(page, size, keyword, status, itemType, publisherUserId));
    }

    @GetMapping("/items/{id}")
    public ApiResponse<LostFoundItem> detail(@PathVariable Long id) {
        return ApiResponse.success(lostFoundService.getById(id));
    }

    @PostMapping("/items")
    public ApiResponse<LostFoundItem> create(@RequestBody LostFoundCreateRequest request) {
        return ApiResponse.success(lostFoundService.createItem(request));
    }

    @PatchMapping("/items/{id}/status")
    public ApiResponse<LostFoundItem> updateStatus(@PathVariable Long id,
                                                   @RequestBody LostFoundStatusUpdateRequest request) {
        return ApiResponse.success(lostFoundService.updateStatus(
                id,
                request == null ? null : request.getStatus(),
                request == null ? null : request.getOperatorUserId()
        ));
    }

    @GetMapping("/items/audit/pending")
    public ApiResponse<Map<String, Object>> pendingAudit(@RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "10") int size,
                                                         @RequestParam(required = false) String operatorRole) {
        return ApiResponse.success(lostFoundService.pagePendingAuditItems(page, size, operatorRole));
    }

    @PatchMapping("/items/{id}/audit")
    public ApiResponse<LostFoundItem> audit(@PathVariable Long id,
                                            @RequestBody LostFoundAuditRequest request) {
        return ApiResponse.success(lostFoundService.auditItem(id, request));
    }

    @GetMapping("/items/{id}/audits")
    public ApiResponse<List<LostFoundAudit>> audits(@PathVariable Long id) {
        return ApiResponse.success(lostFoundService.listAuditRecords(id));
    }

    @DeleteMapping("/items/{id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable Long id,
                                                    @RequestParam Long operatorUserId) {
        lostFoundService.deleteItem(id, operatorUserId);
        return ApiResponse.success(Map.of("deleted", true));
    }

    @GetMapping("/items/{id}/comments")
    public ApiResponse<List<LfComment>> comments(@PathVariable Long id) {
        return ApiResponse.success(lostFoundService.listComments(id));
    }

    @PostMapping("/items/{id}/comments")
    public ApiResponse<LfComment> createComment(@PathVariable Long id,
                                                @RequestBody LfCommentCreateRequest request) {
        return ApiResponse.success(lostFoundService.createComment(id, request));
    }

    @DeleteMapping("/items/{id}/comments/{commentId}")
    public ApiResponse<Map<String, Object>> deleteComment(@PathVariable Long id,
                                                          @PathVariable Long commentId,
                                                          @RequestParam Long operatorUserId) {
        lostFoundService.deleteComment(id, commentId, operatorUserId);
        return ApiResponse.success(Map.of("deleted", true));
    }

    @GetMapping("/private/sessions")
    public ApiResponse<List<LfPrivateSession>> privateSessions(@RequestParam Long userId,
                                                               @RequestParam(required = false) Long itemId) {
        return ApiResponse.success(lostFoundService.listPrivateSessions(userId, itemId));
    }

    @GetMapping("/private/messages")
    public ApiResponse<Map<String, Object>> privateMessages(@RequestParam Long itemId,
                                                            @RequestParam Long userId,
                                                            @RequestParam Long otherUserId,
                                                            @RequestParam(defaultValue = "1") int page,
                                                            @RequestParam(defaultValue = "30") int size) {
        return ApiResponse.success(lostFoundService.pagePrivateMessages(itemId, userId, otherUserId, page, size));
    }

    @PostMapping("/private/messages")
    public ApiResponse<LfPrivateMessage> sendPrivateMessage(@RequestBody LfPrivateMessageSendRequest request) {
        return ApiResponse.success(lostFoundService.sendPrivateMessage(request));
    }

    @PostMapping("/private/read")
    public ApiResponse<Map<String, Object>> readPrivateMessages(@RequestBody LfPrivateMessageReadRequest request) {
        return ApiResponse.success(lostFoundService.markPrivateMessageRead(request));
    }

    @GetMapping("/private/unread")
    public ApiResponse<Map<String, Object>> privateUnread(@RequestParam Long userId) {
        return ApiResponse.success(lostFoundService.privateUnreadSummary(userId));
    }
}
