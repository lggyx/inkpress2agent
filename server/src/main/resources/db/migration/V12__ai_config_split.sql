-- V12 AI 配置分档：全局单套(ai.*)拆分为按能力独立 provider（ai.<cap>.protocol/base_url/api_key/model）
-- 能力槽：text 文本对话 / vision 视觉理解 / image 图像生成 / embedding 向量
-- INSERT IGNORE：config_key 主键去重，已有新键不动；旧 ai.* 行保留作为解析回退
INSERT IGNORE INTO system_config(config_key, config_value, updated_by)
SELECT CONCAT('ai.text.', SUBSTRING(config_key, 4)), config_value, 1
FROM system_config WHERE config_key IN ('ai.protocol', 'ai.base_url', 'ai.api_key', 'ai.model');

INSERT IGNORE INTO system_config(config_key, config_value, updated_by)
SELECT CONCAT('ai.vision.', SUBSTRING(config_key, 4)), config_value, 1
FROM system_config WHERE config_key IN ('ai.protocol', 'ai.base_url', 'ai.api_key', 'ai.model');

INSERT IGNORE INTO system_config(config_key, config_value, updated_by)
SELECT CONCAT('ai.image.', SUBSTRING(config_key, 4)), config_value, 1
FROM system_config WHERE config_key IN ('ai.protocol', 'ai.base_url', 'ai.api_key');

INSERT IGNORE INTO system_config(config_key, config_value, updated_by)
SELECT 'ai.image.model', config_value, 1 FROM system_config WHERE config_key = 'ai.image_model';

INSERT IGNORE INTO system_config(config_key, config_value, updated_by)
SELECT CONCAT('ai.embedding.', SUBSTRING(config_key, 4)), config_value, 1
FROM system_config WHERE config_key IN ('ai.protocol', 'ai.base_url', 'ai.api_key');

INSERT IGNORE INTO system_config(config_key, config_value, updated_by)
SELECT 'ai.embedding.model', config_value, 1 FROM system_config WHERE config_key = 'ai.embedding_model';
