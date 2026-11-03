package com.inkpress.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章列表项
 */
@Data
public class ArticleVO {

    private Long id;
    private String title;
    private String summary;
    private String cover;
    /** 正文首图 URL（无封面时前端列表用作缩略图，运行时从 content 提取） */
    private String firstImage;
    private Long views;
    private Long likes;
    private Boolean isTop;
    private String status;
    private Long categoryId;
    private String categoryName;
    private List<String> tags;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
}
