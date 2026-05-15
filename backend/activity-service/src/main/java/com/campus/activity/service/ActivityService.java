package com.campus.activity.service;

import com.campus.activity.dto.ActivityApplyRequest;
import com.campus.activity.dto.ActivityApplyReviewRequest;
import com.campus.activity.dto.ActivityAuditRequest;
import com.campus.activity.dto.ActivityCategoryCreateRequest;
import com.campus.activity.dto.ActivityCreateRequest;
import com.campus.activity.entity.Activity;
import com.campus.activity.entity.ActivityApply;
import com.campus.activity.entity.ActivityAudit;
import com.campus.activity.entity.ActivityCategory;

import java.util.List;
import java.util.Map;

public interface ActivityService {
    Map<String, Object> pageActivities(int page,
                                       int size,
                                       String keyword,
                                       Integer categoryId,
                                       Integer status,
                                       Long publisherUserId,
                                       Long viewerUserId);

    Activity getActivityDetail(Long id);

    Activity createActivity(ActivityCreateRequest request);

    Map<String, Object> pagePendingAuditActivities(int page, int size, String operatorRole);

    Activity auditActivity(Long activityId, ActivityAuditRequest request);

    List<ActivityAudit> listAuditRecords(Long activityId);

    ActivityApply apply(Long activityId, ActivityApplyRequest request);

    ActivityApply cancelApply(Long activityId, Long userId);

    Map<String, Object> pageApplies(Long activityId, Long operatorUserId, Integer status, int page, int size);

    Map<String, Object> pageJoinedActivities(Long userId,
                                             Integer activityStatus,
                                             Integer applyStatus,
                                             int page,
                                             int size);

    ActivityApply reviewApply(Long activityId, Long applyId, ActivityApplyReviewRequest request);

    Activity stopActivity(Long activityId, Long operatorUserId);

    void deleteActivity(Long activityId, Long operatorUserId);

    List<ActivityCategory> listCategories();

    ActivityCategory createCategory(ActivityCategoryCreateRequest request);
}
