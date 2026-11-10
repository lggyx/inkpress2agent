-- V6 分发域：分发记录与执行明细
CREATE TABLE distribution_record (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    article_id    BIGINT       NOT NULL,
    platform      VARCHAR(20)  NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    draft_url     VARCHAR(500) NULL,
    published_url VARCHAR(500) NULL,
    attempt_count INT          NOT NULL DEFAULT 0,
    detail        TEXT         NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_distribution_article (article_id),
    KEY idx_distribution_status (status, platform)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
