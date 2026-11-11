-- V8 每周精选投递去重：同一用户同一周只投一封（管理端重复触发/消息重投时消费端跳过）
CREATE TABLE email_digest_log (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id    BIGINT   NOT NULL,
    week_start DATE     NOT NULL COMMENT '所属周（周一），用于按周去重',
    sent_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_digest_user_week (user_id, week_start)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
