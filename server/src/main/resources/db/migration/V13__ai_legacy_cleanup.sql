-- V13 清理旧全局 AI 配置键：V12 已将其复制进四个能力槽，保留会导致
-- "槽位清空后 provider 回退复活旧配置"（例如图像/向量槽无法真正回到未配置态）。
-- 回退逻辑保留在代码中仅为兼容 V12 前未迁移的环境；迁移过的环境删除旧键。
DELETE FROM system_config WHERE config_key IN (
    'ai.protocol', 'ai.base_url', 'ai.api_key', 'ai.model',
    'ai.image_model', 'ai.embedding_model'
);
