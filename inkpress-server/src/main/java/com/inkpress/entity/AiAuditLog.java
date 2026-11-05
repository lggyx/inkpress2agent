package com.inkpress.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 调用审计日志：记录每次 AI/向量服务调用的调用者、端点、风险检测与结果，
 * 用于安全审计（异常使用模式追溯）与成本核算。
 */
@Data
@TableName("ai_audit_log")
public class AiAuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 调用端点（如 /api/admin/ai/polish） */
    private String endpoint;

    /** 登录用户 ID（游客为空） */
    private Long userId;

    /** 登录用户名（游客为 anonymous） */
    private String username;

    /** 来源 IP */
    private String ip;

    /** 输入长度（字符，不存原文避免日志敏感信息堆积） */
    private Integer promptLength;

    /** 提示注入风险等级：0 无 / 1 中 / 2 高 */
    private Integer injectionRisk;

    /** 调用结果：OK / TIMEOUT / ERROR / RATE_LIMITED */
    private String status;

    /** AI 返回长度（字符） */
    private Integer outputLength;

    /** 耗时（毫秒） */
    private Long costMs;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
