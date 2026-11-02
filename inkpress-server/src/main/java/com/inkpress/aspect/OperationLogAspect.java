package com.inkpress.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkpress.common.IpUtils;
import com.inkpress.security.LoginUser;
import com.inkpress.security.SecurityUtils;
import com.inkpress.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

/**
 * 操作日志切面：记录管理端写操作的调用者、参数与耗时，异步落库
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private static final int PARAMS_MAX_LENGTH = 2000;

    private final OperationLogService operationLogService;
    private final ObjectMapper objectMapper;

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperationLog operationLog) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            return joinPoint.proceed();
        } finally {
            saveLog(joinPoint, operationLog, System.currentTimeMillis() - start);
        }
    }

    private void saveLog(ProceedingJoinPoint joinPoint, OperationLog annotation, long costMs) {
        try {
            com.inkpress.entity.OperationLog logEntity = new com.inkpress.entity.OperationLog();
            LoginUser loginUser = SecurityUtils.getLoginUser();
            if (loginUser != null) {
                logEntity.setUserId(loginUser.getUserId());
                logEntity.setUsername(loginUser.getUsername());
            }
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                logEntity.setMethod(request.getMethod() + " " + request.getRequestURI());
                logEntity.setIp(IpUtils.getIp(request));
            }
            logEntity.setModule(annotation.module());
            logEntity.setOperation(annotation.operation());
            logEntity.setParams(serializeArgs(joinPoint.getArgs()));
            logEntity.setCostMs(costMs);
            operationLogService.record(logEntity);
        } catch (Exception e) {
            log.warn("记录操作日志失败", e);
        }
    }

    private String serializeArgs(Object[] args) {
        try {
            StringBuilder json = new StringBuilder("[");
            for (Object arg : args) {
                if (arg == null || skip(arg.getClass())) {
                    continue;
                }
                if (json.length() > 1) {
                    json.append(',');
                }
                json.append(objectMapper.writeValueAsString(arg));
            }
            json.append(']');
            String result = json.toString();
            return result.length() > PARAMS_MAX_LENGTH ? result.substring(0, PARAMS_MAX_LENGTH) : result;
        } catch (Exception e) {
            return "[参数序列化失败]";
        }
    }

    private boolean skip(Class<?> type) {
        return HttpServletRequest.class.isAssignableFrom(type)
                || HttpServletResponse.class.isAssignableFrom(type)
                || MultipartFile.class.isAssignableFrom(type);
    }
}
