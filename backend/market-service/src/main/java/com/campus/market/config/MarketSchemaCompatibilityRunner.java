package com.campus.market.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class MarketSchemaCompatibilityRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MarketSchemaCompatibilityRunner.class);

    private final JdbcTemplate jdbcTemplate;

    public MarketSchemaCompatibilityRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureTables();
        ensureColumns();
        ensureIndexes();
        ensureDefaultCategories();
        normalizeLegacyData();
    }

    private void ensureTables() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS product_category (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "name VARCHAR(64) NOT NULL," +
                "icon VARCHAR(128)," +
                "sort INT DEFAULT 0" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS product (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "title VARCHAR(100) NOT NULL," +
                "category_id BIGINT," +
                "cover_image VARCHAR(500)," +
                "images TEXT," +
                "description TEXT," +
                "price DECIMAL(10,2) NOT NULL," +
                "original_price DECIMAL(10,2)," +
                "tags VARCHAR(500)," +
                "status TINYINT DEFAULT 1," +
                "view_count INT DEFAULT 0," +
                "favorite_count INT DEFAULT 0," +
                "total_quantity INT DEFAULT 1," +
                "sold_quantity INT DEFAULT 0," +
                "seller_id BIGINT NOT NULL," +
                "create_time DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS product_favorite (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "product_id BIGINT NOT NULL," +
                "user_id BIGINT NOT NULL," +
                "create_time DATETIME DEFAULT CURRENT_TIMESTAMP" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS market_order (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "order_no VARCHAR(64) NOT NULL," +
                "product_id BIGINT NOT NULL," +
                "buyer_id BIGINT NOT NULL," +
                "seller_id BIGINT NOT NULL," +
                "price DECIMAL(10,2) NOT NULL," +
                "status TINYINT DEFAULT 0," +
                "remark VARCHAR(500)," +
                "pay_status TINYINT DEFAULT 0," +
                "pay_time DATETIME," +
                "pay_channel VARCHAR(32)," +
                "pay_order_no VARCHAR(64)," +
                "create_time DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS user_browse_history (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "user_id BIGINT NOT NULL," +
                "product_id BIGINT NOT NULL," +
                "browse_time DATETIME DEFAULT CURRENT_TIMESTAMP" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS chat_message (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "from_user_id BIGINT NOT NULL," +
                "to_user_id BIGINT NOT NULL," +
                "product_id BIGINT," +
                "content VARCHAR(1000) NOT NULL," +
                "msg_type TINYINT DEFAULT 1," +
                "is_read TINYINT DEFAULT 0," +
                "create_time DATETIME DEFAULT CURRENT_TIMESTAMP" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS chat_session (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "user_id BIGINT NOT NULL," +
                "other_user_id BIGINT NOT NULL," +
                "last_message VARCHAR(1000)," +
                "unread_count INT DEFAULT 0," +
                "update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    }

    private void ensureColumns() {
        ensureColumn("product_category", "icon",
                "ALTER TABLE product_category ADD COLUMN icon VARCHAR(128)");
        ensureColumn("product_category", "sort",
                "ALTER TABLE product_category ADD COLUMN sort INT DEFAULT 0");

        ensureColumn("product", "title",
                "ALTER TABLE product ADD COLUMN title VARCHAR(100) NOT NULL DEFAULT ''");
        ensureColumn("product", "category_id",
                "ALTER TABLE product ADD COLUMN category_id BIGINT");
        ensureColumn("product", "cover_image",
                "ALTER TABLE product ADD COLUMN cover_image VARCHAR(500)");
        ensureColumn("product", "images",
                "ALTER TABLE product ADD COLUMN images TEXT");
        ensureColumn("product", "description",
                "ALTER TABLE product ADD COLUMN description TEXT");
        ensureColumn("product", "price",
                "ALTER TABLE product ADD COLUMN price DECIMAL(10,2) NOT NULL DEFAULT 0");
        ensureColumn("product", "original_price",
                "ALTER TABLE product ADD COLUMN original_price DECIMAL(10,2)");
        ensureColumn("product", "tags",
                "ALTER TABLE product ADD COLUMN tags VARCHAR(500)");
        ensureColumn("product", "status",
                "ALTER TABLE product ADD COLUMN status TINYINT DEFAULT 1");
        ensureColumn("product", "view_count",
                "ALTER TABLE product ADD COLUMN view_count INT DEFAULT 0");
        ensureColumn("product", "favorite_count",
                "ALTER TABLE product ADD COLUMN favorite_count INT DEFAULT 0");
        ensureColumn("product", "total_quantity",
                "ALTER TABLE product ADD COLUMN total_quantity INT DEFAULT 1");
        ensureColumn("product", "sold_quantity",
                "ALTER TABLE product ADD COLUMN sold_quantity INT DEFAULT 0");
        ensureColumn("product", "seller_id",
                "ALTER TABLE product ADD COLUMN seller_id BIGINT NOT NULL DEFAULT 0");
        ensureColumn("product", "create_time",
                "ALTER TABLE product ADD COLUMN create_time DATETIME DEFAULT CURRENT_TIMESTAMP");
        ensureColumn("product", "update_time",
                "ALTER TABLE product ADD COLUMN update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");

        ensureColumn("product_favorite", "product_id",
                "ALTER TABLE product_favorite ADD COLUMN product_id BIGINT NOT NULL DEFAULT 0");
        ensureColumn("product_favorite", "user_id",
                "ALTER TABLE product_favorite ADD COLUMN user_id BIGINT NOT NULL DEFAULT 0");
        ensureColumn("product_favorite", "create_time",
                "ALTER TABLE product_favorite ADD COLUMN create_time DATETIME DEFAULT CURRENT_TIMESTAMP");

        ensureColumn("market_order", "order_no",
                "ALTER TABLE market_order ADD COLUMN order_no VARCHAR(64) NOT NULL DEFAULT ''");
        ensureColumn("market_order", "product_id",
                "ALTER TABLE market_order ADD COLUMN product_id BIGINT NOT NULL DEFAULT 0");
        ensureColumn("market_order", "buyer_id",
                "ALTER TABLE market_order ADD COLUMN buyer_id BIGINT NOT NULL DEFAULT 0");
        ensureColumn("market_order", "seller_id",
                "ALTER TABLE market_order ADD COLUMN seller_id BIGINT NOT NULL DEFAULT 0");
        ensureColumn("market_order", "price",
                "ALTER TABLE market_order ADD COLUMN price DECIMAL(10,2) NOT NULL DEFAULT 0");
        ensureColumn("market_order", "status",
                "ALTER TABLE market_order ADD COLUMN status TINYINT DEFAULT 0");
        ensureColumn("market_order", "remark",
                "ALTER TABLE market_order ADD COLUMN remark VARCHAR(500)");
        ensureColumn("market_order", "pay_status",
                "ALTER TABLE market_order ADD COLUMN pay_status TINYINT DEFAULT 0");
        ensureColumn("market_order", "pay_time",
                "ALTER TABLE market_order ADD COLUMN pay_time DATETIME");
        ensureColumn("market_order", "pay_channel",
                "ALTER TABLE market_order ADD COLUMN pay_channel VARCHAR(32)");
        ensureColumn("market_order", "pay_order_no",
                "ALTER TABLE market_order ADD COLUMN pay_order_no VARCHAR(64)");
        ensureColumn("market_order", "create_time",
                "ALTER TABLE market_order ADD COLUMN create_time DATETIME DEFAULT CURRENT_TIMESTAMP");
        ensureColumn("market_order", "update_time",
                "ALTER TABLE market_order ADD COLUMN update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");

        ensureColumn("user_browse_history", "browse_time",
                "ALTER TABLE user_browse_history ADD COLUMN browse_time DATETIME DEFAULT CURRENT_TIMESTAMP");

        ensureColumn("chat_message", "msg_type",
                "ALTER TABLE chat_message ADD COLUMN msg_type TINYINT DEFAULT 1");
        ensureColumn("chat_message", "is_read",
                "ALTER TABLE chat_message ADD COLUMN is_read TINYINT DEFAULT 0");
        ensureColumn("chat_message", "create_time",
                "ALTER TABLE chat_message ADD COLUMN create_time DATETIME DEFAULT CURRENT_TIMESTAMP");

        ensureColumn("chat_session", "user_id",
                "ALTER TABLE chat_session ADD COLUMN user_id BIGINT NOT NULL DEFAULT 0");
        ensureColumn("chat_session", "other_user_id",
                "ALTER TABLE chat_session ADD COLUMN other_user_id BIGINT NOT NULL DEFAULT 0");
        ensureColumn("chat_session", "last_message",
                "ALTER TABLE chat_session ADD COLUMN last_message VARCHAR(1000)");
        ensureColumn("chat_session", "unread_count",
                "ALTER TABLE chat_session ADD COLUMN unread_count INT DEFAULT 0");
        ensureColumn("chat_session", "update_time",
                "ALTER TABLE chat_session ADD COLUMN update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
    }

    private void ensureIndexes() {
        ensureIndex("product", "idx_product_status_create_time",
                "CREATE INDEX idx_product_status_create_time ON product(status, create_time)");
        ensureIndex("product_favorite", "uk_product_user",
                "CREATE UNIQUE INDEX uk_product_user ON product_favorite(product_id, user_id)");
        ensureIndex("market_order", "uk_order_no",
                "CREATE UNIQUE INDEX uk_order_no ON market_order(order_no)");
        ensureIndex("market_order", "idx_order_user_status",
                "CREATE INDEX idx_order_user_status ON market_order(buyer_id, seller_id, status)");
        ensureIndex("user_browse_history", "idx_user_browse_time",
                "CREATE INDEX idx_user_browse_time ON user_browse_history(user_id, browse_time)");
        ensureIndex("chat_message", "idx_chat_pair_time",
                "CREATE INDEX idx_chat_pair_time ON chat_message(from_user_id, to_user_id, create_time)");
        ensureIndex("chat_session", "uk_chat_session_user_other",
                "CREATE UNIQUE INDEX uk_chat_session_user_other ON chat_session(user_id, other_user_id)");
    }

    private void ensureDefaultCategories() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM product_category", Integer.class);
        if (count == null || count > 0) {
            return;
        }
        jdbcTemplate.update("INSERT INTO product_category(name, icon, sort) VALUES(?, ?, ?)", "教材", "Reading", 10);
        jdbcTemplate.update("INSERT INTO product_category(name, icon, sort) VALUES(?, ?, ?)", "数码", "Monitor", 20);
        jdbcTemplate.update("INSERT INTO product_category(name, icon, sort) VALUES(?, ?, ?)", "生活用品", "House", 30);
        jdbcTemplate.update("INSERT INTO product_category(name, icon, sort) VALUES(?, ?, ?)", "运动户外", "Football", 40);
        jdbcTemplate.update("INSERT INTO product_category(name, icon, sort) VALUES(?, ?, ?)", "其他", "More", 50);
    }

    private void normalizeLegacyData() {
        if (hasColumn("product", "created_at")) {
            jdbcTemplate.update("UPDATE product SET create_time = created_at WHERE create_time IS NULL");
        }
        if (hasColumn("user_browse_history", "viewed_at")) {
            jdbcTemplate.update("UPDATE user_browse_history SET browse_time = viewed_at WHERE browse_time IS NULL");
        }

        String productStatusType = queryColumnType("product", "status");
        if (productStatusType != null && !"tinyint".equalsIgnoreCase(productStatusType)) {
            jdbcTemplate.update("UPDATE product SET status = 1 WHERE LOWER(status) IN ('on_sale','onsale','selling')");
            jdbcTemplate.update("UPDATE product SET status = 2 WHERE LOWER(status) IN ('sold','finished')");
            jdbcTemplate.update("UPDATE product SET status = 0 WHERE LOWER(status) IN ('off','offline','removed')");
            jdbcTemplate.execute("ALTER TABLE product MODIFY COLUMN status TINYINT DEFAULT 1");
        }

        if (hasColumn("chat_message", "sent_at")) {
            jdbcTemplate.update("UPDATE chat_message SET create_time = sent_at WHERE create_time IS NULL");
        }
        if (hasColumn("product", "total_quantity")) {
            jdbcTemplate.update("UPDATE product SET total_quantity = 1 WHERE total_quantity IS NULL OR total_quantity <= 0");
        }
        if (hasColumn("product", "sold_quantity")) {
            jdbcTemplate.update("UPDATE product SET sold_quantity = 0 WHERE sold_quantity IS NULL OR sold_quantity < 0");
        }
        if (hasColumn("product", "total_quantity") && hasColumn("product", "sold_quantity")) {
            jdbcTemplate.update("UPDATE product SET sold_quantity = LEAST(sold_quantity, total_quantity)");
            jdbcTemplate.update("UPDATE product SET status = 2 WHERE sold_quantity >= total_quantity");
        }
    }

    private void ensureColumn(String tableName, String columnName, String ddl) {
        if (hasColumn(tableName, columnName)) {
            return;
        }
        log.warn("Missing column {}.{}, applying compatibility DDL", tableName, columnName);
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

    private String queryColumnType(String tableName, String columnName) {
        if (!hasColumn(tableName, columnName)) {
            return null;
        }
        return jdbcTemplate.queryForObject(
                "SELECT DATA_TYPE FROM information_schema.COLUMNS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                String.class,
                tableName,
                columnName
        );
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
}
