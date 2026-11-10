package cn.hengxing.inkpress.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** 接口限流（Redis 固定窗口计数）：登录/注册/AI/评论等敏感端点按 IP 限制 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private record Rule(String method, String prefix, int limit, Duration window) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule("POST", "/api/auth/login", 10, Duration.ofMinutes(1)),
            new Rule("POST", "/api/auth/register", 5, Duration.ofMinutes(10)),
            new Rule("POST", "/api/auth/device", 10, Duration.ofMinutes(10)),
            new Rule("POST", "/api/auth/forgot-password", 5, Duration.ofMinutes(10)),
            new Rule("POST", "/api/ai/chat", 30, Duration.ofMinutes(1)),
            new Rule("POST", "/api/ai/summary", 10, Duration.ofMinutes(1)));

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RateLimitFilter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return RULES.stream().noneMatch(r ->
                r.method().equals(request.getMethod()) && request.getRequestURI().startsWith(r.prefix()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Rule rule = RULES.stream()
                .filter(r -> r.method().equals(request.getMethod()) && request.getRequestURI().startsWith(r.prefix()))
                .findFirst().orElseThrow();
        String ip = request.getHeader("X-Real-IP");
        if (ip == null) {
            ip = request.getRemoteAddr();
        }
        String key = "rate:" + rule.prefix() + ":" + ip;
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, rule.window());
            }
            if (count != null && count > rule.limit()) {
                response.setStatus(429);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(objectMapper.writeValueAsString(
                        Map.of("code", 429, "message", "请求过于频繁，请稍后再试", "data", null)));
                return;
            }
        } catch (Exception e) {
            // Redis 不可用时放行，不阻塞主链路
        }
        chain.doFilter(request, response);
    }
}
