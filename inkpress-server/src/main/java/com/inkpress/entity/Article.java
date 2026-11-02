package com.inkpress.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文章
 */
@Data
@TableName("article")
public class Article {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    /** 摘要（可由 AI 生成） */
    private String summary;

    /** Markdown 正文 */
    private String content;

    /** 封面图 URL */
    private String cover;

    private Long categoryId;

    private Long authorId;

    /** 状态：DRAFT 草稿 / PUBLISHED 已发布 */
    private String status;

    /** 是否置顶 */
    private Boolean isTop;

    /** 浏览量（Redis 计数定时回写） */
    private Long views;

    /** 点赞数 */
    private Long likes;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    private LocalDateTime publishedAt;
}
