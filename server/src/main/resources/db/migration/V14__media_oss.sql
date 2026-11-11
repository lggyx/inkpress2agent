-- V14 媒体资产支持云 OSS 外链：storage=LOCAL（本地字节，AI 配图）/ OSS（云对象存储外链，上传与视频）
ALTER TABLE media_asset
    ADD COLUMN storage VARCHAR(16) NOT NULL DEFAULT 'LOCAL' COMMENT 'LOCAL 本地字节 / OSS 云对象存储外链',
    ADD COLUMN url VARCHAR(500) NULL COMMENT '公网访问链接（storage=OSS 时有值）';
