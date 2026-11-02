package com.inkpress.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志（AOP 自动记录）
 */
@Data
@TableName("operation_log")
public class OperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String username;

    /** 模块，如：文章管理 */
    private String module;

    /** 操作，如：发布文章 */
    private String operation;

    /** 请求方式 + 路径 */
    private String method;

    /** 请求参数 JSON（截断） */
    private String params;

    private String ip;

    /** 耗时（毫秒） */
    private Long costMs;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
