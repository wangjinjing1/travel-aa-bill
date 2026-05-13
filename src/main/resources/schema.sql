CREATE DATABASE IF NOT EXISTS `travel_bill`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE `travel_bill`;

CREATE TABLE IF NOT EXISTS app_user (
  id VARCHAR(80) PRIMARY KEY,
  username VARCHAR(80) NOT NULL,
  password_hash VARCHAR(128) NOT NULL,
  role VARCHAR(20) NOT NULL DEFAULT 'USER',
  display_name VARCHAR(80) NOT NULL,
  avatar_url VARCHAR(500) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_app_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS registration_invite (
  token VARCHAR(64) PRIMARY KEY,
  created_by VARCHAR(80) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  used_by VARCHAR(80) NULL,
  used_at DATETIME NULL,
  INDEX idx_registration_invite_created_by (created_by),
  INDEX idx_registration_invite_used_by (used_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS travel_plan (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  destination VARCHAR(120) NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  description TEXT NOT NULL,
  creator_id VARCHAR(80) NOT NULL,
  creator_name VARCHAR(80) NOT NULL,
  share_token VARCHAR(64) NOT NULL UNIQUE,
  request_id VARCHAR(80) NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  participant_count INT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  closed_at DATETIME NULL,
  INDEX idx_travel_plan_creator (creator_id),
  INDEX idx_travel_plan_token (share_token),
  UNIQUE KEY uk_plan_creator_request (creator_id, request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS plan_member (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  plan_id BIGINT NOT NULL,
  user_id VARCHAR(80) NOT NULL,
  display_name VARCHAR(80) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  joined_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  reviewed_at DATETIME NULL,
  reviewed_by VARCHAR(80) NULL,
  UNIQUE KEY uk_plan_member (plan_id, user_id),
  INDEX idx_plan_member_user (user_id),
  INDEX idx_plan_member_reviewed_by (reviewed_by),
  CONSTRAINT fk_plan_member_plan
    FOREIGN KEY (plan_id) REFERENCES travel_plan(id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS travel_expense (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  plan_id BIGINT NOT NULL,
  user_id VARCHAR(80) NOT NULL,
  payer_name VARCHAR(80) NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  note VARCHAR(255) NOT NULL,
  spent_at DATE NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  request_id VARCHAR(80) NULL,
  INDEX idx_travel_expense_plan (plan_id),
  UNIQUE KEY uk_expense_user_request (plan_id, user_id, request_id),
  CONSTRAINT fk_travel_expense_plan
    FOREIGN KEY (plan_id) REFERENCES travel_plan(id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ip_blacklist (
  ip VARCHAR(64) PRIMARY KEY,
  blacklisted_at DATETIME NOT NULL,
  expires_at DATETIME NOT NULL,
  request_count INT NOT NULL,
  INDEX idx_ip_blacklist_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
