package com.inkpress.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CommentDTO {

    @NotNull(message = "文章不能为空")
    private Long articleId;

    /** 游客评论时必填 */
    @Size(max = 20, message = "昵称最长 20 字")
    private String nickname;

    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 1000, message = "评论最长 1000 字")
    private String content;

    /** 回复的父评论 ID，一级评论为空 */
    private Long parentId;
}
