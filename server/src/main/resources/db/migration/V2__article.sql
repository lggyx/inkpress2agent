-- V2 文章域：分类树、标签、文章、评论、点赞、按天统计、敏感词
CREATE TABLE category (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    parent_id  BIGINT      NOT NULL DEFAULT 0,
    name       VARCHAR(64) NOT NULL,
    sort       INT         NOT NULL DEFAULT 0,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_category_parent (parent_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE tag (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    name       VARCHAR(64) NOT NULL,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tag_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE article (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    author_id       BIGINT       NOT NULL,
    category_id     BIGINT       NULL,
    title           VARCHAR(200) NOT NULL,
    content_md      MEDIUMTEXT   NOT NULL,
    content_html    MEDIUMTEXT   NULL,
    cover_url       VARCHAR(512) NULL,
    seo_description VARCHAR(500) NULL,
    geo_summary     VARCHAR(500) NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    source          VARCHAR(10)  NOT NULL DEFAULT 'HUMAN',
    pinned          TINYINT      NOT NULL DEFAULT 0,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    view_count      BIGINT       NOT NULL DEFAULT 0,
    like_count      BIGINT       NOT NULL DEFAULT 0,
    comment_count   INT          NOT NULL DEFAULT 0,
    published_at    DATETIME     NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_article_status (status, deleted, pinned, published_at),
    KEY idx_article_category (category_id),
    KEY idx_article_author (author_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE article_tag (
    article_id BIGINT NOT NULL,
    tag_id     BIGINT NOT NULL,
    PRIMARY KEY (article_id, tag_id),
    KEY idx_article_tag_tag (tag_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE comment (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    article_id BIGINT       NOT NULL,
    user_id    BIGINT       NOT NULL,
    parent_id  BIGINT       NOT NULL DEFAULT 0,
    root_id    BIGINT       NOT NULL DEFAULT 0,
    content    TEXT         NOT NULL,
    status     VARCHAR(20)  NOT NULL DEFAULT 'PUBLISHED',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_comment_article (article_id, status, root_id),
    KEY idx_comment_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE article_like (
    article_id BIGINT   NOT NULL,
    user_id    BIGINT   NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (article_id, user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE article_daily_stats (
    article_id BIGINT NOT NULL,
    stat_date  DATE   NOT NULL,
    views      BIGINT NOT NULL DEFAULT 0,
    likes      BIGINT NOT NULL DEFAULT 0,
    comments   BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (article_id, stat_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE sensitive_word (
    id   BIGINT PRIMARY KEY AUTO_INCREMENT,
    word VARCHAR(64) NOT NULL,
    UNIQUE KEY uk_sensitive_word (word)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO sensitive_word (word) VALUES ('示例敏感词'), ('spam-word');
