package com.campus.user.controller;

import com.campus.common.api.ApiResponse;
import com.campus.user.dto.ChangePasswordRequest;
import com.campus.user.dto.LoginRequest;
import com.campus.user.dto.RegisterRequest;
import com.campus.user.dto.UpdateProfileRequest;
import com.campus.user.dto.AdminUserStatusRequest;
import com.campus.user.dto.UserSearchItem;
import com.campus.user.entity.UserEntity;
import com.campus.user.service.AuthService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
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
        authService.getCurrentUser(authorization);
        return ApiResponse.success(Map.of(
                "roles", new String[]{"USER", "AUDITOR", "ADMIN"}
        ));
    }

    @GetMapping("/search")
    public ApiResponse<List<UserSearchItem>> search(@RequestHeader("Authorization") String authorization,
                                                    @RequestParam(required = false) String keyword,
                                                    @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(authService.searchUsers(authorization, keyword, size));
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
                "phone", user.getPhone() == null ? "" : user.getPhone()
        );
    }
}
