package com.travelbill.api;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseMigrationRunner implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    public DatabaseMigrationRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        addAvatarUrlColumnIfMissing();
        addOpenIdColumnIfMissing();
        relaxOpenIdNullabilityIfNeeded();
        normalizeBlankOpenIds();
        addPlanMemberApprovalColumnsIfMissing();
        addIpBlacklistTableIfMissing();
        dropLegacyIpAccessCounterTableIfPresent();
        dropLegacyDisplayNameIndexIfPresent();
        addOpenIdIndexIfMissing();
    }

    private void addAvatarUrlColumnIfMissing() {
        addColumnIfMissing("app_user", "avatar_url", "ALTER TABLE app_user ADD COLUMN avatar_url VARCHAR(500) NULL AFTER display_name");
    }

    private void addOpenIdColumnIfMissing() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'app_user'
                  AND COLUMN_NAME = 'open_id'
                """,
                Integer.class
        );
        if (count != null && count == 0) {
            jdbcTemplate.execute("ALTER TABLE app_user ADD COLUMN open_id VARCHAR(64) NULL AFTER id");
        }
    }

    private void relaxOpenIdNullabilityIfNeeded() {
        String nullable = jdbcTemplate.queryForObject(
                """
                SELECT IS_NULLABLE
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'app_user'
                  AND COLUMN_NAME = 'open_id'
                """,
                String.class
        );
        if ("NO".equalsIgnoreCase(nullable)) {
            jdbcTemplate.execute("ALTER TABLE app_user MODIFY COLUMN open_id VARCHAR(64) NULL");
        }
    }

    private void normalizeBlankOpenIds() {
        jdbcTemplate.execute("UPDATE app_user SET open_id = NULL WHERE open_id = ''");
    }

    private void addPlanMemberApprovalColumnsIfMissing() {
        addColumnIfMissing("plan_member", "status", "ALTER TABLE plan_member ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PENDING' AFTER display_name");
        addColumnIfMissing("plan_member", "reviewed_at", "ALTER TABLE plan_member ADD COLUMN reviewed_at DATETIME NULL AFTER joined_at");
        addColumnIfMissing("plan_member", "reviewed_by", "ALTER TABLE plan_member ADD COLUMN reviewed_by VARCHAR(80) NULL AFTER reviewed_at");
        jdbcTemplate.execute("UPDATE plan_member SET status = 'APPROVED' WHERE status IS NULL OR status = ''");
    }

    private void addColumnIfMissing(String tableName, String columnName, String sql) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                """,
                Integer.class,
                tableName,
                columnName
        );
        if (count != null && count == 0) {
            jdbcTemplate.execute(sql);
        }
    }

    private void addIpBlacklistTableIfMissing() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ip_blacklist (
                  ip VARCHAR(64) PRIMARY KEY,
                  blacklisted_at DATETIME NOT NULL,
                  expires_at DATETIME NOT NULL,
                  request_count INT NOT NULL,
                  INDEX idx_ip_blacklist_expires (expires_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    private void dropLegacyIpAccessCounterTableIfPresent() {
        dropTableIfExists("ip_access_counter");
    }

    private void dropTableIfExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                """,
                Integer.class,
                tableName
        );
        if (count != null && count > 0) {
            jdbcTemplate.execute("DROP TABLE " + tableName);
        }
    }

    private void dropLegacyDisplayNameIndexIfPresent() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'app_user'
                  AND INDEX_NAME = 'uk_app_user_display_name'
                """,
                Integer.class
        );
        if (count != null && count > 0) {
            jdbcTemplate.execute("ALTER TABLE app_user DROP INDEX uk_app_user_display_name");
        }
    }

    private void addOpenIdIndexIfMissing() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'app_user'
                  AND INDEX_NAME = 'uk_app_user_open_id'
                """,
                Integer.class
        );
        if (count != null && count == 0) {
            jdbcTemplate.execute("ALTER TABLE app_user ADD UNIQUE INDEX uk_app_user_open_id (open_id)");
        }
    }
}
