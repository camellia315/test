USE campus_platform;

-- Dev migration for activity module v2.
-- This script recreates activity tables and will clear old activity data.

DROP TABLE IF EXISTS activity_audit;
DROP TABLE IF EXISTS activity_apply;
DROP TABLE IF EXISTS activity;
DROP TABLE IF EXISTS activity_category;

CREATE TABLE activity_category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL
);

CREATE TABLE activity (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(100) NOT NULL COMMENT '活动标题',
    category_id INT COMMENT '分类ID',
    cover_image VARCHAR(500) COMMENT '封面图',
    description TEXT COMMENT '活动描述',
    location VARCHAR(200) COMMENT '活动地点',
    start_time DATETIME COMMENT '开始时间',
    end_time DATETIME COMMENT '结束时间',
    max_participants INT DEFAULT 0 COMMENT '最大参与人数',
    current_participants INT DEFAULT 0 COMMENT '当前参与人数',
    status TINYINT DEFAULT 0 COMMENT '状态：0-待审核 1-报名中 2-已结束 3-已驳回',
    apply_audit_required TINYINT DEFAULT 0 COMMENT '报名是否需要审核：0-否 1-是',
    club_id BIGINT COMMENT '社团ID',
    user_id BIGINT NOT NULL COMMENT '发布者ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE TABLE activity_apply (
    id BIGINT NOT NULL AUTO_INCREMENT,
    activity_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    apply_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    status TINYINT DEFAULT 0 COMMENT '0-待审核 1-已通过 2-已拒绝 3-已取消',
    PRIMARY KEY (id),
    UNIQUE KEY uk_activity_user (activity_id, user_id)
);

CREATE TABLE activity_audit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    activity_id BIGINT NOT NULL,
    auditor_id BIGINT NOT NULL COMMENT '审核人ID',
    status TINYINT COMMENT '1-通过 2-驳回',
    reason VARCHAR(200) COMMENT '驳回原因',
    audit_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

INSERT INTO activity_category (name) VALUES ('讲座'), ('比赛'), ('文艺'), ('公益'), ('体育');
