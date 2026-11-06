package com.inkpress.vo;

import lombok.Data;

/**
 * AI 连通性测试结果（管理端）
 */
@Data
public class AiTestResultVO {

    /** 是否连通 */
    private Boolean success;

    /** 模型回复片段（截断展示） */
    private String reply;

    /** 耗时（毫秒） */
    private Long latencyMs;

    /** 失败原因（成功时为空） */
    private String message;
}
