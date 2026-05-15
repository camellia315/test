package com.campus.lostfound.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class LostFoundSchemaCompatibilityRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LostFoundSchemaCompatibilityRunner.class);

    private final JdbcTemplate jdbcTemplate;

    public LostFoundSchemaCompatibilityRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureTables();
        ensureColumns();
        ensureIndexes();
        normalizeLegacyData();
    }

    private void ensureTables() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS lost_found (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "user_id BIGINT NOT NULL," +
                "category_id BIGINT," +
                "title VARCHAR(255) NOT NULL," +
                "description TEXT," +
                "image_url VARCHAR(512)," +
                "location_text VARCHAR(255)," +
                "item_type VARCHAR(16) NOT NULL," +
                "status VARCHAR(32) DEFAULT 'SEARCHING'," +
                "created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "recovered_at DATETIME NULL" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS lf_comment (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "lost_found_id BIGINT NOT NULL," +
                "user_id BIGINT NOT NULL," +
                "content VARCHAR(500) NOT NULL," +
                "created_at DATETIME DEFAULT CURRENT_TIMESTAMP" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS lost_found_audit (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "lost_found_id BIGINT NOT NULL," +
                "auditor_id BIGINT NOT NULL," +
                "status TINYINT COMMENT '1-通过 2-驳回'," +
                "reason VARCHAR(200)," +
                "audit_time DATETIME DEFAULT CURRENT_TIMESTAMP" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS lf_private_message (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "item_id BIGINT NOT NULL," +
                "from_user_id BIGINT NOT NULL," +
                "to_user_id BIGINT NOT NULL," +
                "content VARCHAR(1000) NOT NULL," +
                "msg_type TINYINT DEFAULT 1," +
                "is_read TINYINT DEFAULT 0," +
                "create_time DATETIME DEFAULT CURRENT_TIMESTAMP" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS lf_private_session (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "item_id BIGINT NOT NULL," +
                "user_id BIGINT NOT NULL," +
                "other_user_id BIGINT NOT NULL," +
                "last_message VARCHAR(1000)," +
                "unread_count INT DEFAULT 0," +
                "update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    }

    private void ensureColumns() {
        ensureColumn("lost_found", "status",
                "ALTER TABLE lost_found ADD COLUMN status VARCHAR(32) DEFAULT 'SEARCHING'");
        ensureColumn("lost_found", "recovered_at",
                "ALTER TABLE lost_found ADD COLUMN recovered_at DATETIME NULL");
        ensureColumn("lost_found_audit", "lost_found_id",
                "ALTER TABLE lost_found_audit ADD COLUMN lost_found_id BIGINT NOT NULL DEFAULT 0");
        ensureColumn("lost_found_audit", "auditor_id",
                "ALTER TABLE lost_found_audit ADD COLUMN auditor_id BIGINT NOT NULL DEFAULT 0");
        ensureColumn("lost_found_audit", "status",
                "ALTER TABLE lost_found_audit ADD COLUMN status TINYINT COMMENT '1-通过 2-驳回'");
        ensureColumn("lost_found_audit", "reason",
                "ALTER TABLE lost_found_audit ADD COLUMN reason VARCHAR(200)");
        ensureColumn("lost_found_audit", "audit_time",
                "ALTER TABLE lost_found_audit ADD COLUMN audit_time DATETIME DEFAULT CURRENT_TIMESTAMP");

        ensureColumn("lf_private_message", "item_id",
                "ALTER TABLE lf_private_message ADD COLUMN item_id BIGINT NOT NULL DEFAULT 0");
        ensureColumn("lf_private_message", "from_user_id",
                "ALTER TABLE lf_private_message ADD COLUMN from_user_id BIGINT NOT NULL DEFAULT 0");
        ensureColumn("lf_private_message", "to_user_id",
                "ALTER TABLE lf_private_message ADD COLUMN to_user_id BIGINT NOT NULL DEFAULT 0");
        ensureColumn("lf_private_message", "content",
                "ALTER TABLE lf_private_message ADD COLUMN content VARCHAR(1000) NOT NULL DEFAULT ''");
        ensureColumn("lf_private_message", "msg_type",
                "ALTER TABLE lf_private_message ADD COLUMN msg_type TINYINT DEFAULT 1");
        ensureColumn("lf_private_message", "is_read",
                "ALTER TABLE lf_private_message ADD COLUMN is_read TINYINT DEFAULT 0");
        ensureColumn("lf_private_message", "create_time",
                "ALTER TABLE lf_private_message ADD COLUMN create_time DATETIME DEFAULT CURRENT_TIMESTAMP");

        ensureColumn("lf_private_session", "item_id",
                "ALTER TABLE lf_private_session ADD COLUMN item_id BIGINT NOT NULL DEFAULT 0");
        ensureColumn("lf_private_session", "user_id",
                "ALTER TABLE lf_private_session ADD COLUMN user_id BIGINT NOT NULL DEFAULT 0");
        ensureColumn("lf_private_session", "other_user_id",
                "ALTER TABLE lf_private_session ADD COLUMN other_user_id BIGINT NOT NULL DEFAULT 0");
        ensureColumn("lf_private_session", "last_message",
                "ALTER TABLE lf_private_session ADD COLUMN last_message VARCHAR(1000)");
        ensureColumn("lf_private_session", "unread_count",
                "ALTER TABLE lf_private_session ADD COLUMN unread_count INT DEFAULT 0");
        ensureColumn("lf_private_session", "update_time",
                "ALTER TABLE lf_private_session ADD COLUMN update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
    }

    private void ensureIndexes() {
        ensureIndex("lf_private_message", "idx_lf_pm_pair_time",
                "CREATE INDEX idx_lf_pm_pair_time ON lf_private_message(item_id, from_user_id, to_user_id, create_time)");
        ensureIndex("lf_private_session", "uk_lf_ps_user_other_item",
                "CREATE UNIQUE INDEX uk_lf_ps_user_other_item ON lf_private_session(item_id, user_id, other_user_id)");
    }

    private void normalizeLegacyData() {
        if (hasColumn("lost_found", "recovered_at")) {
            jdbcTemplate.update(
                    "UPDATE lost_found SET recovered_at = created_at WHERE recovered_at IS NULL AND status IN ('FOUND', 'RETURNED')"
            );
        }
    }

    private void ensureColumn(String tableName, String columnName, String ddl) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.COLUMNS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class,
                tableName,
                columnName
        );
        if (count == null || count > 0) {
            return;
        }
        log.warn("Missing column {}.{}, applying compatibility DDL", tableName, columnName);
        jdbcTemplate.execute(ddl);
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
        log.warn("Missing index {} on {}, applying compatibility DDL", indexName, tableName);
        jdbcTemplate.execute(ddl);
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
