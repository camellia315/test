CREATE DATABASE IF NOT EXISTS campus_platform DEFAULT CHARACTER SET utf8mb4;
USE campus_platform;

CREATE TABLE IF NOT EXISTS user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL UNIQUE,
    user_id VARCHAR(64) UNIQUE,
    user_no VARCHAR(32) UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(128),
    phone VARCHAR(32),
    avatar_url VARCHAR(512),
    bio VARCHAR(512),
    homepage_cover VARCHAR(512),
    status TINYINT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_code VARCHAR(64) NOT NULL UNIQUE,
    role_name VARCHAR(64) NOT NULL
);

CREATE TABLE IF NOT EXISTS permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    permission_code VARCHAR(128) NOT NULL UNIQUE,
    permission_name VARCHAR(128) NOT NULL
);

CREATE TABLE IF NOT EXISTS user_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    UNIQUE KEY uk_user_role (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS role_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    UNIQUE KEY uk_role_permission (role_id, permission_id)
);

CREATE TABLE IF NOT EXISTS user_profile (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL UNIQUE,
    avatar_url VARCHAR(512),
    nickname VARCHAR(64),
    bio VARCHAR(512)
);

CREATE TABLE IF NOT EXISTS user_points (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    points INT DEFAULT 0,
    reason VARCHAR(255),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_favorites (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id BIGINT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_follow (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    follower_id BIGINT NOT NULL,
    followee_id BIGINT NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_follow_relation (follower_id, followee_id)
);

CREATE TABLE IF NOT EXISTS lf_category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL
);

CREATE TABLE IF NOT EXISTS lost_found (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    category_id BIGINT,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    image_url VARCHAR(512),
    location_text VARCHAR(255),
    item_type VARCHAR(16) NOT NULL,
    status VARCHAR(32) DEFAULT 'SEARCHING',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    recovered_at DATETIME NULL
);

CREATE TABLE IF NOT EXISTS lf_comment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    lost_found_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    content VARCHAR(500) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS lf_private_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    item_id BIGINT NOT NULL,
    from_user_id BIGINT NOT NULL,
    to_user_id BIGINT NOT NULL,
    content VARCHAR(1000) NOT NULL,
    msg_type TINYINT DEFAULT 1,
    is_read TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS lf_private_session (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    item_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    other_user_id BIGINT NOT NULL,
    last_message VARCHAR(1000),
    unread_count INT DEFAULT 0,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_lf_ps_user_other_item (item_id, user_id, other_user_id)
);

CREATE TABLE IF NOT EXISTS lost_found_audit (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    lost_found_id BIGINT NOT NULL,
    auditor_id BIGINT NOT NULL,
    status TINYINT COMMENT '1-通过 2-驳回',
    reason VARCHAR(200),
    audit_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS activity_category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL
);

CREATE TABLE IF NOT EXISTS activity (
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
    organizer_id BIGINT NOT NULL COMMENT '组织者ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS activity_apply (
    id BIGINT NOT NULL AUTO_INCREMENT,
    activity_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    apply_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    status TINYINT DEFAULT 0 COMMENT '0-待审核 1-已通过 2-已拒绝 3-已取消',
    PRIMARY KEY (id),
    UNIQUE KEY uk_activity_user (activity_id, user_id)
);

CREATE TABLE IF NOT EXISTS activity_audit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    activity_id BIGINT NOT NULL,
    auditor_id BIGINT NOT NULL COMMENT '审核人ID',
    status TINYINT COMMENT '1-通过 2-驳回',
    reason VARCHAR(200) COMMENT '驳回原因',
    audit_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS notice (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    content TEXT,
    created_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS product_category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL
);

CREATE TABLE IF NOT EXISTS product (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    seller_id BIGINT NOT NULL,
    category_id BIGINT,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    price DECIMAL(10,2) NOT NULL,
    total_quantity INT DEFAULT 1,
    sold_quantity INT DEFAULT 0,
    status VARCHAR(32) DEFAULT 'ON_SALE',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS product_image (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    image_url VARCHAR(512) NOT NULL,
    sort_no INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    from_user_id BIGINT NOT NULL,
    to_user_id BIGINT NOT NULL,
    product_id BIGINT,
    content VARCHAR(1000) NOT NULL,
    sent_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS market_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(64) NOT NULL,
    product_id BIGINT NOT NULL,
    buyer_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    status TINYINT DEFAULT 0,
    remark VARCHAR(500),
    pay_status TINYINT DEFAULT 0,
    pay_time DATETIME,
    pay_channel VARCHAR(32),
    pay_order_no VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_order_no (order_no)
);

CREATE TABLE IF NOT EXISTS user_browse_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    category_id BIGINT,
    viewed_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS `order` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    buyer_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL,
    total_amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(32) DEFAULT 'CREATED',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS order_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    quantity INT DEFAULT 1
);

CREATE TABLE IF NOT EXISTS system_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    config_key VARCHAR(128) NOT NULL UNIQUE,
    config_value VARCHAR(1000) NOT NULL
);

CREATE TABLE IF NOT EXISTS operation_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    operator_id BIGINT,
    module_name VARCHAR(64),
    operation VARCHAR(128),
    details TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS message_notification (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    type TINYINT NOT NULL DEFAULT 1 COMMENT '消息类型：1-系统 2-交易 3-活动 4-评论 5-聊天',
    title VARCHAR(100),
    content VARCHAR(500),
    link_url VARCHAR(200),
    is_read TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);
