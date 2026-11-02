package com.inkpress.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评论
 */
@Data
@TableName("comment")
public class Comment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long articleId;

    /** 登录用户的评论记录用户 ID，游客评论为空 */
    private Long userId;

    /** 游客昵称；登录用户取其昵称 */
    private String nickname;

    /** 游客邮箱（仅用于头像展示与联系，非必填） */
    private String email;

    private String content;

    /** 父评论 ID，一级评论为空 */
    private Long parentId;

    /** 状态：PENDING 待审核 / APPROVED 通过 / REJECTED 拒绝 */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
