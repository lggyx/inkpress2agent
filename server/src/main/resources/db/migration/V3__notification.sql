-- V3 通知域：站内通知（SSE 推送 + 未读数）
CREATE TABLE notification (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    type       VARCHAR(30)  NOT NULL,
    title      VARCHAR(128) NOT NULL,
    content    VARCHAR(500) NULL,
    ref_url    VARCHAR(300) NULL,
    read_at    DATETIME     NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_notification_user (user_id, read_at, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
