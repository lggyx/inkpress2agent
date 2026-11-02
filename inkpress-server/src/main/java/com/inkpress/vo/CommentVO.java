package com.inkpress.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CommentVO {

    private Long id;
    private Long articleId;
    private String articleTitle;
    private String nickname;
    private String email;
    private String content;
    private Long parentId;
    private String status;
    private LocalDateTime createdAt;
}
