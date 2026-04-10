package com.campus.activity.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.activity.dto.ActivityApplyRequest;
import com.campus.activity.dto.ActivityApplyReviewRequest;
import com.campus.activity.dto.ActivityAuditRequest;
import com.campus.activity.dto.ActivityCategoryCreateRequest;
import com.campus.activity.dto.ActivityCreateRequest;
import com.campus.activity.entity.Activity;
import com.campus.activity.entity.ActivityApply;
import com.campus.activity.entity.ActivityAudit;
import com.campus.activity.entity.ActivityCategory;
import com.campus.activity.mapper.ActivityApplyMapper;
import com.campus.activity.mapper.ActivityAuditMapper;
import com.campus.activity.mapper.ActivityCategoryMapper;
import com.campus.activity.mapper.ActivityMapper;
import com.campus.activity.service.ActivityService;
import com.campus.common.exception.BusinessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ActivityServiceImpl implements ActivityService {

    private static final int ACTIVITY_PENDING_AUDIT = 0;
    private static final int ACTIVITY_ENROLLING = 1;
    private static final int ACTIVITY_ENDED = 2;
    private static final int ACTIVITY_REJECTED = 3;

    private static final int APPLY_PENDING = 0;
    private static final int APPLY_APPROVED = 1;
    private static final int APPLY_REJECTED = 2;
    private static final int APPLY_CANCELED = 3;

    private static final int AUDIT_APPROVED = 1;
    private static final int AUDIT_REJECTED = 2;

    private static final int FLAG_TRUE = 1;
    private static final Set<String> PUBLISH_ALLOWED_ROLES = Set.of("CLUB_ADMIN", "ADMIN");

    private final ActivityMapper activityMapper;
    private final ActivityApplyMapper activityApplyMapper;
    private final ActivityAuditMapper activityAuditMapper;
    private final ActivityCategoryMapper activityCategoryMapper;

    public ActivityServiceImpl(ActivityMapper activityMapper,
                               ActivityApplyMapper activityApplyMapper,
                               ActivityAuditMapper activityAuditMapper,
                               ActivityCategoryMapper activityCategoryMapper) {
        this.activityMapper = activityMapper;
        this.activityApplyMapper = activityApplyMapper;
        this.activityAuditMapper = activityAuditMapper;
        this.activityCategoryMapper = activityCategoryMapper;
    }

    @Override
    public Map<String, Object> pageActivities(int page,
                                              int size,
                                              String keyword,
                                              Integer categoryId,
                                              Integer status,
                                              Long publisherUserId) {
        syncEndedActivities();

        Page<Activity> pageReq = new Page<>(normalizePage(page), normalizeSize(size));
        LambdaQueryWrapper<Activity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Activity::getTitle, keyword.trim());
        }
        if (categoryId != null) {
            wrapper.eq(Activity::getCategoryId, categoryId);
        }
        if (status != null) {
            wrapper.eq(Activity::getStatus, status);
        }
        if (publisherUserId != null) {
            wrapper.eq(Activity::getUserId, publisherUserId);
        }
        wrapper.orderByDesc(Activity::getCreateTime);

        Page<Activity> result = activityMapper.selectPage(pageReq, wrapper);
        return toPageData(result);
    }

    @Override
    public Activity getActivityDetail(Long id) {
        Activity activity = getActivityOrThrow(id);
        refreshEndedStatus(activity);
        return activityMapper.selectById(id);
    }

    @Override
    @Transactional
    public Activity createActivity(ActivityCreateRequest request) {
        if (request == null) {
            throw new BusinessException(400, "request is required");
        }
        if (request.getUserId() == null) {
            throw new BusinessException(400, "userId is required");
        }
        if (!StringUtils.hasText(request.getTitle())) {
            throw new BusinessException(400, "title is required");
        }
        if (request.getStartTime() == null || request.getEndTime() == null) {
            throw new BusinessException(400, "startTime and endTime are required");
        }
        if (!request.getStartTime().isBefore(request.getEndTime())) {
            throw new BusinessException(400, "startTime must be before endTime");
        }
        if (request.getMaxParticipants() != null && request.getMaxParticipants() < 0) {
            throw new BusinessException(400, "maxParticipants must be >= 0");
        }

        validatePublishRole(request.getPublisherRole());

        Activity activity = new Activity();
        activity.setTitle(request.getTitle().trim());
        activity.setCategoryId(request.getCategoryId());
        activity.setCoverImage(request.getCoverImage());
        activity.setDescription(request.getDescription());
        activity.setLocation(request.getLocation());
        activity.setStartTime(request.getStartTime());
        activity.setEndTime(request.getEndTime());
        activity.setMaxParticipants(request.getMaxParticipants() == null ? 0 : request.getMaxParticipants());
        activity.setCurrentParticipants(0);
        activity.setStatus(ACTIVITY_PENDING_AUDIT);
        activity.setApplyAuditRequired(Boolean.TRUE.equals(request.getApplyAuditRequired()) ? FLAG_TRUE : 0);
        activity.setClubId(request.getClubId());
        activity.setUserId(request.getUserId());
        activity.setCreateTime(LocalDateTime.now());

        activityMapper.insert(activity);
        return activity;
    }

    @Override
    public Map<String, Object> pagePendingAuditActivities(int page, int size) {
        Page<Activity> pageReq = new Page<>(normalizePage(page), normalizeSize(size));
        LambdaQueryWrapper<Activity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Activity::getStatus, ACTIVITY_PENDING_AUDIT);
        wrapper.orderByAsc(Activity::getCreateTime);
        return toPageData(activityMapper.selectPage(pageReq, wrapper));
    }

    @Override
    @Transactional
    public Activity auditActivity(Long activityId, ActivityAuditRequest request) {
        if (request == null) {
            throw new BusinessException(400, "request is required");
        }
        if (request.getAuditorId() == null) {
            throw new BusinessException(400, "auditorId is required");
        }
        if (request.getStatus() == null || (request.getStatus() != AUDIT_APPROVED && request.getStatus() != AUDIT_REJECTED)) {
            throw new BusinessException(400, "status must be 1(approved) or 2(rejected)");
        }
        if (request.getStatus() == AUDIT_REJECTED && !StringUtils.hasText(request.getReason())) {
            throw new BusinessException(400, "reason is required when rejected");
        }

        Activity activity = getActivityOrThrow(activityId);
        if (activity.getStatus() == null || activity.getStatus() != ACTIVITY_PENDING_AUDIT) {
            throw new BusinessException(409, "activity is not in pending-audit status");
        }

        ActivityAudit auditRecord = new ActivityAudit();
        auditRecord.setActivityId(activityId);
        auditRecord.setAuditorId(request.getAuditorId());
        auditRecord.setStatus(request.getStatus());
        auditRecord.setReason(request.getReason());
        auditRecord.setAuditTime(LocalDateTime.now());
        activityAuditMapper.insert(auditRecord);

        if (request.getStatus() == AUDIT_APPROVED) {
            int nextStatus = isEnded(activity.getEndTime()) ? ACTIVITY_ENDED : ACTIVITY_ENROLLING;
            activity.setStatus(nextStatus);
        } else {
            activity.setStatus(ACTIVITY_REJECTED);
        }
        activityMapper.updateById(activity);
        return activityMapper.selectById(activityId);
    }

    @Override
    public List<ActivityAudit> listAuditRecords(Long activityId) {
        getActivityOrThrow(activityId);
        LambdaQueryWrapper<ActivityAudit> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ActivityAudit::getActivityId, activityId);
        wrapper.orderByDesc(ActivityAudit::getAuditTime);
        return activityAuditMapper.selectList(wrapper);
    }

    @Override
    @Transactional
    public ActivityApply apply(Long activityId, ActivityApplyRequest request) {
        if (request == null || request.getUserId() == null) {
            throw new BusinessException(400, "userId is required");
        }

        Activity activity = getActivityDetail(activityId);
        ensureActivityCanApply(activity);

        int targetApplyStatus = isApplyAuditRequired(activity) ? APPLY_PENDING : APPLY_APPROVED;
        ActivityApply existed = activityApplyMapper.selectByActivityAndUser(activityId, request.getUserId());

        if (existed != null) {
            if (existed.getStatus() != null && (existed.getStatus() == APPLY_PENDING || existed.getStatus() == APPLY_APPROVED)) {
                throw new BusinessException(409, "already applied");
            }
            if (targetApplyStatus == APPLY_APPROVED) {
                tryIncreaseParticipants(activityId);
            }
            existed.setStatus(targetApplyStatus);
            existed.setApplyTime(LocalDateTime.now());
            activityApplyMapper.updateById(existed);
            return activityApplyMapper.selectById(existed.getId());
        }

        ActivityApply apply = new ActivityApply();
        apply.setActivityId(activityId);
        apply.setUserId(request.getUserId());
        apply.setApplyTime(LocalDateTime.now());
        apply.setStatus(targetApplyStatus);

        if (targetApplyStatus == APPLY_APPROVED) {
            tryIncreaseParticipants(activityId);
        }

        try {
            activityApplyMapper.insert(apply);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(409, "already applied");
        }
        return apply;
    }

    @Override
    @Transactional
    public ActivityApply cancelApply(Long activityId, Long userId) {
        if (userId == null) {
            throw new BusinessException(400, "userId is required");
        }

        getActivityOrThrow(activityId);
        ActivityApply apply = activityApplyMapper.selectByActivityAndUser(activityId, userId);
        if (apply == null) {
            throw new BusinessException(404, "apply record not found");
        }
        if (apply.getStatus() != null && apply.getStatus() == APPLY_CANCELED) {
            return apply;
        }

        if (apply.getStatus() != null && apply.getStatus() == APPLY_APPROVED) {
            activityMapper.decreaseParticipants(activityId);
        }

        apply.setStatus(APPLY_CANCELED);
        activityApplyMapper.updateById(apply);
        return activityApplyMapper.selectById(apply.getId());
    }

    @Override
    public Map<String, Object> pageApplies(Long activityId, Long operatorUserId, Integer status, int page, int size) {
        Activity activity = getActivityOrThrow(activityId);
        if (operatorUserId == null) {
            throw new BusinessException(400, "operatorUserId is required");
        }
        if (!operatorUserId.equals(activity.getUserId())) {
            throw new BusinessException(403, "only activity publisher can view apply list");
        }

        Page<ActivityApply> pageReq = new Page<>(normalizePage(page), normalizeSize(size));
        LambdaQueryWrapper<ActivityApply> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ActivityApply::getActivityId, activityId);
        if (status != null) {
            wrapper.eq(ActivityApply::getStatus, status);
        }
        wrapper.orderByDesc(ActivityApply::getApplyTime);

        Page<ActivityApply> result = activityApplyMapper.selectPage(pageReq, wrapper);
        return toPageData(result);
    }

    @Override
    @Transactional
    public ActivityApply reviewApply(Long activityId, Long applyId, ActivityApplyReviewRequest request) {
        if (request == null) {
            throw new BusinessException(400, "request is required");
        }
        if (request.getOperatorUserId() == null) {
            throw new BusinessException(400, "operatorUserId is required");
        }
        if (request.getStatus() == null || (request.getStatus() != APPLY_APPROVED && request.getStatus() != APPLY_REJECTED)) {
            throw new BusinessException(400, "status must be 1(approved) or 2(rejected)");
        }

        Activity activity = getActivityDetail(activityId);
        if (!request.getOperatorUserId().equals(activity.getUserId())) {
            throw new BusinessException(403, "only activity publisher can review apply");
        }
        if (!isApplyAuditRequired(activity)) {
            throw new BusinessException(409, "this activity does not require apply audit");
        }

        ActivityApply apply = activityApplyMapper.selectById(applyId);
        if (apply == null || !activityId.equals(apply.getActivityId())) {
            throw new BusinessException(404, "apply record not found");
        }
        if (apply.getStatus() == null || apply.getStatus() != APPLY_PENDING) {
            throw new BusinessException(409, "only pending apply can be reviewed");
        }

        if (request.getStatus() == APPLY_APPROVED) {
            ensureActivityCanApply(activity);
            tryIncreaseParticipants(activityId);
            apply.setStatus(APPLY_APPROVED);
        } else {
            apply.setStatus(APPLY_REJECTED);
        }
        activityApplyMapper.updateById(apply);
        return activityApplyMapper.selectById(applyId);
    }

    @Override
    public List<ActivityCategory> listCategories() {
        LambdaQueryWrapper<ActivityCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(ActivityCategory::getId);
        return activityCategoryMapper.selectList(wrapper);
    }

    @Override
    @Transactional
    public ActivityCategory createCategory(ActivityCategoryCreateRequest request) {
        if (request == null || !StringUtils.hasText(request.getName())) {
            throw new BusinessException(400, "category name is required");
        }

        ActivityCategory category = new ActivityCategory();
        category.setName(request.getName().trim());
        activityCategoryMapper.insert(category);
        return category;
    }

    private void syncEndedActivities() {
        activityMapper.syncEndedActivities(LocalDateTime.now());
    }

    private Activity getActivityOrThrow(Long id) {
        Activity activity = activityMapper.selectById(id);
        if (activity == null) {
            throw new BusinessException(404, "activity not found");
        }
        return activity;
    }

    private void refreshEndedStatus(Activity activity) {
        if (activity.getStatus() != null
                && activity.getStatus() == ACTIVITY_ENROLLING
                && isEnded(activity.getEndTime())) {
            activity.setStatus(ACTIVITY_ENDED);
            activityMapper.updateById(activity);
        }
    }

    private void ensureActivityCanApply(Activity activity) {
        if (activity.getStatus() == null || activity.getStatus() != ACTIVITY_ENROLLING) {
            throw new BusinessException(409, "activity is not open for apply");
        }
        if (isEnded(activity.getEndTime())) {
            activity.setStatus(ACTIVITY_ENDED);
            activityMapper.updateById(activity);
            throw new BusinessException(409, "activity has ended");
        }
    }

    private void tryIncreaseParticipants(Long activityId) {
        int updated = activityMapper.increaseParticipantsIfAvailable(activityId);
        if (updated <= 0) {
            throw new BusinessException(409, "apply limit reached");
        }
    }

    private boolean isApplyAuditRequired(Activity activity) {
        return activity.getApplyAuditRequired() != null && activity.getApplyAuditRequired() == FLAG_TRUE;
    }

    private boolean isEnded(LocalDateTime endTime) {
        return endTime != null && !endTime.isAfter(LocalDateTime.now());
    }

    private void validatePublishRole(String publisherRole) {
        if (!StringUtils.hasText(publisherRole)) {
            return;
        }
        String role = publisherRole.trim().toUpperCase();
        if (!PUBLISH_ALLOWED_ROLES.contains(role)) {
            throw new BusinessException(403, "only CLUB_ADMIN or ADMIN can publish activity");
        }
    }

    private int normalizePage(int page) {
        return page <= 0 ? 1 : page;
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return 10;
        }
        return Math.min(size, 100);
    }

    private Map<String, Object> toPageData(Page<?> result) {
        Map<String, Object> response = new HashMap<>();
        response.put("records", result.getRecords());
        response.put("total", result.getTotal());
        response.put("pages", result.getPages());
        response.put("current", result.getCurrent());
        response.put("size", result.getSize());
        return response;
    }
}
