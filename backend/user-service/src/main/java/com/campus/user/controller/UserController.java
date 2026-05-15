package com.campus.user.controller;

import com.campus.common.api.ApiResponse;
import com.campus.user.dto.ChangePasswordRequest;
import com.campus.user.dto.LoginRequest;
import com.campus.user.dto.RegisterRequest;
import com.campus.user.dto.UpdateProfileRequest;
import com.campus.user.dto.AdminUserStatusRequest;
import com.campus.user.dto.SystemNotificationCreateRequest;
import com.campus.user.dto.UserSearchItem;
import com.campus.user.entity.UserEntity;
import com.campus.user.service.AuthService;
import com.campus.user.service.FollowService;
import com.campus.user.service.NotificationService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final AuthService authService;
    private final NotificationService notificationService;
    private final FollowService followService;

    public UserController(AuthService authService,
                          NotificationService notificationService,
                          FollowService followService) {
        this.authService = authService;
        this.notificationService = notificationService;
        this.followService = followService;
    }

    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@RequestBody RegisterRequest request) {
        UserEntity user = authService.register(request);
        return ApiResponse.success(Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "userId", user.getUserId() == null ? "" : user.getUserId(),
                "userNo", user.getUserNo() == null ? "" : user.getUserNo(),
                "status", "registered"
        ));
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(@RequestHeader("Authorization") String authorization) {
        UserEntity user = authService.getCurrentUser(authorization);
        return ApiResponse.success(toUserInfo(user));
    }

    @PutMapping("/me")
    public ApiResponse<Map<String, Object>> updateMe(@RequestHeader("Authorization") String authorization,
                                                     @RequestBody UpdateProfileRequest request) {
        UserEntity user = authService.updateCurrentUser(authorization, request);
        return ApiResponse.success(toUserInfo(user));
    }

    @PutMapping("/password")
    public ApiResponse<Map<String, Object>> changePassword(@RequestHeader("Authorization") String authorization,
                                                           @RequestBody ChangePasswordRequest request) {
        authService.changePassword(authorization, request);
        return ApiResponse.success(Map.of("changed", true));
    }

    @PostMapping("/logout")
    public ApiResponse<Map<String, Object>> logout(@RequestHeader("Authorization") String authorization) {
        authService.logout(authorization);
        return ApiResponse.success(Map.of("loggedOut", true));
    }

    @GetMapping("/roles")
    public ApiResponse<Map<String, Object>> roles(@RequestHeader("Authorization") String authorization) {
        return ApiResponse.success(Map.of("roles", authService.getCurrentRoles(authorization)));
    }

    @GetMapping("/search")
    public ApiResponse<List<UserSearchItem>> search(@RequestHeader("Authorization") String authorization,
                                                    @RequestParam(required = false) String keyword,
                                                    @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(authService.searchUsers(authorization, keyword, size));
    }

    @GetMapping("/{targetUserId}/space")
    public ApiResponse<Map<String, Object>> userSpace(@RequestHeader("Authorization") String authorization,
                                                      @PathVariable Long targetUserId) {
        Map<String, Object> space = authService.getUserSpace(authorization, targetUserId);
        Map<String, Object> follow = followService.followSummary(authorization, targetUserId);
        Map<String, Object> result = new java.util.LinkedHashMap<>(space);
        result.put("followerCount", follow.get("followerCount"));
        result.put("followingCount", follow.get("followingCount"));
        result.put("followedByCurrentUser", follow.get("followedByCurrentUser"));
        return ApiResponse.success(result);
    }

    @PostMapping("/follows/{targetUserId}")
    public ApiResponse<Map<String, Object>> follow(@RequestHeader("Authorization") String authorization,
                                                   @PathVariable Long targetUserId) {
        return ApiResponse.success(followService.follow(authorization, targetUserId));
    }

    @DeleteMapping("/follows/{targetUserId}")
    public ApiResponse<Map<String, Object>> unfollow(@RequestHeader("Authorization") String authorization,
                                                     @PathVariable Long targetUserId) {
        return ApiResponse.success(followService.unfollow(authorization, targetUserId));
    }

    @GetMapping("/{targetUserId}/followers")
    public ApiResponse<Map<String, Object>> followers(@RequestHeader("Authorization") String authorization,
                                                      @PathVariable Long targetUserId,
                                                      @RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(followService.pageFollowers(authorization, targetUserId, page, size));
    }

    @GetMapping("/{targetUserId}/following")
    public ApiResponse<Map<String, Object>> following(@RequestHeader("Authorization") String authorization,
                                                      @PathVariable Long targetUserId,
                                                      @RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(followService.pageFollowing(authorization, targetUserId, page, size));
    }

    @GetMapping("/{targetUserId}/follow-summary")
    public ApiResponse<Map<String, Object>> followSummary(@RequestHeader("Authorization") String authorization,
                                                          @PathVariable Long targetUserId) {
        return ApiResponse.success(followService.followSummary(authorization, targetUserId));
    }

    @GetMapping("/notifications")
    public ApiResponse<Map<String, Object>> notifications(@RequestHeader("Authorization") String authorization,
                                                          @RequestParam(required = false) Integer type,
                                                          @RequestParam(required = false) Integer isRead,
                                                          @RequestParam(defaultValue = "1") int page,
                                                          @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(notificationService.pageNotifications(authorization, type, isRead, page, size));
    }

    @GetMapping("/notifications/unread-count")
    public ApiResponse<Map<String, Object>> notificationUnread(@RequestHeader("Authorization") String authorization) {
        return ApiResponse.success(notificationService.unreadCount(authorization));
    }

    @PatchMapping("/notifications/{id}/read")
    public ApiResponse<Map<String, Object>> notificationRead(@RequestHeader("Authorization") String authorization,
                                                             @PathVariable("id") Long id) {
        return ApiResponse.success(notificationService.markRead(authorization, id));
    }

    @PatchMapping("/notifications/read-all")
    public ApiResponse<Map<String, Object>> notificationReadAll(@RequestHeader("Authorization") String authorization) {
        return ApiResponse.success(notificationService.markAllRead(authorization));
    }

    @PostMapping("/admin/notifications/system")
    public ApiResponse<Map<String, Object>> publishSystemNotification(@RequestHeader("Authorization") String authorization,
                                                                      @RequestBody SystemNotificationCreateRequest request) {
        return ApiResponse.success(notificationService.publishSystemNotification(authorization, request));
    }

    @GetMapping("/admin/overview")
    public ApiResponse<Map<String, Object>> adminOverview(@RequestHeader("Authorization") String authorization) {
        return ApiResponse.success(authService.adminOverview(authorization));
    }

    @GetMapping("/admin/page")
    public ApiResponse<Map<String, Object>> adminPage(@RequestHeader("Authorization") String authorization,
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(required = false) Integer status,
                                                      @RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(authService.adminPage(authorization, keyword, status, page, size));
    }

    @PutMapping("/admin/{id}/status")
    public ApiResponse<Map<String, Object>> adminUpdateStatus(@RequestHeader("Authorization") String authorization,
                                                              @PathVariable("id") Long id,
                                                              @RequestBody AdminUserStatusRequest request) {
        UserEntity user = authService.adminUpdateStatus(authorization, id, request == null ? null : request.getStatus());
        return ApiResponse.success(toUserInfo(user));
    }

    @GetMapping("/health/startup")
    public ApiResponse<Map<String, Object>> startupHealth() {
        return ApiResponse.success(Map.of(
                "service", "user-service",
                "up", true,
                "checkedAt", LocalDateTime.now().toString()
        ));
    }

    private Map<String, Object> toUserInfo(UserEntity user) {
        return Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "userId", user.getUserId() == null ? "" : user.getUserId(),
                "userNo", user.getUserNo() == null ? "" : user.getUserNo(),
                "email", user.getEmail() == null ? "" : user.getEmail(),
                "phone", user.getPhone() == null ? "" : user.getPhone(),
                "avatarUrl", user.getAvatarUrl() == null ? "" : user.getAvatarUrl(),
                "bio", user.getBio() == null ? "" : user.getBio(),
                "homepageCover", user.getHomepageCover() == null ? "" : user.getHomepageCover()
        );
    }
}
