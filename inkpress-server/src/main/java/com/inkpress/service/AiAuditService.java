package com.inkpress.service;

import com.inkpress.common.PageResult;
import com.inkpress.entity.AiAuditLog;

public interface AiAuditService {

    /**
     * 异步记录一次 AI 调用审计。
     *
     * @param endpoint      调用端点
     * @param promptLength  输入长度（字符）
     * @param injectionRisk 提示注入风险等级（0/1/2）
     * @param status        OK / TIMEOUT / ERROR / RATE_LIMITED
     * @param outputLength  输出长度（字符，失败为 0）
     * @param costMs        耗时毫秒
     */
    void record(String endpoint, int promptLength, int injectionRisk,
                String status, int outputLength, long costMs);

    /** 审计日志分页（管理端查询） */
    PageResult<AiAuditLog> page(long pageNum, long pageSize, String status, Integer minRisk);
}
