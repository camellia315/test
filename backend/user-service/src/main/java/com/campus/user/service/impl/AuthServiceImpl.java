package com.campus.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.common.exception.BusinessException;
import com.campus.user.dto.ChangePasswordRequest;
import com.campus.user.dto.LoginRequest;
import com.campus.user.dto.RegisterRequest;
import com.campus.user.dto.UpdateProfileRequest;
import com.campus.user.dto.UserSearchItem;
import com.campus.user.entity.UserEntity;
import com.campus.user.mapper.UserMapper;
import com.campus.user.service.AuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Service
public class AuthServiceImpl implements AuthService {

    private static final String TOKEN_PREFIX = "auth:token:";
    private static final Pattern CUSTOM_USER_ID_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_-]{3,31}$");

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate stringRedisTemplate;
    private final ConcurrentHashMap<String, LocalTokenSession> localTokenStore = new ConcurrentHashMap<>();

    @Value("${auth.token-expire-seconds:7200}")
    private long tokenExpireSeconds;

    public AuthServiceImpl(UserMapper userMapper,
                           PasswordEncoder passwordEncoder,
                           StringRedisTemplate stringRedisTemplate) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public UserEntity register(RegisterRequest request) {
        if (!StringUtils.hasText(request.getUsername()) || request.getUsername().trim().length() < 3) {
            throw new BusinessException(400, "用户名至少3个字符");
        }
        if (!StringUtils.hasText(request.getPassword()) || request.getPassword().length() < 6) {
            throw new BusinessException(400, "密码至少6位");
        }

        String username = request.getUsername().trim();
        UserEntity existed = findByUsername(username);
        if (existed != null) {
            throw new BusinessException(409, "用户名已存在");
        }

        String customUserId = normalizeCustomUserId(request.getUserId());
        if (customUserId != null && existsByCustomUserId(customUserId, null)) {
            throw new BusinessException(409, "用户ID已存在");
        }

        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setUserId(customUserId);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);
        ensureIdentityFields(user);
        return userMapper.selectById(user.getId());
    }

    @Override
    public Map<String, Object> login(LoginRequest request) {
        if (!StringUtils.hasText(request.getUsername()) || !StringUtils.hasText(request.getPassword())) {
            throw new BusinessException(400, "用户名和密码不能为空");
        }

        UserEntity user = findByAccount(request.getUsername().trim());
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(403, "账号已被禁用");
        }
        user = ensureIdentityFields(user);

        String token = UUID.randomUUID().toString().replace("-", "");
        saveToken(token, user.getId());

        Map<String, Object> userInfo = Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "userId", safe(user.getUserId()),
                "userNo", safe(user.getUserNo()),
                "email", user.getEmail() == null ? "" : user.getEmail(),
                "phone", user.getPhone() == null ? "" : user.getPhone()
        );
        return Map.of(
                "token", token,
                "tokenType", "Bearer",
                "expireInSeconds", tokenExpireSeconds,
                "user", userInfo
        );
    }

    @Override
    public UserEntity getCurrentUser(String authorizationHeader) {
        String token = extractToken(authorizationHeader);
        Long userId = getUserIdByToken(token);
        if (userId == null) {
            throw new BusinessException(401, "登录已过期，请重新登录");
        }
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return ensureIdentityFields(user);
    }

    @Override
    public UserEntity updateCurrentUser(String authorizationHeader, UpdateProfileRequest request) {
        UserEntity user = getCurrentUser(authorizationHeader);
        String customUserId = normalizeCustomUserId(request == null ? null : request.getUserId());
        String email = request == null ? null : normalize(request.getEmail());
        String phone = request == null ? null : normalize(request.getPhone());

        if (customUserId != null && !customUserId.equals(user.getUserId())
                && existsByCustomUserId(customUserId, user.getId())) {
            throw new BusinessException(409, "用户ID已存在");
        }
        if (email != null && email.length() > 128) {
            throw new BusinessException(400, "邮箱长度不能超过128个字符");
        }
        if (phone != null && phone.length() > 32) {
            throw new BusinessException(400, "手机号长度不能超过32个字符");
        }

        if (customUserId != null) {
            user.setUserId(customUserId);
        }
        user.setEmail(email);
        user.setPhone(phone);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return userMapper.selectById(user.getId());
    }

    @Override
    public void changePassword(String authorizationHeader, ChangePasswordRequest request) {
        if (request == null || !StringUtils.hasText(request.getOldPassword()) || !StringUtils.hasText(request.getNewPassword())) {
            throw new BusinessException(400, "旧密码和新密码不能为空");
        }
        if (request.getNewPassword().length() < 6) {
            throw new BusinessException(400, "新密码至少6位");
        }

        UserEntity user = getCurrentUser(authorizationHeader);
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new BusinessException(400, "旧密码错误");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    @Override
    public void logout(String authorizationHeader) {
        String token = extractToken(authorizationHeader);
        localTokenStore.remove(token);
        try {
            stringRedisTemplate.delete(TOKEN_PREFIX + token);
        } catch (RedisConnectionFailureException ignored) {
        }
    }

    @Override
    public List<UserSearchItem> searchUsers(String authorizationHeader, String keyword, int size) {
        UserEntity current = getCurrentUser(authorizationHeader);
        int limit = Math.max(1, Math.min(size, 50));
        String normalized = keyword == null ? "" : keyword.trim();

        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.ne(UserEntity::getId, current.getId());
        wrapper.eq(UserEntity::getStatus, 1);

        if (StringUtils.hasText(normalized)) {
            Long keywordId = parseLong(normalized);
            wrapper.and(w -> {
                w.like(UserEntity::getUserId, normalized)
                        .or()
                        .like(UserEntity::getUsername, normalized)
                        .or()
                        .like(UserEntity::getUserNo, normalized);
                if (keywordId != null) {
                    w.or().eq(UserEntity::getId, keywordId);
                }
            });
        }

        wrapper.orderByDesc(UserEntity::getUpdatedAt).orderByDesc(UserEntity::getId).last("LIMIT " + limit);
        List<UserEntity> users = userMapper.selectList(wrapper);
        List<UserSearchItem> result = new ArrayList<>(users.size());
        for (UserEntity user : users) {
            UserEntity normalizedUser = ensureIdentityFields(user);
            UserSearchItem item = new UserSearchItem();
            item.setId(normalizedUser.getId());
            item.setUsername(normalizedUser.getUsername());
            item.setUserId(normalizedUser.getUserId());
            item.setUserNo(normalizedUser.getUserNo());
            result.add(item);
        }
        return result;
    }

    @Override
    public Map<String, Object> adminOverview(String authorizationHeader) {
        getCurrentUser(authorizationHeader);

        Long totalUsers = userMapper.selectCount(new LambdaQueryWrapper<>());
        Long activeUsers = userMapper.selectCount(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getStatus, 1));
        Long disabledUsers = userMapper.selectCount(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getStatus, 0));

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime sevenDaysStart = LocalDate.now().minusDays(6).atStartOfDay();

        Long todayNewUsers = userMapper.selectCount(new LambdaQueryWrapper<UserEntity>()
                .ge(UserEntity::getCreatedAt, todayStart));
        Long sevenDaysNewUsers = userMapper.selectCount(new LambdaQueryWrapper<UserEntity>()
                .ge(UserEntity::getCreatedAt, sevenDaysStart));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalUsers", safeLong(totalUsers));
        result.put("activeUsers", safeLong(activeUsers));
        result.put("disabledUsers", safeLong(disabledUsers));
        result.put("todayNewUsers", safeLong(todayNewUsers));
        result.put("sevenDaysNewUsers", safeLong(sevenDaysNewUsers));
        result.put("checkedAt", LocalDateTime.now().toString());
        return result;
    }

    @Override
    public Map<String, Object> adminPage(String authorizationHeader,
                                         String keyword,
                                         Integer status,
                                         int page,
                                         int size) {
        getCurrentUser(authorizationHeader);

        int current = Math.max(1, page);
        int limit = Math.max(1, Math.min(size, 100));
        int offset = (current - 1) * limit;

        LambdaQueryWrapper<UserEntity> countWrapper = buildAdminFilterWrapper(keyword, status);
        Long total = userMapper.selectCount(countWrapper);

        LambdaQueryWrapper<UserEntity> listWrapper = buildAdminFilterWrapper(keyword, status);
        listWrapper.orderByDesc(UserEntity::getUpdatedAt)
                .orderByDesc(UserEntity::getId)
                .last("LIMIT " + offset + "," + limit);
        List<UserEntity> users = userMapper.selectList(listWrapper);

        List<Map<String, Object>> records = new ArrayList<>(users.size());
        for (UserEntity user : users) {
            records.add(toAdminUserInfo(ensureIdentityFields(user)));
        }

        long totalValue = safeLong(total);
        long pages = totalValue == 0 ? 0 : (totalValue + limit - 1) / limit;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", totalValue);
        result.put("current", current);
        result.put("size", limit);
        result.put("pages", pages);
        return result;
    }

    @Override
    public UserEntity adminUpdateStatus(String authorizationHeader, Long targetUserId, Integer status) {
        UserEntity currentUser = getCurrentUser(authorizationHeader);
        if (targetUserId == null || targetUserId <= 0) {
            throw new BusinessException(400, "用户ID不合法");
        }
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(400, "状态仅支持 0-禁用 或 1-启用");
        }

        UserEntity targetUser = userMapper.selectById(targetUserId);
        if (targetUser == null) {
            throw new BusinessException(404, "目标用户不存在");
        }
        if (status == 0 && targetUserId.equals(currentUser.getId())) {
            throw new BusinessException(400, "不能禁用当前登录账号");
        }
        if (targetUser.getStatus() != null && targetUser.getStatus().equals(status)) {
            return ensureIdentityFields(targetUser);
        }

        targetUser.setStatus(status);
        targetUser.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(targetUser);
        return ensureIdentityFields(userMapper.selectById(targetUserId));
    }

    private UserEntity findByUsername(String username) {
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserEntity::getUsername, username);
        wrapper.last("LIMIT 1");
        return userMapper.selectOne(wrapper);
    }

    private LambdaQueryWrapper<UserEntity> buildAdminFilterWrapper(String keyword, Integer status) {
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<>();
        String normalizedKeyword = normalize(keyword);
        if (status != null) {
            wrapper.eq(UserEntity::getStatus, status);
        }
        if (StringUtils.hasText(normalizedKeyword)) {
            Long keywordId = parseLong(normalizedKeyword);
            wrapper.and(w -> {
                w.like(UserEntity::getUsername, normalizedKeyword)
                        .or()
                        .like(UserEntity::getUserId, normalizedKeyword)
                        .or()
                        .like(UserEntity::getUserNo, normalizedKeyword);
                if (keywordId != null) {
                    w.or().eq(UserEntity::getId, keywordId);
                }
            });
        }
        return wrapper;
    }

    private Map<String, Object> toAdminUserInfo(UserEntity user) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", user.getId());
        item.put("username", safe(user.getUsername()));
        item.put("userId", safe(user.getUserId()));
        item.put("userNo", safe(user.getUserNo()));
        item.put("email", safe(user.getEmail()));
        item.put("phone", safe(user.getPhone()));
        item.put("status", user.getStatus() == null ? 1 : user.getStatus());
        item.put("createdAt", user.getCreatedAt() == null ? "" : user.getCreatedAt().toString());
        item.put("updatedAt", user.getUpdatedAt() == null ? "" : user.getUpdatedAt().toString());
        return item;
    }

    private UserEntity findByAccount(String account) {
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.eq(UserEntity::getUsername, account)
                .or()
                .eq(UserEntity::getUserId, account)
                .or()
                .eq(UserEntity::getUserNo, account));
        wrapper.last("LIMIT 1");
        return userMapper.selectOne(wrapper);
    }

    private boolean existsByCustomUserId(String userId, Long excludeId) {
        if (!StringUtils.hasText(userId)) {
            return false;
        }
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserEntity::getUserId, userId);
        if (excludeId != null) {
            wrapper.ne(UserEntity::getId, excludeId);
        }
        wrapper.last("LIMIT 1");
        return userMapper.selectOne(wrapper) != null;
    }

    private String normalizeCustomUserId(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        String value = rawValue.trim();
        if (!CUSTOM_USER_ID_PATTERN.matcher(value).matches()) {
            throw new BusinessException(400, "用户ID格式不正确，仅支持字母开头，4-32位字母数字_-");
        }
        return value;
    }

    private UserEntity ensureIdentityFields(UserEntity user) {
        if (user == null || user.getId() == null) {
            return user;
        }
        boolean changed = false;
        if (!StringUtils.hasText(user.getUserNo())) {
            user.setUserNo(generateUserNo(user.getId()));
            changed = true;
        }
        if (!StringUtils.hasText(user.getUserId())) {
            user.setUserId(generateDefaultUserId(user.getUsername(), user.getId()));
            changed = true;
        }
        if (!changed) {
            return user;
        }
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return userMapper.selectById(user.getId());
    }

    private String generateUserNo(Long userId) {
        return "U" + String.format("%08d", userId);
    }

    private String generateDefaultUserId(String username, Long userId) {
        String normalizedUsername = normalizeByRule(username);
        if (StringUtils.hasText(normalizedUsername) && !existsByCustomUserId(normalizedUsername, userId)) {
            return normalizedUsername;
        }
        String fallback = "user" + userId;
        if (!existsByCustomUserId(fallback, userId)) {
            return fallback;
        }
        for (int i = 1; i <= 99; i++) {
            String candidate = fallback + "_" + i;
            if (!existsByCustomUserId(candidate, userId)) {
                return candidate;
            }
        }
        throw new BusinessException(500, "生成用户ID失败，请稍后重试");
    }

    private String normalizeByRule(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().replaceAll("[^A-Za-z0-9_-]", "");
        if (normalized.length() > 32) {
            normalized = normalized.substring(0, 32);
        }
        if (!CUSTOM_USER_ID_PATTERN.matcher(normalized).matches()) {
            return null;
        }
        return normalized;
    }

    private String extractToken(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader)) {
            throw new BusinessException(401, "缺少登录令牌");
        }
        String value = authorizationHeader.trim();
        if (value.startsWith("Bearer ")) {
            return value.substring(7).trim();
        }
        return value;
    }

    private void saveToken(String token, Long userId) {
        long expireAt = System.currentTimeMillis() + tokenExpireSeconds * 1000;
        localTokenStore.put(token, new LocalTokenSession(userId, expireAt));
        try {
            stringRedisTemplate.opsForValue()
                    .set(TOKEN_PREFIX + token, String.valueOf(userId), tokenExpireSeconds, TimeUnit.SECONDS);
        } catch (RedisConnectionFailureException ignored) {
        }
    }

    private Long getUserIdByToken(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        try {
            String value = stringRedisTemplate.opsForValue().get(TOKEN_PREFIX + token);
            if (StringUtils.hasText(value)) {
                return Long.parseLong(value);
            }
        } catch (RedisConnectionFailureException ignored) {
        }

        LocalTokenSession session = localTokenStore.get(token);
        if (session == null) {
            return null;
        }
        if (session.expireAt < System.currentTimeMillis()) {
            localTokenStore.remove(token);
            return null;
        }
        return session.userId;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private Long parseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private static class LocalTokenSession {
        private final Long userId;
        private final long expireAt;

        private LocalTokenSession(Long userId, long expireAt) {
            this.userId = userId;
            this.expireAt = expireAt;
        }
    }
}
