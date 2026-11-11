package cn.hengxing.inkpress.modules.auth.controller;

import cn.hengxing.inkpress.common.Result;
import cn.hengxing.inkpress.modules.auth.dto.ChangePasswordRequest;
import cn.hengxing.inkpress.modules.auth.dto.SubscriptionRequest;
import cn.hengxing.inkpress.modules.auth.dto.UpdateProfileRequest;
import cn.hengxing.inkpress.modules.auth.entity.User;
import cn.hengxing.inkpress.modules.auth.entity.UserRole;
import cn.hengxing.inkpress.modules.auth.mapper.UserMapper;
import cn.hengxing.inkpress.modules.auth.mapper.UserRoleMapper;
import cn.hengxing.inkpress.modules.auth.service.AuthService;
import cn.hengxing.inkpress.modules.auth.support.JwtAuthFilter;
import cn.hengxing.inkpress.modules.messaging.service.MailService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final AuthService authService;
    private final MailService mailService;

    public UserController(UserMapper userMapper, UserRoleMapper userRoleMapper, AuthService authService,
                          MailService mailService) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.authService = authService;
        this.mailService = mailService;
    }

    @GetMapping("/me")
    public Result<Map<String, Object>> me(@AuthenticationPrincipal JwtAuthFilter.LoginUser loginUser) {
        User user = userMapper.selectById(loginUser.id());
        List<String> roles = userRoleMapper.selectList(
                        new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, user.getId()))
                .stream().map(UserRole::getRole).toList();
        return Result.ok(Map.of(
                "id", user.getId(),
                "email", user.getEmail(),
                "nickname", user.getNickname(),
                "avatarUrl", user.getAvatarUrl() == null ? "" : user.getAvatarUrl(),
                "bio", user.getBio() == null ? "" : user.getBio(),
                "roles", roles));
    }

    @PutMapping("/me")
    public Result<Void> updateProfile(@AuthenticationPrincipal JwtAuthFilter.LoginUser loginUser,
                                      @Valid @RequestBody UpdateProfileRequest req) {
        User user = userMapper.selectById(loginUser.id());
        if (req.nickname() != null) {
            user.setNickname(req.nickname());
        }
        if (req.bio() != null) {
            user.setBio(req.bio());
        }
        userMapper.updateById(user);
        return Result.ok();
    }

    @PutMapping("/me/password")
    public Result<Void> changePassword(@AuthenticationPrincipal JwtAuthFilter.LoginUser loginUser,
                                      @Valid @RequestBody ChangePasswordRequest req) {
        authService.changePassword(loginUser.id(), req.oldPassword(), req.newPassword());
        return Result.ok();
    }

    /** 邮件订阅设置（每周精选，默认关闭；退订链接随每封邮件携带） */
    @GetMapping("/me/subscription")
    public Result<Map<String, Object>> subscription(@AuthenticationPrincipal JwtAuthFilter.LoginUser loginUser) {
        User user = userMapper.selectById(loginUser.id());
        return Result.ok(Map.of(
                "email", user.getEmail(),
                "digestSubscribed", user.getDigestSubscribed() != null && user.getDigestSubscribed() == 1));
    }

    @PutMapping("/me/subscription")
    public Result<Map<String, Object>> updateSubscription(@AuthenticationPrincipal JwtAuthFilter.LoginUser loginUser,
                                                          @Valid @RequestBody SubscriptionRequest req) {
        User user = userMapper.selectById(loginUser.id());
        user.setDigestSubscribed(Boolean.TRUE.equals(req.digestSubscribed()) ? 1 : 0);
        if (Boolean.TRUE.equals(req.digestSubscribed())) {
            // 开启订阅即确保退订令牌就绪，保证每封邮件都带一键退订
            mailService.ensureUnsubToken(user);
        }
        user.setUpdatedAt(java.time.LocalDateTime.now());
        userMapper.updateById(user);
        return Result.ok(Map.of("digestSubscribed", Boolean.TRUE.equals(req.digestSubscribed())));
    }
}
