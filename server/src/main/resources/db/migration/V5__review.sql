-- V5 评审域：AI 评审报告
CREATE TABLE review_report (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    article_id      BIGINT       NOT NULL,
    readability     INT          NULL,
    structure       INT          NULL,
    keyword_density INT          NULL,
    seo             INT          NULL,
    total_score     INT          NULL,
    suggestion      TEXT         NULL,
    confidence      VARCHAR(10)  NOT NULL DEFAULT 'HIGH',
    trigger_type    VARCHAR(10)  NOT NULL DEFAULT 'MANUAL',
    model           VARCHAR(64)  NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_review_article (article_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
