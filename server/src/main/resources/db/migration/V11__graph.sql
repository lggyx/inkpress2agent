-- V11 M8 图谱：手动关联边（标签共现边查询时从 article_tag 计算，不落表）
CREATE TABLE article_link (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    article_id BIGINT   NOT NULL COMMENT '源文章',
    target_id  BIGINT   NOT NULL COMMENT '关联目标文章',
    created_by BIGINT   NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_article_link (article_id, target_id),
    KEY idx_article_link_target (target_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
