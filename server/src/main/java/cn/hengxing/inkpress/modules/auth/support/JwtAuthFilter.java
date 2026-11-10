package cn.hengxing.inkpress.modules.auth.support;

import cn.hengxing.inkpress.modules.auth.entity.User;
import cn.hengxing.inkpress.modules.auth.entity.UserRole;
import cn.hengxing.inkpress.modules.auth.mapper.UserMapper;
import cn.hengxing.inkpress.modules.auth.mapper.UserRoleMapper;
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
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;

    public JwtAuthFilter(JwtService jwtService, UserMapper userMapper, UserRoleMapper userRoleMapper) {
        this.jwtService = jwtService;
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            Long userId = jwtService.parseAccessToken(header.substring(7));
            if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                User user = userMapper.selectById(userId);
                if (user != null && "ACTIVE".equals(user.getStatus())) {
                    List<UserRole> roles = userRoleMapper.selectList(
                            new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId));
                    List<GrantedAuthority> authorities = roles.stream()
                            .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r.getRole()))
                            .toList();
                    LoginUser loginUser = new LoginUser(userId, user.getEmail(), user.getNickname(), authorities);
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(loginUser, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        }
        chain.doFilter(request, response);
    }

    public record LoginUser(Long id, String email, String nickname, List<GrantedAuthority> authorities) {
    }
}
