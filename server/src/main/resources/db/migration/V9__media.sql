-- V9 媒体资产：AI 配图等二进制媒体统一落库（MEDIUMBLOB 上限 16MB，配图流量级足够；
-- distributor 侧 OSS 外链回填后仍可作为本地缓存/审计副本）
CREATE TABLE media_asset (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    kind       VARCHAR(20) NOT NULL COMMENT 'AI_IMAGE / UPLOAD（预留）',
    mime       VARCHAR(50) NOT NULL,
    byte_size  INT         NOT NULL,
    content    MEDIUMBLOB  NOT NULL,
    created_by BIGINT      NOT NULL,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_media_creator (created_by, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
