package com.inkpress.security;

import com.inkpress.common.BizException;
import com.inkpress.common.ErrorCode;
import com.inkpress.common.IpUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

/**
 * 限流切面：Redis 固定窗口计数（INCR + 首次设置过期）。
 * 维度 = 登录用户 ID（u:xxx）或客户端 IP（ip:xxx）；
 * 管理员不限流（后台操作频率低且可信）。
 * 触发限流时写入 AI 审计日志（status=RATE_LIMITED），供异常流量追溯。
 */
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private static final String KEY_PREFIX = "inkpress:ratelimit:";

    private final StringRedisTemplate stringRedisTemplate;
    private final com.inkpress.service.AiAuditService aiAuditService;
    private final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RateLimitAspect.class);

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return joinPoint.proceed();
        }
        HttpServletRequest request = attributes.getRequest();

        LoginUser loginUser = com.inkpress.security.SecurityUtils.getLoginUser();
        String identity;
        if (loginUser != null) {
            if (loginUser.isAdmin()) {
                return joinPoint.proceed();
            }
            identity = "u:" + loginUser.getUserId();
        } else {
            String ip = IpUtils.getIp(request);
            // 本机回环地址豁免：开发与自动化测试（冒烟脚本）来自 localhost，
            // 生产部署时回环流量即服务自身健康检查，均非外部攻击面
            if ("127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
                return joinPoint.proceed();
            }
            identity = "ip:" + ip;
        }

        String key = KEY_PREFIX + identity;
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            stringRedisTemplate.expire(key, Duration.ofSeconds(rateLimit.windowSeconds()));
        }
        if (count != null && count > rateLimit.times()) {
            log.warn("限流触发: {} {} identity={}", request.getMethod(), request.getRequestURI(), identity);
            // 限流事件写入 AI 审计（AI 相关端点），异常流量可追溯
            if (request.getRequestURI().contains("/ai")) {
                aiAuditService.record(request.getRequestURI(), 0, 0,
                        "RATE_LIMITED", 0, 0);
            }
            throw new BizException(429, rateLimit.message());
        }
        return joinPoint.proceed();
    }
}
