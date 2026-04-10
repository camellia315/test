package com.campus.activity.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ActivitySchemaCompatibilityRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ActivitySchemaCompatibilityRunner.class);

    private final JdbcTemplate jdbcTemplate;

    public ActivitySchemaCompatibilityRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureTables();
        ensureColumns();
        ensureIndexes();
    }

    private void ensureTables() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS activity_category (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "name VARCHAR(64) NOT NULL" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS activity (" +
                "id BIGINT NOT NULL AUTO_INCREMENT," +
                "title VARCHAR(100) NOT NULL COMMENT '活动标题'," +
                "category_id INT COMMENT '分类ID'," +
                "cover_image VARCHAR(500) COMMENT '封面图'," +
                "description TEXT COMMENT '活动描述'," +
                "location VARCHAR(200) COMMENT '活动地点'," +
                "start_time DATETIME COMMENT '开始时间'," +
                "end_time DATETIME COMMENT '结束时间'," +
                "max_participants INT DEFAULT 0 COMMENT '最大参与人数'," +
                "current_participants INT DEFAULT 0 COMMENT '当前参与人数'," +
                "status TINYINT DEFAULT 0 COMMENT '状态：0-待审核 1-报名中 2-已结束 3-已驳回'," +
                "apply_audit_required TINYINT DEFAULT 0 COMMENT '报名是否需要审核：0-否 1-是'," +
                "club_id BIGINT COMMENT '社团ID'," +
                "user_id BIGINT NOT NULL DEFAULT 0 COMMENT '发布者ID'," +
                "create_time DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "PRIMARY KEY (id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动表'");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS activity_apply (" +
                "id BIGINT NOT NULL AUTO_INCREMENT," +
                "activity_id BIGINT NOT NULL," +
                "user_id BIGINT NOT NULL," +
                "apply_time DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "status TINYINT DEFAULT 0 COMMENT '0-待审核 1-已通过 2-已拒绝 3-已取消'," +
                "PRIMARY KEY (id)," +
                "UNIQUE KEY uk_activity_user (activity_id, user_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS activity_audit (" +
                "id BIGINT NOT NULL AUTO_INCREMENT," +
                "activity_id BIGINT NOT NULL," +
                "auditor_id BIGINT NOT NULL COMMENT '审核人ID'," +
                "status TINYINT COMMENT '1-通过 2-驳回'," +
                "reason VARCHAR(200) COMMENT '驳回原因'," +
                "audit_time DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "PRIMARY KEY (id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    }

    private void ensureColumns() {
        ensureColumn("activity", "title",
                "ALTER TABLE activity ADD COLUMN title VARCHAR(100) NOT NULL DEFAULT '' COMMENT '活动标题'");
        ensureColumn("activity", "category_id",
                "ALTER TABLE activity ADD COLUMN category_id INT COMMENT '分类ID'");
        ensureColumn("activity", "cover_image",
                "ALTER TABLE activity ADD COLUMN cover_image VARCHAR(500) COMMENT '封面图'");
        ensureColumn("activity", "description",
                "ALTER TABLE activity ADD COLUMN description TEXT COMMENT '活动描述'");
        ensureColumn("activity", "location",
                "ALTER TABLE activity ADD COLUMN location VARCHAR(200) COMMENT '活动地点'");
        ensureColumn("activity", "start_time",
                "ALTER TABLE activity ADD COLUMN start_time DATETIME COMMENT '开始时间'");
        ensureColumn("activity", "end_time",
                "ALTER TABLE activity ADD COLUMN end_time DATETIME COMMENT '结束时间'");
        ensureColumn("activity", "max_participants",
                "ALTER TABLE activity ADD COLUMN max_participants INT DEFAULT 0 COMMENT '最大参与人数'");
        ensureColumn("activity", "current_participants",
                "ALTER TABLE activity ADD COLUMN current_participants INT DEFAULT 0 COMMENT '当前参与人数'");
        ensureColumn("activity", "status",
                "ALTER TABLE activity ADD COLUMN status TINYINT DEFAULT 0 COMMENT '状态：0-待审核 1-报名中 2-已结束 3-已驳回'");
        ensureColumn("activity", "apply_audit_required",
                "ALTER TABLE activity ADD COLUMN apply_audit_required TINYINT DEFAULT 0 COMMENT '报名是否需要审核：0-否 1-是'");
        ensureColumn("activity", "club_id",
                "ALTER TABLE activity ADD COLUMN club_id BIGINT COMMENT '社团ID'");
        ensureColumn("activity", "user_id",
                "ALTER TABLE activity ADD COLUMN user_id BIGINT NOT NULL DEFAULT 0 COMMENT '发布者ID'");
        ensureColumn("activity", "create_time",
                "ALTER TABLE activity ADD COLUMN create_time DATETIME DEFAULT CURRENT_TIMESTAMP");

        ensureColumn("activity_apply", "activity_id",
                "ALTER TABLE activity_apply ADD COLUMN activity_id BIGINT NOT NULL DEFAULT 0");
        ensureColumn("activity_apply", "user_id",
                "ALTER TABLE activity_apply ADD COLUMN user_id BIGINT NOT NULL DEFAULT 0");
        ensureColumn("activity_apply", "apply_time",
                "ALTER TABLE activity_apply ADD COLUMN apply_time DATETIME DEFAULT CURRENT_TIMESTAMP");
        ensureColumn("activity_apply", "status",
                "ALTER TABLE activity_apply ADD COLUMN status TINYINT DEFAULT 0 COMMENT '0-待审核 1-已通过 2-已拒绝 3-已取消'");

        ensureColumn("activity_audit", "activity_id",
                "ALTER TABLE activity_audit ADD COLUMN activity_id BIGINT NOT NULL DEFAULT 0");
        ensureColumn("activity_audit", "auditor_id",
                "ALTER TABLE activity_audit ADD COLUMN auditor_id BIGINT NOT NULL DEFAULT 0 COMMENT '审核人ID'");
        ensureColumn("activity_audit", "status",
                "ALTER TABLE activity_audit ADD COLUMN status TINYINT COMMENT '1-通过 2-驳回'");
        ensureColumn("activity_audit", "reason",
                "ALTER TABLE activity_audit ADD COLUMN reason VARCHAR(200) COMMENT '驳回原因'");
        ensureColumn("activity_audit", "audit_time",
                "ALTER TABLE activity_audit ADD COLUMN audit_time DATETIME DEFAULT CURRENT_TIMESTAMP");
    }

    private void ensureIndexes() {
        ensureIndex("activity_apply", "uk_activity_user",
                "CREATE UNIQUE INDEX uk_activity_user ON activity_apply(activity_id, user_id)");
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
        if (count == null || count > 0) {
            return;
        }
        log.warn("Missing index {} on {}, applying compatibility DDL", indexName, tableName);
        jdbcTemplate.execute(ddl);
    }
}

