package com.inkpress.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章详情
 */
@Data
public class ArticleDetailVO {

    private Long id;
    private String title;
    private String summary;
    private String content;
    private String cover;
    private Long views;
    private Long likes;
    private Boolean isTop;
    private String status;
    private Long categoryId;
    private String categoryName;
    private List<String> tags;
    private String authorName;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
