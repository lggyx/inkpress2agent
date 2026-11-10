-- V1 认证域：用户、角色、刷新令牌、设备令牌、邮箱验证码
CREATE TABLE `user` (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    email         VARCHAR(128) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    nickname      VARCHAR(64)  NOT NULL,
    avatar_url    VARCHAR(512) NULL,
    bio           VARCHAR(500) NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'UNVERIFIED',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_email (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE user_role (
    user_id    BIGINT      NOT NULL,
    role       VARCHAR(20) NOT NULL,
    granted_by BIGINT      NULL,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE refresh_token (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    token_hash VARCHAR(100) NOT NULL,
    ip         VARCHAR(64)  NULL,
    user_agent VARCHAR(300) NULL,
    expires_at DATETIME     NOT NULL,
    revoked_at DATETIME     NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_refresh_hash (token_hash),
    KEY idx_refresh_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE device_token (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    name          VARCHAR(64)  NOT NULL,
    token_hash    VARCHAR(100) NOT NULL,
    last_used_at  DATETIME     NULL,
    last_used_ip  VARCHAR(64)  NULL,
    expires_at    DATETIME     NOT NULL,
    revoked_at    DATETIME     NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_device_hash (token_hash),
    KEY idx_device_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE email_code (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    email       VARCHAR(128) NOT NULL,
    purpose     VARCHAR(30)  NOT NULL,
    code_hash   VARCHAR(100) NOT NULL,
    expires_at  DATETIME     NOT NULL,
    consumed_at DATETIME     NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_email_code (email, purpose)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
