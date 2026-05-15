package com.campus.activity.controller;

import com.campus.activity.dto.ActivityApplyRequest;
import com.campus.activity.dto.ActivityApplyReviewRequest;
import com.campus.activity.dto.ActivityAuditRequest;
import com.campus.activity.dto.ActivityCategoryCreateRequest;
import com.campus.activity.dto.ActivityCreateRequest;
import com.campus.activity.entity.Activity;
import com.campus.activity.entity.ActivityApply;
import com.campus.activity.entity.ActivityAudit;
import com.campus.activity.entity.ActivityCategory;
import com.campus.activity.service.ActivityService;
import com.campus.common.api.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/activities")
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "10") int size,
                                                  @RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) Integer categoryId,
                                                  @RequestParam(required = false) Integer status,
                                                  @RequestParam(required = false) Long publisherUserId,
                                                  @RequestParam(required = false) Long viewerUserId) {
        return ApiResponse.success(activityService.pageActivities(page, size, keyword, categoryId, status, publisherUserId, viewerUserId));
    }

    @PostMapping
    public ApiResponse<Activity> publish(@RequestBody ActivityCreateRequest request) {
        return ApiResponse.success(activityService.createActivity(request));
    }

    @GetMapping("/audit/pending")
    public ApiResponse<Map<String, Object>> pendingAudit(@RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "10") int size,
                                                         @RequestParam(required = false) String operatorRole) {
        return ApiResponse.success(activityService.pagePendingAuditActivities(page, size, operatorRole));
    }

    @GetMapping("/categories")
    public ApiResponse<List<ActivityCategory>> categories() {
        return ApiResponse.success(activityService.listCategories());
    }

    @PostMapping("/categories")
    public ApiResponse<ActivityCategory> createCategory(@RequestBody ActivityCategoryCreateRequest request) {
        return ApiResponse.success(activityService.createCategory(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<Activity> detail(@PathVariable Long id) {
        return ApiResponse.success(activityService.getActivityDetail(id));
    }

    @PatchMapping("/{id}/audit")
    public ApiResponse<Activity> audit(@PathVariable Long id, @RequestBody ActivityAuditRequest request) {
        return ApiResponse.success(activityService.auditActivity(id, request));
    }

    @GetMapping("/{id}/audits")
    public ApiResponse<List<ActivityAudit>> auditRecords(@PathVariable Long id) {
        return ApiResponse.success(activityService.listAuditRecords(id));
    }

    @PostMapping("/{id}/apply")
    public ApiResponse<ActivityApply> apply(@PathVariable Long id, @RequestBody ActivityApplyRequest request) {
        return ApiResponse.success(activityService.apply(id, request));
    }

    @PostMapping("/{id}/apply/cancel")
    public ApiResponse<ActivityApply> cancelApply(@PathVariable Long id, @RequestBody ActivityApplyRequest request) {
        return ApiResponse.success(activityService.cancelApply(id, request.getUserId()));
    }

    @GetMapping("/{id}/applies")
    public ApiResponse<Map<String, Object>> applies(@PathVariable Long id,
                                                    @RequestParam Long operatorUserId,
                                                    @RequestParam(required = false) Integer status,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(activityService.pageApplies(id, operatorUserId, status, page, size));
    }

    @GetMapping("/joined")
    public ApiResponse<Map<String, Object>> joined(@RequestParam Long userId,
                                                   @RequestParam(required = false) Integer activityStatus,
                                                   @RequestParam(required = false) Integer applyStatus,
                                                   @RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(activityService.pageJoinedActivities(userId, activityStatus, applyStatus, page, size));
    }

    @PatchMapping("/{id}/applies/{applyId}/review")
    public ApiResponse<ActivityApply> reviewApply(@PathVariable Long id,
                                                  @PathVariable Long applyId,
                                                  @RequestBody ActivityApplyReviewRequest request) {
        return ApiResponse.success(activityService.reviewApply(id, applyId, request));
    }

    @PatchMapping("/{id}/stop")
    public ApiResponse<Activity> stopActivity(@PathVariable Long id,
                                              @RequestParam Long operatorUserId) {
        return ApiResponse.success(activityService.stopActivity(id, operatorUserId));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> deleteActivity(@PathVariable Long id,
                                               @RequestParam Long operatorUserId) {
        activityService.deleteActivity(id, operatorUserId);
        return ApiResponse.success(true);
    }
}
