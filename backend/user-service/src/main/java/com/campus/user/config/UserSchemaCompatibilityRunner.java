package com.campus.user.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class UserSchemaCompatibilityRunner implements ApplicationRunner {

    private static final Pattern USER_ID_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_-]{3,31}$");
    private final JdbcTemplate jdbcTemplate;

    public UserSchemaCompatibilityRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!hasTable("user")) {
            return;
        }
        ensureColumn("user", "user_id", "ALTER TABLE `user` ADD COLUMN user_id VARCHAR(64)");
        ensureColumn("user", "user_no", "ALTER TABLE `user` ADD COLUMN user_no VARCHAR(32)");
        backfillIdentity();
        ensureIndex("user", "uk_user_user_id", "CREATE UNIQUE INDEX uk_user_user_id ON `user`(user_id)");
        ensureIndex("user", "uk_user_user_no", "CREATE UNIQUE INDEX uk_user_user_no ON `user`(user_no)");
    }

    private void backfillIdentity() {
        List<Map<String, Object>> users = jdbcTemplate.queryForList(
                "SELECT id, username, user_id, user_no FROM `user` ORDER BY id ASC");
        for (Map<String, Object> row : users) {
            Long id = toLong(row.get("id"));
            if (id == null) {
                continue;
            }

            String userNo = trim(row.get("user_no"));
            String userId = trim(row.get("user_id"));
            boolean changed = false;

            if (!StringUtils.hasText(userNo)) {
                userNo = generateUserNo(id);
                changed = true;
            }

            if (!StringUtils.hasText(userId)) {
                userId = generateUserId(trim(row.get("username")), id);
                changed = true;
            }

            if (!changed) {
                continue;
            }
            jdbcTemplate.update("UPDATE `user` SET user_id = ?, user_no = ? WHERE id = ?", userId, userNo, id);
        }
    }

    private String generateUserNo(Long id) {
        return "U" + String.format("%08d", id);
    }

    private String generateUserId(String username, Long id) {
        String normalized = normalizeUsername(username);
        if (StringUtils.hasText(normalized) && !existsUserId(normalized, id)) {
            return normalized;
        }
        String fallback = "user" + id;
        if (!existsUserId(fallback, id)) {
            return fallback;
        }
        for (int i = 1; i <= 99; i++) {
            String candidate = fallback + "_" + i;
            if (!existsUserId(candidate, id)) {
                return candidate;
            }
        }
        return "user" + id + "_x";
    }

    private String normalizeUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return null;
        }
        String normalized = username.trim().replaceAll("[^A-Za-z0-9_-]", "");
        if (normalized.length() > 32) {
            normalized = normalized.substring(0, 32);
        }
        if (!USER_ID_PATTERN.matcher(normalized).matches()) {
            return null;
        }
        return normalized;
    }

    private boolean existsUserId(String userId, Long currentId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM `user` WHERE user_id = ? AND id <> ?",
                Integer.class,
                userId,
                currentId == null ? -1L : currentId
        );
        return count != null && count > 0;
    }

    private void ensureColumn(String tableName, String columnName, String ddl) {
        if (hasColumn(tableName, columnName)) {
            return;
        }
        try {
            jdbcTemplate.execute(ddl);
        } catch (Exception ignored) {
        }
    }

    private void ensureIndex(String tableName, String indexName, String ddl) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.STATISTICS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?",
                Integer.class,
                tableName,
                indexName
        );
        if (count != null && count > 0) {
            return;
        }
        try {
            jdbcTemplate.execute(ddl);
        } catch (Exception ignored) {
        }
    }

    private boolean hasTable(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.TABLES " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                Integer.class,
                tableName
        );
        return count != null && count > 0;
    }

    private boolean hasColumn(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.COLUMNS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class,
                tableName,
                columnName
        );
        return count != null && count > 0;
    }

    private String trim(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
