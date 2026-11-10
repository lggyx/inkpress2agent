package cn.hengxing.inkpress.modules.auth.controller;

import cn.hengxing.inkpress.common.Result;
import cn.hengxing.inkpress.modules.auth.dto.ChangePasswordRequest;
import cn.hengxing.inkpress.modules.auth.dto.UpdateProfileRequest;
import cn.hengxing.inkpress.modules.auth.entity.User;
import cn.hengxing.inkpress.modules.auth.entity.UserRole;
import cn.hengxing.inkpress.modules.auth.mapper.UserMapper;
import cn.hengxing.inkpress.modules.auth.mapper.UserRoleMapper;
import cn.hengxing.inkpress.modules.auth.service.AuthService;
import cn.hengxing.inkpress.modules.auth.support.JwtAuthFilter;
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

    public UserController(UserMapper userMapper, UserRoleMapper userRoleMapper, AuthService authService) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.authService = authService;
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
}
