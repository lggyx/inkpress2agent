package com.inkpress.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解：基于 Redis 固定窗口计数。
 * 同一访问者（登录用户 ID 或客户端 IP）在 windowSeconds 内最多 times 次请求。
 * 超限抛业务异常（错误码 429），由全局异常处理器统一返回。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** 窗口内允许的最大请求次数 */
    int times() default 10;

    /** 窗口长度（秒） */
    int windowSeconds() default 60;

    /** 限流维度说明（写日志用） */
    String message() default "请求过于频繁，请稍后再试";
}
