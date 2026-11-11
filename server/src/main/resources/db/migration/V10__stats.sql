-- V10 M6 统计画像：匿名访客浏览日志 + 出站跳转点击（查询时聚合，不建预聚合表）
CREATE TABLE visit_log (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    visitor_id VARCHAR(64)  NOT NULL COMMENT '前端 localStorage 匿名访客 ID',
    ip         VARCHAR(64)  NULL,
    region     VARCHAR(120) NULL COMMENT 'ip2region 解析的国家/省市摘要（不存可回溯侧写）',
    device     VARCHAR(10)  NULL COMMENT 'MOBILE / PC',
    path       VARCHAR(300) NOT NULL,
    article_id BIGINT       NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_visit_time (created_at),
    KEY idx_visit_visitor (visitor_id, created_at),
    KEY idx_visit_article (article_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE outbound_click (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    visitor_id  VARCHAR(64)  NOT NULL,
    article_id  BIGINT       NULL,
    target_host VARCHAR(200) NOT NULL,
    target_url  VARCHAR(500) NOT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_out_host (target_host, created_at),
    KEY idx_out_article (article_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
