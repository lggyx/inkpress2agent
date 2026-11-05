package com.inkpress.controller;

import com.inkpress.common.ApiResponse;
import com.inkpress.common.PageResult;
import com.inkpress.entity.AiAuditLog;
import com.inkpress.service.AiAuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 调用审计查询（管理端）：安全审计与滥用追溯入口。
 */
@Tag(name = "AI 审计")
@RestController
@RequestMapping("/api/admin/ai-audit")
@RequiredArgsConstructor
public class AiAuditController {

    private final AiAuditService aiAuditService;

    @Operation(summary = "AI 调用审计分页（可按状态与最低风险等级过滤）")
    @GetMapping
    public ApiResponse<PageResult<AiAuditLog>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer minRisk) {
        return ApiResponse.ok(aiAuditService.page(pageNum, pageSize, status, minRisk));
    }
}
