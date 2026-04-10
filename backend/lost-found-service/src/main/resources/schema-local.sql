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
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS lf_comment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    lost_found_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    content VARCHAR(500) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
