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
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    recovered_at TIMESTAMP NULL DEFAULT NULL
);

CREATE TABLE IF NOT EXISTS lf_comment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    lost_found_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    content VARCHAR(500) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS lf_private_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    item_id BIGINT NOT NULL,
    from_user_id BIGINT NOT NULL,
    to_user_id BIGINT NOT NULL,
    content VARCHAR(1000) NOT NULL,
    msg_type TINYINT DEFAULT 1,
    is_read TINYINT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS lf_private_session (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    item_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    other_user_id BIGINT NOT NULL,
    last_message VARCHAR(1000),
    unread_count INT DEFAULT 0,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_lf_ps_user_other_item (item_id, user_id, other_user_id)
);

CREATE TABLE IF NOT EXISTS lost_found_audit (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    lost_found_id BIGINT NOT NULL,
    auditor_id BIGINT NOT NULL,
    status TINYINT COMMENT '1-通过 2-驳回',
    reason VARCHAR(200),
    audit_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
