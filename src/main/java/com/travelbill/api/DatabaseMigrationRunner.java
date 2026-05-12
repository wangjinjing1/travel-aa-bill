package com.travelbill.api;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(10)
public class DatabaseMigrationRunner implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    public DatabaseMigrationRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        addAvatarUrlColumnIfMissing();
        addUsernameColumnIfMissing();
        addPasswordHashColumnIfMissing();
        addRoleColumnIfMissing();
        fillMissingAccountColumns();
        addPlanMemberApprovalColumnsIfMissing();
        addRegistrationInviteTableIfMissing();
        addTravelPlanImageTableIfMissing();
        addIpBlacklistTableIfMissing();
        dropLegacyIpAccessCounterTableIfPresent();
        dropLegacyDisplayNameIndexIfPresent();
        addUsernameIndexIfMissing();
    }

    private void addAvatarUrlColumnIfMissing() {
        addColumnIfMissing("app_user", "avatar_url", "ALTER TABLE app_user ADD COLUMN avatar_url VARCHAR(500) NULL AFTER display_name");
    }

    private void addUsernameColumnIfMissing() {
        addColumnIfMissing("app_user", "username", "ALTER TABLE app_user ADD COLUMN username VARCHAR(80) NULL AFTER id");
    }

    private void addPasswordHashColumnIfMissing() {
        addColumnIfMissing("app_user", "password_hash", "ALTER TABLE app_user ADD COLUMN password_hash VARCHAR(128) NULL AFTER username");
    }

    private void addRoleColumnIfMissing() {
        addColumnIfMissing("app_user", "role", "ALTER TABLE app_user ADD COLUMN role VARCHAR(20) NULL AFTER password_hash");
    }

    private void fillMissingAccountColumns() {
        jdbcTemplate.execute("UPDATE app_user SET username = CONCAT('legacy_', id) WHERE username IS NULL OR username = ''");
        jdbcTemplate.execute("UPDATE app_user SET password_hash = REPEAT('0', 64) WHERE password_hash IS NULL OR password_hash = ''");
        jdbcTemplate.execute("UPDATE app_user SET role = 'USER' WHERE role IS NULL OR role = ''");
        jdbcTemplate.execute("ALTER TABLE app_user MODIFY COLUMN username VARCHAR(80) NOT NULL");
        jdbcTemplate.execute("ALTER TABLE app_user MODIFY COLUMN password_hash VARCHAR(128) NOT NULL");
        jdbcTemplate.execute("ALTER TABLE app_user MODIFY COLUMN role VARCHAR(20) NOT NULL");
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

    private void addRegistrationInviteTableIfMissing() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS registration_invite (
                  token VARCHAR(64) PRIMARY KEY,
                  created_by VARCHAR(80) NOT NULL,
                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  used_by VARCHAR(80) NULL,
                  used_at DATETIME NULL,
                  INDEX idx_registration_invite_created_by (created_by),
                  INDEX idx_registration_invite_used_by (used_by)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    private void addTravelPlanImageTableIfMissing() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS travel_plan_image (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  plan_id BIGINT NOT NULL,
                  filename VARCHAR(120) NOT NULL,
                  content_type VARCHAR(80) NOT NULL,
                  data LONGBLOB NOT NULL,
                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  INDEX idx_travel_plan_image_plan (plan_id),
                  CONSTRAINT fk_travel_plan_image_plan
                    FOREIGN KEY (plan_id) REFERENCES travel_plan(id)
                    ON DELETE CASCADE
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

    private void addUsernameIndexIfMissing() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'app_user'
                  AND INDEX_NAME = 'uk_app_user_username'
                """,
                Integer.class
        );
        if (count != null && count == 0) {
            jdbcTemplate.execute("ALTER TABLE app_user ADD UNIQUE INDEX uk_app_user_username (username)");
        }
    }
}
