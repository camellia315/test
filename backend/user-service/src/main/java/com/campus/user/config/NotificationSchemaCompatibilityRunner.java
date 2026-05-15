package com.campus.user.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class NotificationSchemaCompatibilityRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(NotificationSchemaCompatibilityRunner.class);

    private final JdbcTemplate jdbcTemplate;

    public NotificationSchemaCompatibilityRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureTable();
        ensureColumns();
        ensureIndex();
    }

    private void ensureTable() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS message_notification (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "user_id BIGINT NOT NULL," +
                "type TINYINT NOT NULL DEFAULT 1 COMMENT '消息类型：1-系统 2-交易 3-活动 4-评论 5-聊天'," +
                "title VARCHAR(100)," +
                "content VARCHAR(500)," +
                "link_url VARCHAR(200)," +
                "is_read TINYINT DEFAULT 0," +
                "create_time DATETIME DEFAULT CURRENT_TIMESTAMP" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    }

    private void ensureColumns() {
        ensureColumn("message_notification", "type",
                "ALTER TABLE message_notification ADD COLUMN type TINYINT NOT NULL DEFAULT 1 COMMENT '消息类型：1-系统 2-交易 3-活动 4-评论 5-聊天'");
        ensureColumn("message_notification", "link_url",
                "ALTER TABLE message_notification ADD COLUMN link_url VARCHAR(200)");
        ensureColumn("message_notification", "is_read",
                "ALTER TABLE message_notification ADD COLUMN is_read TINYINT DEFAULT 0");
        ensureColumn("message_notification", "create_time",
                "ALTER TABLE message_notification ADD COLUMN create_time DATETIME DEFAULT CURRENT_TIMESTAMP");

        if (hasColumn("message_notification", "created_at")) {
            jdbcTemplate.execute("UPDATE message_notification SET create_time = created_at WHERE create_time IS NULL");
        }
    }

    private void ensureIndex() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.STATISTICS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'message_notification' AND INDEX_NAME = 'idx_user_read'",
                Integer.class
        );
        if (count != null && count > 0) {
            return;
        }
        try {
            jdbcTemplate.execute("CREATE INDEX idx_user_read ON message_notification(user_id, is_read)");
        } catch (Exception ex) {
            log.warn("create idx_user_read failed: {}", ex.getMessage());
        }
    }

    private void ensureColumn(String tableName, String columnName, String ddl) {
        if (hasColumn(tableName, columnName)) {
            return;
        }
        try {
            jdbcTemplate.execute(ddl);
        } catch (Exception ex) {
            log.warn("ensure column {}.{} failed: {}", tableName, columnName, ex.getMessage());
        }
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
}
