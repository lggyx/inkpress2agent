-- V25：分发作品数据回流（WorkProvider B 档）
-- distributor CLI `inkpress work` 从已发布详情页提取阅读/点赞/评论后回报；博主端查看文章分发记录时可见
ALTER TABLE distribution_record
    ADD COLUMN work_views      BIGINT   NULL COMMENT '作品阅读数',
    ADD COLUMN work_likes      BIGINT   NULL COMMENT '作品点赞数',
    ADD COLUMN work_comments   BIGINT   NULL COMMENT '作品评论数',
    ADD COLUMN work_fetched_at DATETIME NULL COMMENT '最近一次数据回流时间';
