package com.inkpress.controller;

import com.inkpress.common.ApiResponse;
import com.inkpress.service.DashboardService;
import com.inkpress.vo.DashboardVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "数据看板")
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "看板统计")
    @GetMapping("/stats")
    public ApiResponse<DashboardVO> stats() {
        return ApiResponse.ok(dashboardService.stats());
    }
}
