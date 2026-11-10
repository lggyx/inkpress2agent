package cn.hengxing.inkpress.modules.auth.controller;

import cn.hengxing.inkpress.common.Result;
import cn.hengxing.inkpress.modules.auth.dto.AuthResponse;
import cn.hengxing.inkpress.modules.auth.dto.ForgotPasswordRequest;
import cn.hengxing.inkpress.modules.auth.dto.LoginRequest;
import cn.hengxing.inkpress.modules.auth.dto.RefreshRequest;
import cn.hengxing.inkpress.modules.auth.dto.RegisterRequest;
import cn.hengxing.inkpress.modules.auth.dto.ResetPasswordRequest;
import cn.hengxing.inkpress.modules.auth.dto.VerifyEmailRequest;
import cn.hengxing.inkpress.modules.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterRequest req) {
        authService.register(req.email(), req.password(), req.nickname());
        return Result.ok();
    }

    @PostMapping("/verify-email")
    public Result<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest req) {
        authService.verifyEmail(req.email(), req.code());
        return Result.ok();
    }

    @PostMapping("/login")
    public Result<AuthResponse> login(@Valid @RequestBody LoginRequest req, HttpServletRequest request) {
        return Result.ok(authService.login(req.email(), req.password(), clientIp(request), userAgent(request)));
    }

    @PostMapping("/refresh")
    public Result<AuthResponse> refresh(@Valid @RequestBody RefreshRequest req, HttpServletRequest request) {
        return Result.ok(authService.refresh(req.refreshToken(), clientIp(request), userAgent(request)));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestBody(required = false) RefreshRequest req) {
        authService.logout(req != null ? req.refreshToken() : null);
        return Result.ok();
    }

    @PostMapping("/forgot-password")
    public Result<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        authService.forgotPassword(req.email());
        return Result.ok();
    }

    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req.email(), req.code(), req.newPassword());
        return Result.ok();
    }

    static String clientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Real-IP");
        return ip != null ? ip : request.getRemoteAddr();
    }

    static String userAgent(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        return ua != null && ua.length() > 300 ? ua.substring(0, 300) : ua;
    }
}
