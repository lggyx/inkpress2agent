package com.inkpress.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 业务错误码
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "没有操作权限"),
    NOT_FOUND(404, "资源不存在"),
    FAIL(500, "系统繁忙，请稍后再试"),

    USERNAME_EXISTS(1001, "用户名已被占用"),
    USERNAME_OR_PASSWORD_ERROR(1002, "用户名或密码错误"),
    ACCOUNT_DISABLED(1003, "账号已被禁用"),

    AI_NOT_CONFIGURED(2001, "AI 服务未配置，请在 application.yml 中配置 inkpress.ai.api-key"),
    AI_CALL_FAILED(2002, "AI 服务调用失败，请稍后重试或检查配置"),
    AI_RESPONSE_INVALID(2003, "AI 返回内容解析失败");

    private final int code;
    private final String message;
}
