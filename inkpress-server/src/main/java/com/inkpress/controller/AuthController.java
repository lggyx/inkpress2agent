package com.inkpress.controller;

import com.inkpress.common.ApiResponse;
import com.inkpress.dto.LoginDTO;
import com.inkpress.dto.RegisterDTO;
import com.inkpress.security.RateLimit;
import com.inkpress.service.AuthService;
import com.inkpress.vo.LoginVO;
import com.inkpress.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "认证")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "登录")
    @RateLimit(times = 5, windowSeconds = 300, message = "登录尝试过于频繁，请 5 分钟后再试")
    @PostMapping("/login")
    public ApiResponse<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return ApiResponse.ok(authService.login(dto));
    }

    @Operation(summary = "注册")
    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody RegisterDTO dto) {
        authService.register(dto);
        return ApiResponse.ok();
    }

    @Operation(summary = "当前登录用户信息")
    @GetMapping("/me")
    public ApiResponse<UserVO> me() {
        return ApiResponse.ok(authService.currentUser());
    }
}
