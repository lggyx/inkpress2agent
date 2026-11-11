-- V15 OSS 外链资产无本地字节：content 放宽为可空（storage=LOCAL 仍有值，OSS 外链为空）
ALTER TABLE media_asset
    MODIFY COLUMN content MEDIUMBLOB NULL COMMENT '本地字节（storage=LOCAL 时有值；OSS 外链为空）';
