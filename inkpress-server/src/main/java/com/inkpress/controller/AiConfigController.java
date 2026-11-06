package com.inkpress.controller;

import com.inkpress.aspect.OperationLog;
import com.inkpress.common.ApiResponse;
import com.inkpress.dto.AiConfigDTO;
import com.inkpress.security.LoginUser;
import com.inkpress.security.SecurityUtils;
import com.inkpress.service.AiConfigService;
import com.inkpress.service.AiService;
import com.inkpress.vo.AiConfigVO;
import com.inkpress.vo.AiTestResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 服务配置管理（管理端）。
 * 密钥只写不回显完整值：GET 返回脱敏串，PUT 接收新值，任何接口不返回明文密钥。
 */
@Tag(name = "AI 服务配置")
@RestController
@RequestMapping("/api/admin/ai-config")
@RequiredArgsConstructor
public class AiConfigController {

    private final AiConfigService aiConfigService;
    private final AiService aiService;

    @Operation(summary = "获取当前 AI 配置（密钥脱敏）")
    @GetMapping
    public ApiResponse<AiConfigVO> get() {
        return ApiResponse.ok(aiConfigService.getForAdmin());
    }

    @Operation(summary = "保存 AI 配置（整行替换，保存后即时生效）")
    @OperationLog(module = "AI 服务配置", operation = "修改配置")
    @PutMapping
    public ApiResponse<Void> update(@Valid @RequestBody AiConfigDTO dto) {
        aiConfigService.update(dto, currentOperator());
        return ApiResponse.ok();
    }

    @Operation(summary = "恢复默认（删除数据库配置，回落环境变量）")
    @OperationLog(module = "AI 服务配置", operation = "恢复默认")
    @DeleteMapping
    public ApiResponse<Void> reset() {
        aiConfigService.reset(currentOperator());
        return ApiResponse.ok();
    }

    @Operation(summary = "测试当前配置连通性（最小调用）")
    @PostMapping("/test")
    public ApiResponse<AiTestResultVO> test() {
        return ApiResponse.ok(aiService.testConnection());
    }

    private String currentOperator() {
        LoginUser current = SecurityUtils.getLoginUser();
        return current == null ? "admin" : current.getUsername();
    }
}
