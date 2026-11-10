-- V4 AI 域：系统配置（AI/SMTP/站点）与提示词模板
CREATE TABLE system_config (
    config_key   VARCHAR(64) PRIMARY KEY,
    config_value TEXT         NULL,
    updated_by   BIGINT       NULL,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE prompt_template (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    category    VARCHAR(30)  NOT NULL,
    name        VARCHAR(64)  NOT NULL,
    content     TEXT         NOT NULL,
    enabled     TINYINT      NOT NULL DEFAULT 1,
    updated_by  BIGINT       NULL,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_prompt_category (category)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 内置提示词模板：写作/纠错/摘要/续写/标题/评审
INSERT INTO prompt_template (category, name, content) VALUES
('polish',    '文本润色',      '你是一位技术博客编辑。请在不改变原意的前提下润色以下 Markdown 片段，修正语病、提升表达的专业性与流畅度，直接输出润色后的文本，不要解释。\n\n{{content}}'),
('correct',   '语法纠错',      '请检查以下 Markdown 片段中的错别字、语法与标点问题，输出修正后的完整文本，不要解释。\n\n{{content}}'),
('summary',   '摘要与标签',    '请为以下 Markdown 文章生成两类摘要和标签，严格按 JSON 输出（不要输出其他内容）：\n{"seoDescription":"面向搜索引擎的 meta 描述，150字以内，含关键词","geoSummary":"面向生成式引擎/AI问答引用的简明摘要，80字以内，信息密度高","tags":["3-5个关键词标签"]}\n\n文章：\n{{content}}'),
('continue',  '提纲续写',      '请根据以下提纲续写文章段落，风格与上下文保持一致，输出 Markdown。\n\n{{content}}'),
('title',     '标题推荐',      '请为以下文章推荐 5 个候选标题，要求风格各异（技术向/吸引力向/简洁向），每行一个，不要编号。\n\n{{content}}'),
('review',    '内容评审',      '请从可读性、结构完整性、关键词密度、SEO 友好度四个维度评价以下文章（各 0-100 分），指出主要问题并给出修改建议，最后给出总分（0-100）与一句话结论。输出格式：\n可读性：X分\n结构完整性：X分\n关键词密度：X分\nSEO 友好度：X分\n问题与建议：...\n总分：X\n结论：...\n\n文章：\n{{content}}');
