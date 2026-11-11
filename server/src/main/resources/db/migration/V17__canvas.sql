-- V17 无限画布工作区：画布 + 画布元素（文字便签/文章引用/图片/网页链接，自由摆放）
CREATE TABLE canvas (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    owner_id   BIGINT       NOT NULL,
    name       VARCHAR(100) NOT NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_canvas_owner (owner_id, updated_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE canvas_item (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    canvas_id  BIGINT      NOT NULL,
    item_type  VARCHAR(20) NOT NULL COMMENT 'note 便签 / article 文章引用 / image 图片 / link 网页链接',
    x          INT         NOT NULL DEFAULT 0,
    y          INT         NOT NULL DEFAULT 0,
    w          INT         NOT NULL DEFAULT 220,
    h          INT         NOT NULL DEFAULT 120,
    z          INT         NOT NULL DEFAULT 0,
    payload    TEXT        NULL COMMENT 'JSON：type=note 文本 / article{articleId,title} / image{url} / link{url,title}',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_canvas_item (canvas_id, z),
    KEY idx_canvas_item_canvas (canvas_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
