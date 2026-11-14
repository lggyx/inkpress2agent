-- V22 进化域（BoHack 日新赛道）：运行会话 / AI 建议反馈 / 写作记忆 / 记忆快照
-- 核心闭环：作者对 AI 建议的反馈(👍/👎/采纳/修改) → 蒸馏成写作记忆 → 后续运行注入 system 提示
--          → 两次运行的指标可对比；记忆可快照/回退，供现场演示"第二次比第一次好"。

CREATE TABLE evolution_run (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id      BIGINT      NOT NULL,
    task_kind    VARCHAR(30) NOT NULL DEFAULT 'WRITING_ASSIST',
    status       VARCHAR(20) NOT NULL DEFAULT 'RUNNING',   -- RUNNING / FINISHED
    article_id   BIGINT      NULL,
    metrics_json TEXT        NULL,                          -- 结束运行时计算的量化指标
    started_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at  DATETIME    NULL,
    KEY idx_run_user (user_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE ai_feedback (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    run_id          BIGINT       NULL,                       -- 无进行中运行时允许单独记录
    user_id         BIGINT       NOT NULL,
    article_id      BIGINT       NULL,
    suggestion_type VARCHAR(20)  NOT NULL,                   -- REPLACE / APPEND / CHAT
    suggestion_text MEDIUMTEXT   NULL,                       -- AI 建议文本（截断存储）
    verdict         VARCHAR(20)  NOT NULL,                   -- UP / DOWN / APPLIED
    final_verbatim  TINYINT      NULL,                       -- 结束运行时核算：建议是否被原样保留进定稿
    edit_distance   INT          NULL,                       -- 非原样采纳时的归一化编辑距离（万分比）
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_feedback_run (run_id),
    KEY idx_feedback_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE writing_memory (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    category      VARCHAR(30)  NOT NULL DEFAULT 'STYLE',    -- STYLE / TONE / STRUCTURE / LENGTH / FACT
    content       VARCHAR(500) NOT NULL,                    -- 简短可执行的偏好规则
    source        VARCHAR(20)  NOT NULL DEFAULT 'DISTILL',  -- DISTILL / CORRECTION / RESTORED
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE / SUPERSEDED
    origin_run_id BIGINT       NULL,
    last_hit_at   DATETIME     NULL,                        -- 最近一次被注入 AI 提示的时间
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_memory_user (user_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE memory_snapshot (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    label         VARCHAR(100) NULL,
    snapshot_json MEDIUMTEXT   NOT NULL,                    -- 拍快照时的 ACTIVE 记忆全集
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_snapshot_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 记忆蒸馏模板：把一次运行的全部反馈提炼为持久偏好规则
INSERT IGNORE INTO prompt_template (category, name, content, enabled, updated_by)
VALUES ('memory_distill', '写作记忆蒸馏器',
        '你是博客作者的写作记忆蒸馏器。输入是作者在本次写作运行中对 AI 输出的全部反馈'
        '（👍 采纳 / 👎 拒绝 / 采纳后修改幅度 / 作者的文字纠正）。'
        '请提炼出能指导未来写作的持久偏好规则，输出 JSON 数组，每项格式 {"category":"...","content":"..."}。'
        '要求：1) 每条不超过 40 字，具体、可执行、可验证；2) 合并同类反馈，剔除只与本文相关的一次性内容；'
        '3) 只输出 JSON 数组本身，不要任何解释或代码块标记。'
        'category 只能取 STYLE / TONE / STRUCTURE / LENGTH / FACT 之一。',
        1, 1);

-- 写作助手模板追加"遵守写作记忆"约定（仅当模板未被管理员改过时生效，updated_by=1 为种子标记）
UPDATE prompt_template
SET content = CONCAT(content,
        '\n\n【写作偏好记忆】当系统提示中附带「作者写作偏好记忆」条目时，'
        '输出的风格、语气、结构、篇幅与措辞必须严格遵守这些记忆；'
        '记忆与本文其他要求冲突时，以记忆为准。输出前先逐条自查是否满足。')
WHERE category = 'assistant' AND updated_by = 1;
