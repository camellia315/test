package com.campus.user.service;

import com.campus.user.dto.LoginRequest;
import com.campus.user.dto.RegisterRequest;
import com.campus.user.dto.ChangePasswordRequest;
import com.campus.user.dto.UpdateProfileRequest;
import com.campus.user.dto.UserSearchItem;
import com.campus.user.entity.UserEntity;

import java.util.List;
import java.util.Map;

public interface AuthService {
    UserEntity register(RegisterRequest request);

    Map<String, Object> login(LoginRequest request);

    UserEntity getCurrentUser(String authorizationHeader);

    UserEntity updateCurrentUser(String authorizationHeader, UpdateProfileRequest request);

    void changePassword(String authorizationHeader, ChangePasswordRequest request);

    void logout(String authorizationHeader);

    List<UserSearchItem> searchUsers(String authorizationHeader, String keyword, int size);

    List<String> getCurrentRoles(String authorizationHeader);

    Map<String, Object> adminOverview(String authorizationHeader);

    Map<String, Object> adminPage(String authorizationHeader, String keyword, Integer status, int page, int size);

    UserEntity adminUpdateStatus(String authorizationHeader, Long targetUserId, Integer status);

    Map<String, Object> getUserSpace(String authorizationHeader, Long targetUserId);
}
