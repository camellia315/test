package com.campus.lostfound.controller;

import com.campus.common.api.ApiResponse;
import com.campus.lostfound.dto.LfCommentCreateRequest;
import com.campus.lostfound.dto.LostFoundCreateRequest;
import com.campus.lostfound.dto.LostFoundStatusUpdateRequest;
import com.campus.lostfound.entity.LfComment;
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
                                                  @RequestParam(required = false) String itemType) {
        return ApiResponse.success(lostFoundService.pageItems(page, size, keyword, status, itemType));
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
        return ApiResponse.success(lostFoundService.updateStatus(id, request.getStatus()));
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
}
