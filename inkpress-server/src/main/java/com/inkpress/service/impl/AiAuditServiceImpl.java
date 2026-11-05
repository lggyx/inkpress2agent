package com.inkpress.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.inkpress.common.IpUtils;
import com.inkpress.common.PageResult;
import com.inkpress.entity.AiAuditLog;
import com.inkpress.mapper.AiAuditLogMapper;
import com.inkpress.security.LoginUser;
import com.inkpress.security.SecurityUtils;
import com.inkpress.service.AiAuditService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
@RequiredArgsConstructor
public class AiAuditServiceImpl implements AiAuditService {

    private final AiAuditLogMapper aiAuditLogMapper;

    @Override
    public void record(String endpoint, int promptLength, int injectionRisk,
                       String status, int outputLength, long costMs) {
        // 调用线程（Controller/切面）中同步捕获身份与 IP：
        // @Async 线程池不继承 SecurityContext 与 RequestContextHolder 的 ThreadLocal
        LoginUser loginUser = SecurityUtils.getLoginUser();
        Long userId = loginUser != null ? loginUser.getUserId() : null;
        String username = loginUser != null ? loginUser.getUsername() : "anonymous";
        String ip = null;
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            ip = IpUtils.getIp(request);
        }
        saveAsync(endpoint, userId, username, ip, promptLength, injectionRisk,
                status, outputLength, costMs);
    }

    @Async("taskExecutor")
    void saveAsync(String endpoint, Long userId, String username, String ip,
                   int promptLength, int injectionRisk, String status,
                   int outputLength, long costMs) {
        try {
            AiAuditLog log = new AiAuditLog();
            log.setEndpoint(endpoint);
            log.setUserId(userId);
            log.setUsername(username);
            log.setIp(ip);
            log.setPromptLength(promptLength);
            log.setInjectionRisk(injectionRisk);
            log.setStatus(status);
            log.setOutputLength(outputLength);
            log.setCostMs(costMs);
            aiAuditLogMapper.insert(log);
        } catch (Exception e) {
            // 审计失败不影响业务主流程，仅控制台告警
            org.slf4j.LoggerFactory.getLogger(AiAuditServiceImpl.class)
                    .warn("AI 审计日志写入失败", e);
        }
    }

    @Override
    public PageResult<AiAuditLog> page(long pageNum, long pageSize, String status, Integer minRisk) {
        LambdaQueryWrapper<AiAuditLog> wrapper = new LambdaQueryWrapper<AiAuditLog>()
                .eq(StringUtils.hasText(status), AiAuditLog::getStatus, status)
                .ge(minRisk != null, AiAuditLog::getInjectionRisk, minRisk)
                .orderByDesc(AiAuditLog::getId);
        Page<AiAuditLog> page = aiAuditLogMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page, r -> r);
    }
}
