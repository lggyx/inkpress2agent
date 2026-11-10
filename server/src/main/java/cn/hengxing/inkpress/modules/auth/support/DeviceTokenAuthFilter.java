package cn.hengxing.inkpress.modules.auth.support;

import cn.hengxing.inkpress.modules.auth.entity.DeviceToken;
import cn.hengxing.inkpress.modules.auth.entity.User;
import cn.hengxing.inkpress.modules.auth.entity.UserRole;
import cn.hengxing.inkpress.modules.auth.mapper.DeviceTokenMapper;
import cn.hengxing.inkpress.modules.auth.mapper.UserMapper;
import cn.hengxing.inkpress.modules.auth.mapper.UserRoleMapper;
import cn.hengxing.inkpress.modules.auth.service.AuthService;
import cn.hengxing.inkpress.modules.auth.controller.DeviceTokenController;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

/** 设备令牌鉴权（本机 agent）：Authorization: Bearer ink_xxx；与 JWT 并列互不混流 */
@Component
public class DeviceTokenAuthFilter extends OncePerRequestFilter {

    private final DeviceTokenMapper deviceTokenMapper;
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;

    public DeviceTokenAuthFilter(DeviceTokenMapper deviceTokenMapper, UserMapper userMapper,
                                 UserRoleMapper userRoleMapper) {
        this.deviceTokenMapper = deviceTokenMapper;
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer " + DeviceTokenController.TOKEN_PREFIX)) {
            String raw = header.substring(7);
            DeviceToken dt = deviceTokenMapper.selectOne(new LambdaQueryWrapper<DeviceToken>()
                    .eq(DeviceToken::getTokenHash, AuthService.sha256(raw)));
            if (dt != null && dt.getRevokedAt() == null && dt.getExpiresAt().isAfter(LocalDateTime.now())
                    && SecurityContextHolder.getContext().getAuthentication() == null) {
                User user = userMapper.selectById(dt.getUserId());
                if (user != null && "ACTIVE".equals(user.getStatus())) {
                    dt.setLastUsedAt(LocalDateTime.now());
                    String ip = request.getHeader("X-Real-IP");
                    dt.setLastUsedIp(ip != null ? ip : request.getRemoteAddr());
                    deviceTokenMapper.updateById(dt);
                    List<GrantedAuthority> authorities = userRoleMapper
                            .selectList(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, user.getId()))
                            .stream().map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r.getRole()))
                            .toList();
                    JwtAuthFilter.LoginUser loginUser =
                            new JwtAuthFilter.LoginUser(user.getId(), user.getEmail(), user.getNickname(), authorities);
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(loginUser, null, authorities));
                }
            }
        }
        chain.doFilter(request, response);
    }
}
