package com.inkpress.controller;

import com.inkpress.common.ApiResponse;
import com.inkpress.common.PageResult;
import com.inkpress.entity.OperationLog;
import com.inkpress.service.OperationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "操作日志")
@RestController
@RequestMapping("/api/admin/logs")
@RequiredArgsConstructor
public class LogController {

    private final OperationLogService operationLogService;

    @Operation(summary = "操作日志分页")
    @GetMapping
    public ApiResponse<PageResult<OperationLog>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize) {
        return ApiResponse.ok(operationLogService.page(pageNum, pageSize));
    }
}
