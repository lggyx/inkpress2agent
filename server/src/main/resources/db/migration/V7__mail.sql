-- V7 邮件域：每周精选订阅字段（默认关闭，仅注册用户）
-- 退订令牌为低敏感凭据（仅能关闭订阅开关），需在多封邮件中复用原文链接，故存原文而非哈希；
-- 与 refresh/device 令牌（鉴权凭据，哈希存储）性质不同。
ALTER TABLE `user`
    ADD COLUMN digest_subscribed  TINYINT      NOT NULL DEFAULT 0 COMMENT '每周精选订阅开关 0/1',
    ADD COLUMN digest_unsub_token VARCHAR(100) NULL COMMENT '一键退订令牌原文（首封邮件/首次订阅时生成）',
    ADD KEY idx_user_digest_token (digest_unsub_token);
