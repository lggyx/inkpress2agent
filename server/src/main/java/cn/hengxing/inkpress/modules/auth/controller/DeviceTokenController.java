package cn.hengxing.inkpress.modules.auth.controller;

import cn.hengxing.inkpress.common.BizException;
import cn.hengxing.inkpress.common.Result;
import cn.hengxing.inkpress.modules.auth.dto.DeviceLoginRequest;
import cn.hengxing.inkpress.modules.auth.entity.DeviceToken;
import cn.hengxing.inkpress.modules.auth.entity.User;
import cn.hengxing.inkpress.modules.auth.mapper.DeviceTokenMapper;
import cn.hengxing.inkpress.modules.auth.mapper.UserMapper;
import cn.hengxing.inkpress.modules.auth.service.AuthService;
import cn.hengxing.inkpress.modules.auth.support.JwtAuthFilter;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DeviceTokenController {

    public static final String TOKEN_PREFIX = "ink_";

    private final DeviceTokenMapper deviceTokenMapper;
    private final UserMapper userMapper;
    private final AuthService authService;
    private final SecureRandom random = new SecureRandom();
    private final Duration deviceTtl;

    public DeviceTokenController(DeviceTokenMapper deviceTokenMapper, UserMapper userMapper,
                                 AuthService authService,
                                 @Value("${inkpress.jwt.refresh-token-ttl}") Duration deviceTtl) {
        this.deviceTokenMapper = deviceTokenMapper;
        this.userMapper = userMapper;
        this.authService = authService;
        this.deviceTtl = deviceTtl;
    }

    /** 本机 agent 登录：邮箱+密码+设备名 → 长期设备令牌（明文只返回一次） */
    @PostMapping("/auth/device")
    public Result<Map<String, Object>> deviceLogin(@Valid @RequestBody DeviceLoginRequest req,
                                                   HttpServletRequest request) {
        authService.login(req.email(), req.password(),
                AuthController.clientIp(request), AuthController.userAgent(request));
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, req.email().toLowerCase()));
        String token = TOKEN_PREFIX + newToken();
        DeviceToken dt = new DeviceToken();
        dt.setUserId(user.getId());
        dt.setName(req.deviceName());
        dt.setTokenHash(AuthService.sha256(token));
        dt.setExpiresAt(LocalDateTime.now().plus(deviceTtl));
        deviceTokenMapper.insert(dt);
        return Result.ok(Map.of("deviceToken", token, "deviceId", dt.getId()));
    }

    @GetMapping("/devices")
    public Result<List<Map<String, Object>>> list(@AuthenticationPrincipal JwtAuthFilter.LoginUser loginUser) {
        List<DeviceToken> devices = deviceTokenMapper.selectList(new LambdaQueryWrapper<DeviceToken>()
                .eq(DeviceToken::getUserId, loginUser.id())
                .isNull(DeviceToken::getRevokedAt)
                .orderByDesc(DeviceToken::getLastUsedAt));
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (DeviceToken d : devices) {
            Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("id", d.getId());
            item.put("name", d.getName());
            item.put("lastUsedAt", d.getLastUsedAt() == null ? "" : d.getLastUsedAt().toString());
            item.put("lastUsedIp", d.getLastUsedIp() == null ? "" : d.getLastUsedIp());
            item.put("createdAt", d.getCreatedAt().toString());
            result.add(item);
        }
        return Result.ok(result);
    }

    @DeleteMapping("/devices/{id}")
    public Result<Void> revoke(@AuthenticationPrincipal JwtAuthFilter.LoginUser loginUser, @PathVariable Long id) {
        DeviceToken dt = deviceTokenMapper.selectById(id);
        if (dt == null || !dt.getUserId().equals(loginUser.id()) || dt.getRevokedAt() != null) {
            throw BizException.of(1007, "设备不存在或已吊销");
        }
        dt.setRevokedAt(LocalDateTime.now());
        deviceTokenMapper.updateById(dt);
        return Result.ok();
    }

    private String newToken() {
        byte[] buf = new byte[36];
        random.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}
